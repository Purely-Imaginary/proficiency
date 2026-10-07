package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endurance: the skill you train by getting hit and living. It owns the passive (maximum health),
 * the XP (health actually lost), and the signature proc, Grit. The tree's mechanics are layered on
 * the same events in {@link TalentEnduranceEvents}.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class EnduranceEvents {

    /**
     * The passive, on MAX_HEALTH. PERMANENT rather than transient like the speed and reach
     * modifiers, and that difference is load-bearing: a player's attributes are read back from
     * their save before their health is, and setHealth clamps to the maximum at that moment. With a
     * transient modifier every relog would open at 20 health out of 40. The cost is that a server
     * which removes this mod keeps whatever bonus each player logged out with, until an op strips it.
     */
    private static final ResourceLocation MAX_HEALTH_MODIFIER = Proficiency.id("endurance_max_health");

    /** How often the passive is re-read. A level-up shows within a quarter of a second. */
    private static final int REFRESH_TICKS = 5;

    /** Grit only rolls on a hit that leaves you below this share of your maximum health. */
    public static final float GRIT_THRESHOLD = 0.30f;

    /** What Grit heals before proc power: two hearts. Deep Reserves raises it. */
    public static final float GRIT_HEAL = 4.0f;

    /** Each player's rolling allowance for XP from the world (falls, fire, cactus). */
    private static final Map<UUID, EnduranceMath.Budget> BUDGETS = new ConcurrentHashMap<>();

    private EnduranceEvents() {
    }

    // ---- The passive -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // Server only: MAX_HEALTH is a synced attribute, so the client's copy follows by itself.
        if (player.level().isClientSide() || player.tickCount % REFRESH_TICKS != 0) {
            return;
        }
        refreshMaxHealth(player);
    }

    /**
     * Puts the maximum-health modifier where the skill says it should be: +{@code bonus} of the base
     * (a multiplier on base, so +100% at level 100 is +20, whatever else raises the maximum). Also
     * covers login, a respawn and a level-up, since each of those is just "the number moved".
     * Public so the GameTest can call it on a mock player that nothing ticks.
     */
    public static void refreshMaxHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double amount = SkillService.bonus(player, Skill.ENDURANCE);
        AttributeModifier existing = maxHealth.getModifier(MAX_HEALTH_MODIFIER);
        if (!(amount > 0)) {
            if (existing != null) {
                maxHealth.removeModifier(MAX_HEALTH_MODIFIER);
            }
        } else if (existing == null || existing.amount() != amount) {
            maxHealth.addOrReplacePermanentModifier(new AttributeModifier(MAX_HEALTH_MODIFIER, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        // Vanilla clamps too, but only when it next refreshes dirty attributes on the entity's own
        // tick. A respec or a config change should not leave 38/30 on screen until then.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * A respawned or returning player is a new entity, and restoreFrom copies only base attribute
     * values, so the bonus is gone until the next refresh and vanilla has already set health to
     * the bare 20. Put it back here, after the skills themselves are copied across (this runs at
     * LOWEST), and then give the health back: a full bar after a death, the old number after
     * leaving the End.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        Player fresh = event.getEntity();
        if (fresh.level().isClientSide()) {
            return;
        }
        refreshMaxHealth(fresh);
        fresh.setHealth(event.isWasDeath() ? fresh.getMaxHealth() : event.getOriginal().getHealth());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BUDGETS.remove(event.getEntity().getUUID());
    }

    // ---- XP and Grit -------------------------------------------------------------------------

    /**
     * Post, not Pre: by now armour, enchantments and absorption have all taken their share and
     * health has actually dropped, so {@code getNewDamage()} is exactly what was lost, and whether
     * the player survived is a fact rather than a forecast. A hit soaked entirely by absorption
     * cost no health and pays nothing.
     */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity culprit = source.getEntity();
        LivingEntity attacker = culprit instanceof LivingEntity living && living != player ? living : null;
        String type = source.typeHolder().unwrapKey().map(key -> key.location().getPath()).orElse(null);

        EnduranceMath.Kind kind = EnduranceMath.classify(attacker != null, culprit == player, type);
        boolean survived = player.isAlive() && player.getHealth() > 0;
        // Survival or adventure, read from the game mode itself. Same answer as isCreative() and
        // isSpectator() on a real player, but the GameTest mock overrides isCreative() to true
        // whatever its mode, and this is the check that has to be testable.
        boolean counts = player.gameMode.isSurvival();

        double xp = EnduranceMath.xpFor(kind, event.getNewDamage(), survived, counts);
        if (kind == EnduranceMath.Kind.ENVIRONMENT && xp > 0) {
            xp = BUDGETS.computeIfAbsent(player.getUUID(), id -> new EnduranceMath.Budget())
                    .take(player.level().getGameTime(), xp);
        }
        if (xp > 0) {
            SkillService.grant(player, Skill.ENDURANCE, xp, sourceKey(attacker, type));
        }

        if (survived && counts && kind != EnduranceMath.Kind.NONE && event.getNewDamage() > 0) {
            grit(player, attacker);
        }
    }

    /**
     * Grit: a hit that leaves you under 30% rolls the proc, and on a landing you get two hearts
     * back, more with proc power. Unbreakable, the ability, is the usual promise: for its twenty
     * seconds every such hit is a Grit.
     */
    private static void grit(ServerPlayer player, @Nullable LivingEntity attacker) {
        if (player.getHealth() >= player.getMaxHealth() * GRIT_THRESHOLD) {
            return;
        }
        if (ProcService.fire(player, Skill.ENDURANCE, attacker)) {
            player.heal(GRIT_HEAL * (float) ProcService.power(player, Skill.ENDURANCE));
        }
    }

    /**
     * What the XP log names: the attacker's own name ("Zombie") when something alive did it, or a
     * line per damage type ("Fall", "Cactus"). Unknown modded types still read as words, because
     * the screen falls back to the last part of the key.
     */
    @Nullable
    private static String sourceKey(@Nullable LivingEntity attacker, @Nullable String type) {
        if (attacker != null) {
            return attacker.getType().getDescriptionId();
        }
        return type == null ? null : "proficiency.xplog.damage." + type;
    }
}
