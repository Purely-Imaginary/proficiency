package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.CourageMath;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillProcEvent;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Courage: fighting when the odds are against you (idea 37). It owns the XP (damage dealt and
 * kills, times the odds), the passive (+damage per foe after you), Rally, Stand Your Ground and
 * the tree's mechanics. The numbers are in {@link CourageMath}.
 *
 * <p>Endurance pays for damage taken and owns surviving at low health (Grit, Last Stand,
 * Indomitable, Adrenal Mend). Courage never pays for a hit you take; it pays for the hits you
 * give while the fight is against you.
 *
 * <p>Anti-farm rules, all here:
 * <ul>
 * <li>Nothing at even or favourable odds ({@link CourageMath#odds} is 0).</li>
 * <li>Only while you are in a fight: a mob (not a pet) hurt you within the last 20 seconds. A
 * mob farm, where mobs see you but cannot reach you, never opens that window.</li>
 * <li>A foe counts toward the crowd only if it attacked you itself within that window, is still
 * after you and is not stuck in a boat or a minecart. Mobs that only watch you through bars or
 * glass never count, even when one other mob keeps the window open.</li>
 * <li>The target must be a threat: a hostile mob, or one that is after you. Not a player, not
 * someone's pet, not a cow.</li>
 * <li>One mob pays for at most its own max health of damage, so a trapped mob that heals is not
 * a farm; one hit counts at most 20 damage.</li>
 * <li>Survival or adventure only.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class CourageEvents {

    /** On a mob: the damage it has already paid Courage XP for. Saved with the mob. */
    public static final String PAID_TAG = "proficiency_courage_paid";

    private static final TagKey<EntityType<?>> NOTABLE_BOSSES = TagKey.create(
            Registries.ENTITY_TYPE, Proficiency.id("notable_bosses"));

    /** Game time a mob last hurt each player. The "in a fight" window. */
    private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();
    /** Per player: each mob that attacked them, and when. Only these count toward the crowd. */
    private static final Map<UUID, Map<UUID, Long>> ENGAGED = new ConcurrentHashMap<>();
    /** The damage of each player's last blow before armour, so a bow kill reads the arrow, not the bow. */
    private static final Map<UUID, Float> LAST_BLOW = new ConcurrentHashMap<>();
    /** Unshaken re-adds a shorter effect; this stops that from being shortened again. */
    private static final ThreadLocal<Boolean> UNSHAKING = ThreadLocal.withInitial(() -> false);

    private CourageEvents() {
    }

    // ---- Reading the fight --------------------------------------------------------------------

    public static boolean isBoss(LivingEntity entity) {
        return entity.getType().is(NOTABLE_BOSSES) || entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Someone's pet. Its hits and its hurts never count, so two friends cannot farm each other. */
    private static boolean owned(Entity entity) {
        return entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null;
    }

    /**
     * Mobs after this player within 12 blocks that attacked them in the fight window, not pets and
     * not stuck riding a boat or a minecart. {@code include} (the target) counts if it is after
     * the player, even if it just died (a kill reads the fight as it was at the blow). No area scan
     * and no ray casts: it only looks up the few mobs that attacked this player.
     */
    public static int foes(ServerPlayer player, @Nullable LivingEntity include) {
        int count = 0;
        if (include instanceof Mob mob && mob.getTarget() == player && inCrowd(player, mob)) {
            count++;
        }
        Map<UUID, Long> engaged = ENGAGED.get(player.getUUID());
        if (engaged == null || engaged.isEmpty()) {
            return count;
        }
        long now = player.level().getGameTime();
        long window = ProficiencyConfig.courageFightWindow();
        engaged.values().removeIf(time -> !CourageMath.inFight(time, now, window));
        for (UUID id : engaged.keySet()) {
            if (include != null && id.equals(include.getUUID())) {
                continue;
            }
            if (player.serverLevel().getEntity(id) instanceof Mob mob && mob.isAlive()
                    && mob.getTarget() == player && inCrowd(player, mob)) {
                count++;
            }
        }
        return count;
    }

    private static boolean inCrowd(ServerPlayer player, Mob mob) {
        if (owned(mob) || mob.distanceToSqr(player) > CourageMath.CROWD_RADIUS * CourageMath.CROWD_RADIUS) {
            return false;
        }
        Entity vehicle = mob.getVehicle();
        return vehicle == null || vehicle instanceof LivingEntity;
    }

    /** A mob attacked this player (the hit may still be blocked or absorbed). */
    private static void noteAttack(ServerPlayer player, Mob mob) {
        ENGAGED.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(mob.getUUID(), player.level().getGameTime());
    }

    /** Hostile mobs (or mobs after you) within {@code radius}: Stand Your Ground's count. */
    public static int nearbyHostiles(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        double range = radius * radius;
        return (int) player.level().getEntitiesOfClass(Mob.class, box,
                mob -> mob.isAlive() && !owned(mob) && mob.distanceToSqr(player) <= range
                        && (mob instanceof Enemy || mob.getTarget() == player)).size();
    }

    /** A target worth courage: a hostile mob, or a mob that is after you. Never a player or a pet. */
    public static boolean isThreat(LivingEntity target, ServerPlayer player) {
        if (target instanceof Player || owned(target)) {
            return false;
        }
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == player);
    }

    private static double attack(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 0.0 : attack.getValue();
    }

    /** The fight as it stands for a blow on {@code target}. */
    public static CourageMath.Fight fight(ServerPlayer player, LivingEntity target, int foes) {
        // A bow's attack attribute is a fist's; the last blow's real damage is the fairer "yours".
        double mine = Math.max(attack(player), LAST_BLOW.getOrDefault(player.getUUID(), 0f));
        return new CourageMath.Fight(foes, target.getMaxHealth(), attack(target), isBoss(target),
                player.getMaxHealth(), mine, player.getHealth(), player.getArmorValue());
    }

    /** Whether a mob hurt this player recently enough to count as a fight. */
    public static boolean inFight(ServerPlayer player) {
        return CourageMath.inFight(LAST_HURT.getOrDefault(player.getUUID(), -1L),
                player.level().getGameTime(), ProficiencyConfig.courageFightWindow());
    }

    /** Opens the fight window as if a mob just hit. For GameTests. */
    public static void noteHurtByMob(ServerPlayer player) {
        LAST_HURT.put(player.getUUID(), player.level().getGameTime());
    }

    /** Opens the fight window as if each of these mobs just hit. For GameTests. */
    public static void noteHurtBy(ServerPlayer player, Mob... mobs) {
        noteHurtByMob(player);
        for (Mob mob : mobs) {
            noteAttack(player, mob);
        }
    }

    public static void forget(UUID player) {
        LAST_HURT.remove(player);
        LAST_BLOW.remove(player);
        ENGAGED.remove(player);
    }

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself, as Endurance does: the GameTest mock claims creative.
        return player.gameMode.isSurvival();
    }

    // ---- XP: damage dealt, and the fight window ------------------------------------------------

    /**
     * Post: the damage is what the target actually lost. Two jobs: a mob hurting a player opens
     * that player's fight window, and a player's hit on a threat pays Courage at the odds.
     */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity culprit = event.getSource().getEntity();
        if (victim instanceof ServerPlayer hurt) {
            if (culprit instanceof Mob && !owned(culprit) && event.getNewDamage() > 0) {
                LAST_HURT.put(hurt.getUUID(), hurt.level().getGameTime());
            }
            return;
        }
        if (!(culprit instanceof ServerPlayer player)) {
            return;
        }
        LAST_BLOW.put(player.getUUID(), event.getOriginalDamage());
        if (!(victim instanceof Player) && event.getNewDamage() > 0) {
            // Here, not on the incoming hit: only a hit that really landed pulls the mob.
            challenge(player, victim);
        }
        payHit(player, victim, event.getNewDamage());
    }

    /** Pays one hit. Public so a GameTest can drive it with exact numbers. */
    public static float payHit(ServerPlayer player, LivingEntity target, float damage) {
        if (!counts(player) || !isThreat(target, player) || !inFight(player)
                || !ProficiencyConfig.enabled(Skill.COURAGE)) {
            return 0f;
        }
        double odds = CourageMath.odds(fight(player, target, foes(player, target)));
        if (!(odds > 0)) {
            return 0f;
        }
        float paid = target.getPersistentData().getFloat(PAID_TAG);
        double counted = CourageMath.countedDamage(damage, target.getMaxHealth() - paid);
        double xp = CourageMath.hitXp(damage, target.getMaxHealth() - paid, odds,
                ProficiencyConfig.courageXpPerDamage())
                * dev.amman.proficiency.skill.SpawnOrigin.xpFactor(target);
        if (!(xp > 0)) {
            return 0f;
        }
        target.getPersistentData().putFloat(PAID_TAG, (float) (paid + counted));
        return SkillService.grant(player, Skill.COURAGE, xp, target.getType().getDescriptionId());
    }

    // ---- Kills: XP, Rally, Lionheart, Spoils of Valor ------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (victim instanceof ServerPlayer dead) {
            // A new life is not still in the old fight.
            LAST_HURT.remove(dead.getUUID());
            ENGAGED.remove(dead.getUUID());
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !isThreat(victim, player)) {
            return;
        }
        int foes = foes(player, victim);
        CourageMath.Fight fight = fight(player, victim, foes);
        boolean outnumbered = CourageMath.outnumbered(foes);

        if (outnumbered && ProcService.fire(player, Skill.COURAGE, victim)) {
            rally(player);
        }
        if (outnumbered && TalentService.rank(player, Skill.COURAGE, "lionheart") > 0) {
            ProficiencyAttachments.of(player).shortenCooldown(Skill.COURAGE,
                    CourageMath.LIONHEART_COOLDOWN_TICKS, player.level().getGameTime());
        }
        float heal = CourageMath.valorHeal(TalentService.rank(player, Skill.COURAGE, "valor_heal"),
                CourageMath.strengthRatio(fight.foeMaxHealth(), fight.foeAttack(), fight.myMaxHealth(),
                        fight.myAttack()), fight.boss());
        if (heal > 0) {
            player.heal(heal);
        }
        payKill(player, victim, fight);
    }

    private static void payKill(ServerPlayer player, LivingEntity victim, CourageMath.Fight fight) {
        if (!counts(player) || !inFight(player) || !ProficiencyConfig.enabled(Skill.COURAGE)) {
            return;
        }
        double xp = CourageMath.killXp(CourageMath.odds(fight), ProficiencyConfig.courageKillXp())
                * dev.amman.proficiency.skill.SpawnOrigin.xpFactor(victim);
        if (xp > 0) {
            SkillService.grant(player, Skill.COURAGE, xp, victim.getType().getDescriptionId());
        }
    }

    /**
     * Rally: Strength I (longer with proc power) and a short Speed II burst. Rallying Cry shares
     * it with every player within 8 blocks.
     */
    public static void rally(ServerPlayer player) {
        int ticks = CourageMath.rallyTicks(ProcService.power(player, Skill.COURAGE));
        buff(player, ticks);
        if (TalentService.rank(player, Skill.COURAGE, "rally_friends") > 0) {
            double range = CourageMath.RALLY_CRY_RADIUS * CourageMath.RALLY_CRY_RADIUS;
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other != player && other.isAlive() && !other.isSpectator()
                        && other.distanceToSqr(player) <= range) {
                    buff(other, ticks);
                }
            }
        }
    }

    private static void buff(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, CourageMath.RALLY_SPEED_TICKS, 1,
                false, false, true));
    }

    // ---- The passive, Stand Your Ground, Giant Slayer, Challenge, Hold the Line ----------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        Entity culprit = event.getSource().getEntity();
        if (target instanceof ServerPlayer victim) {
            if (culprit instanceof Mob attacker && !owned(attacker)) {
                noteAttack(victim, attacker);
            }
            // Hold the Line: surrounded, you take less from the mobs doing it.
            if (culprit instanceof Mob) {
                int rank = TalentService.rank(victim, Skill.COURAGE, "hold_the_line");
                if (rank > 0) {
                    double multiplier = CourageMath.holdTheLine(rank, foes(victim, null));
                    if (multiplier < 1.0) {
                        event.setAmount((float) (event.getAmount() * multiplier));
                    }
                }
            }
            return;
        }
        if (!(culprit instanceof ServerPlayer player) || target instanceof Player) {
            return;
        }
        double multiplier = damageMultiplier(player, target);
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /** Everything Courage adds to a blow on a mob: the passive, the ability, Giant Slayer. */
    public static double damageMultiplier(ServerPlayer player, LivingEntity target) {
        double passive = SkillService.bonus(player, Skill.COURAGE);
        double multiplier = 1.0;
        if (passive > 0) {
            boolean lionheart = TalentService.rank(player, Skill.COURAGE, "lionheart") > 0;
            multiplier = CourageMath.passiveMultiplier(passive, foes(player, target), lionheart);
        }
        if (ActiveService.isFrenzied(player, Skill.COURAGE)) {
            multiplier *= CourageMath.standMultiplier(nearbyHostiles(player, CourageMath.STAND_RADIUS));
        }
        multiplier *= CourageMath.giantSlayer(TalentService.rank(player, Skill.COURAGE, "giant_slayer"),
                isBoss(target), target.getMaxHealth());
        return multiplier;
    }

    /** Challenge: a mob after a friend near you turns on you when you hit it. */
    public static void challenge(ServerPlayer player, LivingEntity target) {
        if (!(target instanceof Mob mob) || !(mob.getTarget() instanceof Player friend) || friend == player) {
            return;
        }
        double radius = CourageMath.challengeRadius(TalentService.rank(player, Skill.COURAGE, "challenge"));
        if (radius > 0 && friend.distanceToSqr(player) <= radius * radius) {
            mob.setTarget(player);
        }
    }

    /** Stand Your Ground: nothing shoves you while it runs. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ActiveService.isFrenzied(player, Skill.COURAGE)) {
            event.setCanceled(true);
        }
    }

    // ---- Unshaken -----------------------------------------------------------------------------

    /**
     * Unshaken: Slowness and Weakness put on you by a mob (a Stray's arrow, a Witch's potion) are
     * a third shorter per rank, and never land at rank 3. A potion you drink yourself is untouched.
     */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (UNSHAKING.get() || effect == null
                || !(effect.is(MobEffects.MOVEMENT_SLOWDOWN) || effect.is(MobEffects.WEAKNESS))
                || !(event.getEntity() instanceof ServerPlayer player)
                || !fromMob(event.getEffectSource())) {
            return;
        }
        int rank = TalentService.rank(player, Skill.COURAGE, "fearless");
        int shorter = CourageMath.unshakenTicks(effect.getDuration(), rank);
        if (shorter >= effect.getDuration()) {
            return;
        }
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (shorter <= 0) {
            return;
        }
        UNSHAKING.set(true);
        try {
            player.addEffect(new MobEffectInstance(effect.getEffect(), shorter, effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()), event.getEffectSource());
        } finally {
            UNSHAKING.set(false);
        }
    }

    private static boolean fromMob(@Nullable Entity source) {
        if (source instanceof Projectile projectile) {
            source = projectile.getOwner();
        }
        return source instanceof Mob && !owned(source);
    }

    // ---- Fearless Heart (with Endurance) ------------------------------------------------------

    /** Fearless Heart: the hit you took becomes the hit you give. A Grit also gives Strength I. */
    @SubscribeEvent
    public static void onProc(SkillProcEvent event) {
        if (event.skill() == Skill.ENDURANCE && TalentService.hasSynergy(event.player(), "fearless_heart")) {
            event.player().addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                    CourageMath.FEARLESS_HEART_TICKS, 0, false, false, true));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }
}
