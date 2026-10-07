package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Endurance tree's mechanics. Kept apart from {@link EnduranceEvents}, which owns the passive,
 * the XP and Grit: everything here is a multiplier or a side effect on the same damage events, so
 * the two compose without either knowing about the other. The numbers are in
 * {@link EnduranceMath}, where they are unit tested.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentEnduranceEvents {

    private TalentEnduranceEvents() {
    }

    // ---------------------------------------------------------------- tuning

    /** Adrenal Mend: a hit this big or bigger, and not more often than this. */
    private static final float MEND_MIN_DAMAGE = 4.0f;
    private static final long MEND_COOLDOWN = 20 * 15;
    private static final int MEND_TICKS_PER_RANK = 40;

    private static final long LAST_STAND_COOLDOWN = 20 * 60 * 5;
    private static final int LAST_STAND_RESISTANCE_TICKS = 60;

    /** Indomitable: this long without a hit before it starts, then one heart every interval. */
    private static final long INDOMITABLE_QUIET_TICKS = 200;
    private static final int INDOMITABLE_INTERVAL_TICKS = 100;

    /**
     * Absorption is clamped to MAX_ABSORPTION, which is zero by default, so Indomitable raises the
     * cap by four hearts while its hearts last and takes it back once they are gone. Its own id,
     * next to Aegis's: both hand out at most four hearts counted from what is already there, so
     * the two caps never let absorption past four hearts between them.
     */
    private static final ResourceLocation INDOMITABLE_CAP_ID = Proficiency.id("indomitable_cap");

    // ---------------------------------------------------------------- state

    private record Scars(int count, long lastHit) {
    }

    private static final Map<UUID, Scars> SCARS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MEND_READY_AT = new ConcurrentHashMap<>();
    /** Kept across a relog on purpose, or logging out and back in would re-arm it. */
    private static final Map<UUID, Long> LAST_STAND_READY_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_EXHAUSTION = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SCARS.remove(id);
        MEND_READY_AT.remove(id);
        LAST_HURT.remove(id);
        LAST_EXHAUSTION.remove(id);
    }

    // ---------------------------------------------------------------- Roll With It

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        int rank = TalentService.rank(player, Skill.ENDURANCE, "roll_with_it");
        if (rank > 0) {
            event.setStrength((float) (event.getStrength() * TalentMath.lessPerRank(rank, 0.10)));
        }
    }

    // ---------------------------------------------------------------- Scar Tissue, Last Stand

    /**
     * Scar Tissue. Pre, not the incoming-damage event: incoming fires on every touch, including the
     * ones the invulnerability frames then throw away, so a cactus would stack five scars in five
     * ticks. Pre only sees hits that land. HIGH, so Last Stand below sees the scarred amount.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScarredHit(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getNewDamage() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        int rank = TalentService.rank(player, Skill.ENDURANCE, "scar_tissue");
        if (rank <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        Scars before = SCARS.get(player.getUUID());
        int standing = before == null ? 0 : EnduranceMath.scarsAt(before.count(), before.lastHit(), now);
        if (standing > 0) {
            event.setNewDamage((float) (event.getNewDamage() * EnduranceMath.scarMultiplier(standing, rank)));
        }
        SCARS.put(player.getUUID(), new Scars(Math.min(EnduranceMath.MAX_SCARS, standing + 1), now));
    }

    /**
     * Last Stand. Read after armour and after Scar Tissue, where the damage is the real amount, and
     * before absorption and health are touched. /kill and the void still kill: they bypass
     * invulnerability. Undying Rage works the same way on the same event; whichever runs first
     * leaves a hit that is no longer lethal, so the other keeps its charge.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLethalHit(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float buffer = player.getHealth() + player.getAbsorptionAmount();
        if (event.getNewDamage() < buffer
                || TalentService.rank(player, Skill.ENDURANCE, "last_stand") <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        Long readyAt = LAST_STAND_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            return;
        }
        LAST_STAND_READY_AT.put(player.getUUID(), now + LAST_STAND_COOLDOWN);
        event.setNewDamage(Math.max(0f, buffer - 1.0f));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LAST_STAND_RESISTANCE_TICKS, 1));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 0.9f);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        }
    }

    // ---------------------------------------------------------------- Adrenal Mend

    /** Adrenal Mend, and the quiet clock Indomitable reads. Post: the hit has landed. */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getOriginalDamage() <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        LAST_HURT.put(player.getUUID(), now);

        int mend = TalentService.rank(player, Skill.ENDURANCE, "hurt_regen");
        if (mend <= 0 || event.getNewDamage() < MEND_MIN_DAMAGE || !player.isAlive()) {
            return;
        }
        Long readyAt = MEND_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            return;
        }
        MEND_READY_AT.put(player.getUUID(), now + MEND_COOLDOWN);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEND_TICKS_PER_RANK * mend, 1));
    }

    // ---------------------------------------------------------------- Lean Times, Indomitable

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        leanTimes(player);
        if (player.tickCount % INDOMITABLE_INTERVAL_TICKS == 0) {
            indomitable(player);
        }
        if (player.tickCount % 20 == 0) {
            dropSpentCap(player);
        }
    }

    /**
     * Lean Times. There is no exhaustion event, so this watches the number: whatever it climbed
     * since last tick, while you are below half health, a share goes back. A drop means the food
     * bar just rolled over (or Long Haul refunded first), and there is nothing to hand back.
     */
    private static void leanTimes(Player player) {
        FoodData food = player.getFoodData();
        float now = food.getExhaustionLevel();
        Float before = LAST_EXHAUSTION.put(player.getUUID(), now);
        if (before == null || now <= before || player.getHealth() >= player.getMaxHealth() * 0.5f) {
            return;
        }
        int rank = TalentService.rank(player, Skill.ENDURANCE, "lean_times");
        if (rank <= 0) {
            return;
        }
        float refunded = (float) Math.max(0.0, now - (now - before) * EnduranceMath.leanRefund(rank));
        food.setExhaustion(refunded);
        LAST_EXHAUSTION.put(player.getUUID(), refunded);
    }

    /** Indomitable: ten quiet seconds, then a heart of absorption every five, up to four. */
    private static void indomitable(Player player) {
        if (TalentService.rank(player, Skill.ENDURANCE, "indomitable") <= 0 || !player.isAlive()) {
            return;
        }
        Long lastHurt = LAST_HURT.get(player.getUUID());
        if (lastHurt != null && player.level().getGameTime() - lastHurt < INDOMITABLE_QUIET_TICKS) {
            return;
        }
        float current = player.getAbsorptionAmount();
        float next = EnduranceMath.indomitableAbsorption(current);
        if (next <= current) {
            return;
        }
        AttributeInstance cap = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (cap == null) {
            return;
        }
        cap.addOrUpdateTransientModifier(new AttributeModifier(INDOMITABLE_CAP_ID,
                EnduranceMath.INDOMITABLE_CAP, AttributeModifier.Operation.ADD_VALUE));
        player.setAbsorptionAmount(next);
    }

    /** Once Indomitable's hearts are spent, the raised cap goes, so nothing else fills it for free. */
    private static void dropSpentCap(Player player) {
        AttributeInstance cap = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (cap != null && player.getAbsorptionAmount() <= 0 && cap.hasModifier(INDOMITABLE_CAP_ID)) {
            cap.removeModifier(INDOMITABLE_CAP_ID);
        }
    }
}
