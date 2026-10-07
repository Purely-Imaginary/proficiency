package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillProcEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The talent specials of Blocking, Archery and Crossbows. Kept apart from {@link CombatEvents},
 * which owns the passive and the proc: everything here is a multiplier or a side effect layered on
 * the same events, so the two compose without either knowing about the other.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentRangedEvents {

    private TalentRangedEvents() {
    }

    // ---------------------------------------------------------------- tuning

    private static final double LONGSHOT_STEP = 8.0;
    private static final double LONGSHOT_CAP = 40.0;
    private static final double SIEGE_MASTER_RANGE = 20.0;
    private static final int ARMOR_PIERCER_THRESHOLD = 10;
    private static final int MARKSMAN_EVERY = 5;
    private static final int HUNTERS_MARK_TICKS = 160;
    /** More marks than this per player and the oldest are dropped; nobody juggles 32 targets. */
    private static final int HUNTERS_MARK_LIMIT = 32;
    private static final double BASTION_RADIUS = 4.0;
    private static final float AEGIS_HEART = 2.0f;
    private static final float AEGIS_CAP = 8.0f;
    private static final double BALLISTA_RADIUS = 3.0;
    private static final float BALLISTA_DAMAGE = 4.0f;

    /**
     * Absorption above zero only exists while MAX_ABSORPTION allows it, and by default it is zero:
     * {@code setAbsorptionAmount} clamps to it. Aegis raises the cap by four hearts while its hearts
     * last, and takes the cap back once they are gone.
     */

    /** Set on an arrow at loose time: fully drawn from a crouch. Crouch at impact means nothing. */
    private static final String DEADEYE_TAG = "proficiency_deadeye";

    // ---------------------------------------------------------------- state

    /** Arrows that hit, per player, towards Marksman's fifth. */
    private static final Map<UUID, Integer> MARKSMAN_COUNT = new ConcurrentHashMap<>();

    /** Hunter's Mark: per player, the targets they marked and the game time each mark ends. */
    private static final Map<UUID, Map<UUID, Long>> MARKS = new ConcurrentHashMap<>();

    /**
     * The Ballista blast and the Spiked Rim hit are damage this class causes inside a damage
     * event. Both use a source with no direct entity, so nothing (here or in CombatEvents) reads
     * them as an arrow or a swing and nothing procs again; this flag is the belt to that braces.
     */
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        MARKSMAN_COUNT.remove(id);
        MARKS.remove(id);
    }

    // ---------------------------------------------------------------- pure arithmetic

    /** Longshot: +2% per rank for every whole 8 blocks, counted up to 40. */
    public static double longshotMultiplier(int rank, double distance) {
        if (rank <= 0 || !(distance > 0)) {
            return 1.0;
        }
        int steps = (int) Math.floor(Math.min(distance, LONGSHOT_CAP) / LONGSHOT_STEP);
        return 1.0 + 0.02 * rank * steps;
    }

    /** Aegis: one more heart, but never past four, and never taking away what is already there. */
    public static float aegisAbsorption(float current) {
        if (current >= AEGIS_CAP) {
            return current;
        }
        return Math.min(AEGIS_CAP, current + AEGIS_HEART);
    }

    /** Ballista: full damage at the heart of the blast, half at its edge. */
    public static float ballistaFalloff(double distance) {
        double t = Math.min(1.0, Math.max(0.0, distance / BALLISTA_RADIUS));
        return (float) (1.0 - 0.5 * t);
    }

    // ---------------------------------------------------------------- arrows

    /** Mirrors CombatEvents.arrowSkill: the weapon that fired it, not the arrow itself, decides. */
    @Nullable
    private static Skill arrowSkill(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof Player)) {
            return null;
        }
        // 1.20.1 does not keep the weapon on the arrow; it keeps whether a crossbow shot it.
        return arrow.shotFromCrossbow() ? Skill.CROSSBOWS : Skill.ARCHERY;
    }

    /** Deadeye has to know how the arrow left the bow, and by impact the player may have stood up. */
    @SubscribeEvent
    public static void onArrowLoosed(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof Player player)) {
            return;
        }
        // A bow marks a full draw as a crit arrow; a crossbow marks every bolt, hence the skill check.
        if (arrowSkill(arrow) == Skill.ARCHERY && arrow.isCritArrow() && player.isCrouching()
                && TalentService.rank(player, Skill.ARCHERY, "deadeye") > 0) {
            arrow.getPersistentData().putBoolean(DEADEYE_TAG, true);
        }
    }

    /**
     * The damage specials, and Marksman. HIGH so it runs before CombatEvents rolls the proc:
     * Marksman's forced shot has to be queued before that roll, and a Hunter's Mark placed by this
     * very hit must not already count for it.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onArrowDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == player) {
            return;
        }
        double multiplier = markMultiplier(player, target);

        if (source.getDirectEntity() instanceof AbstractArrow arrow && !REFLECTING.get()) {
            Skill skill = arrowSkill(arrow);
            double distance = player.distanceTo(target);
            if (skill == Skill.ARCHERY) {
                multiplier *= longshotMultiplier(
                        TalentService.rank(player, Skill.ARCHERY, "longshot"), distance);
                int sky = TalentService.rank(player, Skill.ARCHERY, "skyhunter");
                // Flying, falling, jumping or swimming: anything not standing on something.
                if (sky > 0 && !target.onGround()) {
                    multiplier *= 1.0 + 0.10 * sky;
                }
                if (arrow.getPersistentData().getBoolean(DEADEYE_TAG)
                        && TalentService.rank(player, Skill.ARCHERY, "deadeye") > 0) {
                    multiplier *= 1.5;
                }
                marksman(player);
            } else if (skill == Skill.CROSSBOWS) {
                int piercer = TalentService.rank(player, Skill.CROSSBOWS, "armor_piercer");
                if (piercer > 0 && target.getArmorValue() >= ARMOR_PIERCER_THRESHOLD) {
                    multiplier *= 1.0 + 0.08 * piercer;
                }
                if (distance > SIEGE_MASTER_RANGE
                        && TalentService.rank(player, Skill.CROSSBOWS, "siege_master") > 0) {
                    multiplier *= 1.25;
                }
            }
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /** Marksman: count the arrows that land, and make the fifth one certain. */
    private static void marksman(Player player) {
        if (TalentService.rank(player, Skill.ARCHERY, "marksman") <= 0) {
            return;
        }
        int count = MARKSMAN_COUNT.merge(player.getUUID(), 1, Integer::sum);
        if (count >= MARKSMAN_EVERY) {
            MARKSMAN_COUNT.remove(player.getUUID());
            // A frenzy procs every arrow already, and a force queued now would linger past it.
            if (!ActiveService.isFrenzied(player, Skill.ARCHERY)) {
                ProcService.forceNext(player, Skill.ARCHERY);
            }
        }
    }

    /**
     * The side effects of an arrow that landed. LOW, after CombatEvents: by now nothing upstream
     * cancelled the hit, so the arrow really did connect.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onArrowLanded(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof Player player)
                || player.level().isClientSide() || event.getEntity() == player) {
            return;
        }
        Skill skill = arrowSkill(arrow);
        if (skill == null) {
            return;
        }
        LivingEntity target = event.getEntity();

        // Only an arrow the player could have walked over and picked up comes back. That rules out
        // infinity, creative and multishot's extra bolts, which are all CREATIVE_ONLY.
        int saver = TalentService.rank(player, skill, "arrow_saver");
        if (saver > 0 && arrow.pickup == AbstractArrow.Pickup.ALLOWED
                && player.getRandom().nextDouble() < 0.10 * saver) {
            ItemStack returned = ((dev.amman.proficiency.mixin.AbstractArrowInvoker) arrow).proficiency$getPickupItem().copy();
            if (!returned.isEmpty()) {
                // A piercing arrow hits again and a deflected one stays in the world: either way,
                // once it has come back it cannot be collected a second time.
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                player.getInventory().placeItemBackInInventory(returned);
            }
        }

        if (skill == Skill.CROSSBOWS) {
            int kick = TalentService.rank(player, Skill.CROSSBOWS, "kickback");
            if (kick > 0) {
                // Vanilla's own hit knockback runs after this and halves whatever is here, so the
                // number is double what it feels like.
                target.knockback(0.5 * kick, player.getX() - target.getX(), player.getZ() - target.getZ());
                target.hurtMarked = true;
            }
        }
    }

    // ---------------------------------------------------------------- procs

    /** Hunter's Mark and Ballista both hang off the perfect shot. */
    @SubscribeEvent
    public static void onProc(SkillProcEvent event) {
        LivingEntity target = event.target();
        if (target == null || REFLECTING.get()) {
            return;
        }
        Player player = event.player();
        if (event.skill() == Skill.ARCHERY) {
            if (TalentService.rank(player, Skill.ARCHERY, "hunters_mark") > 0) {
                mark(player, target);
            }
        } else if (event.skill() == Skill.CROSSBOWS) {
            if (TalentService.rank(player, Skill.CROSSBOWS, "ballista") > 0) {
                ballista(player, target);
            }
        }
    }

    private static void mark(Player player, LivingEntity target) {
        long now = player.level().getGameTime();
        Map<UUID, Long> marks = MARKS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        marks.values().removeIf(end -> end <= now);
        if (marks.size() >= HUNTERS_MARK_LIMIT) {
            marks.clear();
        }
        marks.put(target.getUUID(), now + HUNTERS_MARK_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, HUNTERS_MARK_TICKS, 0, false, false), player);
    }

    /** 5% per rank more from the player who marked it, from anything they do, while the mark lasts. */
    private static double markMultiplier(Player player, LivingEntity target) {
        Map<UUID, Long> marks = MARKS.get(player.getUUID());
        if (marks == null) {
            return 1.0;
        }
        Long end = marks.get(target.getUUID());
        if (end == null) {
            return 1.0;
        }
        if (end <= player.level().getGameTime()) {
            marks.remove(target.getUUID());
            return 1.0;
        }
        int rank = TalentService.rank(player, Skill.ARCHERY, "hunters_mark");
        return 1.0 + 0.05 * rank;
    }

    /**
     * A small blast around the bolt's target, not a real Explosion: that would break blocks under
     * some rules, push and hurt other players, and burst item frames. It hurts the mobs standing
     * next to the target (the target already took the perfect bolt), scaled by proc power.
     */
    private static void ballista(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.6f, 1.3f);

        AABB area = new AABB(centre, centre).inflate(BALLISTA_RADIUS);
        var nearby = level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player
                && entity != target && entity.isAlive() && !(entity instanceof Player)
                && !(entity instanceof ArmorStand)
                && !(entity instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())));
        if (nearby.isEmpty()) {
            return;
        }
        // No direct entity: CombatEvents only scores swings and arrows, so this cannot proc again.
        DamageSource blast = player.damageSources().explosion(null, player);
        float damage = (float) (BALLISTA_DAMAGE * ProcService.power(player, Skill.CROSSBOWS));
        REFLECTING.set(true);
        try {
            for (LivingEntity entity : nearby) {
                double distance = entity.position().add(0, entity.getBbHeight() * 0.5, 0).distanceTo(centre);
                if (distance <= BALLISTA_RADIUS) {
                    entity.hurt(blast, damage * ballistaFalloff(distance));
                }
            }
        } finally {
            REFLECTING.set(false);
        }
    }

    // ---------------------------------------------------------------- blocking

    /** LOW, so it sees whether the block survived every other listener, CombatEvents' included. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.isCanceled()) {
            return;
        }
        DamageSource source = event.getDamageSource();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player
                ? living : null;

        // Melee the way vanilla means it: not a projectile, and the attacker is what touched you.
        int bash = TalentService.rank(player, Skill.BLOCKING, "shield_bash");
        if (bash > 0 && attacker != null && source.getDirectEntity() == attacker
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            attacker.knockback(0.4 * bash, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }

        int spikes = TalentService.rank(player, Skill.BLOCKING, "spiked_rim");
        if (spikes > 0 && attacker != null && attacker.isAlive() && !REFLECTING.get()) {
            // No direct entity, so no position: it cannot be blocked back, and CombatEvents does not
            // mistake it for a swing of whatever is in the main hand.
            DamageSource rim = ((dev.amman.proficiency.mixin.DamageSourcesInvoker) player.damageSources()).proficiency$source(DamageTypes.THORNS, null, player);
            REFLECTING.set(true);
            try {
                attacker.hurt(rim, spikes);
            } finally {
                REFLECTING.set(false);
            }
        }

        if (TalentService.rank(player, Skill.BLOCKING, "aegis") > 0) {
            // 1.21 caps absorption with the max_absorption attribute, so master raises it first;
            // 1.20.1 has no cap, so the hearts are simply set.
            player.setAbsorptionAmount(aegisAbsorption(player.getAbsorptionAmount()));
        }
    }

    /** Once Aegis's hearts are spent, the raised cap goes, so nothing else can fill it for free. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        // Nothing to drop on 1.20.1: there is no absorption cap attribute (see onShieldBlock).
    }

    /**
     * Bastion: a blocking player shelters the players around them. The strongest Bastion in range
     * counts; two do not stack. The blocker is covered too, for what gets round the shield.
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide()) {
            return;
        }
        int best = 0;
        for (Player other : victim.level().players()) {
            if (!other.isBlocking()
                    || other.distanceToSqr(victim) > BASTION_RADIUS * BASTION_RADIUS) {
                continue;
            }
            best = Math.max(best, TalentService.rank(other, Skill.BLOCKING, "bastion"));
        }
        if (best > 0) {
            event.setAmount(event.getAmount() * (1.0f - 0.05f * best));
        }
    }

    /** Immovable: behind a raised shield, no hit and no blast moves you. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && immovable(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * The blast half. NeoForge has an explosion-knockback event; Forge 1.20.1 does not, so
     * {@code mixin.ProtectionEnchantmentMixin} asks this from the knockback dampener every
     * explosion runs for each living entity it pushes, and a true here zeroes the push.
     */
    public static boolean immovableAgainstBlast(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof Player player && immovable(player);
    }

    private static boolean immovable(Player player) {
        return !player.level().isClientSide() && player.isBlocking()
                && TalentService.rank(player, Skill.BLOCKING, "immovable") > 0;
    }
}
