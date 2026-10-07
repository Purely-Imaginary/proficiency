package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.ChargerMath;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.TacticianMath;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tactician: the back line (idea 40), the mirror of Charger. It owns the XP (support, rescue and
 * Overwatch shots), the passive (more ranged damage on a mob that is after someone else), Called
 * Shot and its mark, Suppressing Fire, Clear Line, the tree's mechanics, and the two pair
 * effects: Hammer and Anvil with Charger and Covering Fire with Guardian. The numbers are in
 * {@link TacticianMath}.
 *
 * <p>Archery and Crossbows own raw ranged damage (Longshot, Siege Master, Hunter's Mark on your
 * own damage). Sneaking owns hits from behind a mob. Tactician pays for position and team play:
 * shooting what is after your friends, from behind your own front line.
 *
 * <p>Anti-farm rules, all here:
 * <ul>
 * <li>Ranged only: the damage must come through a projectile or a cloud you own, never your hand.</li>
 * <li>Every source needs another player the mob is fighting: it is after them, or it hurt them
 * (or you pulled it off them) in the last 5 s. A mob after nobody, or only after you, never pays:
 * a skeleton farm is worth nothing.</li>
 * <li>Support pays per point of damage, at most 20 per hit and 40 per mob (saved on the mob).
 * Overwatch pays at most 3 times per mob, and only from 6 blocks away.</li>
 * <li>The spot rule: one spot (12 blocks) pays at most 48 base XP in 5 minutes.</li>
 * <li>A mob only makes danger when it is nobody's pet. Damage between players never counts.</li>
 * <li>Survival or adventure only.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TacticianEvents {

    /** On a mob: the damage it already paid support XP for. */
    public static final String SUPPORT_PAID = "proficiency_tactician_support_paid";
    /** On a mob: how many Overwatch hits it already paid for. */
    public static final String OVERWATCH_PAID = "proficiency_tactician_overwatch_paid";
    /** On a mob: the last player it hurt, and when. */
    private static final String HURT_FRIEND = "proficiency_tactician_hurt_friend";
    private static final String HURT_AT = "proficiency_tactician_hurt_at";
    /** On a mob: the player Suppressing Fire pulled it off, and when. */
    private static final String PULLED_FROM = "proficiency_tactician_pulled_from";
    private static final String PULLED_AT = "proficiency_tactician_pulled_at";
    /** On a mob: Covering Fire, until when, and whether the synergy made it stronger. */
    private static final String COVER_UNTIL = "proficiency_tactician_cover_until";
    private static final String COVER_STRONG = "proficiency_tactician_cover_strong";

    public static final String SOURCE_SUPPORT = "proficiency.xplog.source.tactician_support";
    public static final String SOURCE_RESCUE = "proficiency.xplog.source.tactician_rescue";
    public static final String SOURCE_OVERWATCH = "proficiency.xplog.source.tactician_overwatch";

    /** The scoreboard team marked mobs join, so their glow is red and not Hunter's Mark's white. */
    public static final String TEAM = "proficiency_called_shot";

    /** Every live Called Shot mark, keyed by the marked mob. */
    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
    /** Per player: recent payouts, for the spot rule. */
    private static final Map<UUID, ArrayDeque<Spot>> SPOTS = new ConcurrentHashMap<>();

    private static volatile boolean initialised;

    /**
     * A Called Shot mark: who placed it, on what, until when, and who was told (they are told
     * again when it ends). {@code joinedTeam} says whether this mark put the mob in the red team,
     * so ending it never takes a mob out of a team someone else put it in.
     */
    public record Mark(UUID marker, UUID mob, int entityId, ResourceKey<Level> dimension, long until,
            Set<UUID> receivers, boolean joinedTeam) {
    }

    private record Spot(long tick, double x, double z, double xp) {
    }

    private TacticianEvents() {
    }

    /** Called once from the mod constructor: Tactician's side of Hammer and Anvil. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        ChargerEvents.addFirstBloodBoost(TacticianEvents::hammerAndAnvil);
    }

    // ---- Who counts ----------------------------------------------------------------------------

    private static boolean owned(@Nullable Entity entity) {
        return entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null;
    }

    /** A mob worth shooting: nobody's pet, and hostile or after a player. Never a player or a cow. */
    public static boolean isThreat(@Nullable Entity entity) {
        return entity instanceof Mob mob && !owned(mob)
                && (mob instanceof Enemy || mob.getTarget() instanceof Player);
    }

    /**
     * A ranged hit: the player caused it through a projectile (an arrow, a trident, a snowball, a
     * spell, a thrown potion) or a cloud they own. Never their own hand.
     */
    public static boolean ranged(DamageSource source, ServerPlayer player) {
        Entity direct = source.getDirectEntity();
        return source.getEntity() == player && direct != null && direct != player
                && (direct instanceof Projectile || direct instanceof AreaEffectCloud);
    }

    /** Another player: alive, not a spectator, not you, in your world. */
    public static boolean isAlly(ServerPlayer player, @Nullable Entity other) {
        return other instanceof ServerPlayer friend && friend != player && friend.isAlive()
                && !friend.isSpectator() && friend.level() == player.level();
    }

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself: the GameTest mock claims creative.
        return player.gameMode.isSurvival() && ProficiencyConfig.enabled(Skill.TACTICIAN);
    }

    private static int level(ServerPlayer player) {
        return SkillService.level(player, Skill.TACTICIAN);
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    @Nullable
    private static ServerPlayer online(@Nullable MinecraftServer server, @Nullable UUID id) {
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static double worth(LivingEntity mob) {
        return ChargerMath.worth(mob.getMaxHealth(), CourageEvents.isBoss(mob));
    }

    /**
     * The friend this mob is fighting, seen from {@code you}: the player it is after, else one it
     * hurt in the last 5 s, else one your Suppressing Fire pulled it off in the last 5 s. Null
     * when it is after nobody or only you.
     */
    @Nullable
    public static ServerPlayer engagedFriend(ServerPlayer you, Mob mob) {
        if (isAlly(you, mob.getTarget())) {
            return (ServerPlayer) mob.getTarget();
        }
        CompoundTag data = mob.getPersistentData();
        long now = now(mob);
        if (data.hasUUID(HURT_FRIEND) && TacticianMath.engaged(data.getLong(HURT_AT), now)) {
            ServerPlayer hurt = online(you.server, data.getUUID(HURT_FRIEND));
            if (isAlly(you, hurt)) {
                return hurt;
            }
        }
        if (data.hasUUID(PULLED_FROM) && TacticianMath.engaged(data.getLong(PULLED_AT), now)) {
            ServerPlayer from = online(you.server, data.getUUID(PULLED_FROM));
            if (isAlly(you, from)) {
                return from;
            }
        }
        return null;
    }

    /**
     * How many friends stand between you and the mob (the "front line"), counting only those
     * within {@code radius} of you.
     */
    public static int friendsBetween(ServerPlayer you, Entity target, double radius) {
        double range = radius * radius;
        int count = 0;
        for (ServerPlayer other : you.serverLevel().players()) {
            if (!isAlly(you, other) || other.distanceToSqr(you) > range) {
                continue;
            }
            if (TacticianMath.between(you.getX(), you.getY(), you.getZ(), other.getX(), other.getY(), other.getZ(),
                    target.getX(), target.getY(), target.getZ())) {
                count++;
            }
        }
        return count;
    }

    /** Overwatch for one shot: a friend within 16 blocks of you stands between you and the mob. */
    public static boolean overwatch(ServerPlayer you, Entity target) {
        return friendsBetween(you, target, TacticianMath.OVERWATCH_FRIEND_RADIUS) > 0;
    }

    // ---- The hit on the way in: damage -----------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (target instanceof ServerPlayer victim) {
            if (clearLine(victim, source)) {
                event.setCanceled(true);
                return;
            }
            coveringFireCut(victim, source, event);
            return;
        }
        double multiplier = 1.0;
        Mark mark = liveMark(target);
        if (mark != null && source.getEntity() instanceof ServerPlayer hitter
                && !hitter.getUUID().equals(mark.marker())) {
            multiplier *= markMultiplier(online(hitter.server, mark.marker()));
        }
        if (source.getEntity() instanceof ServerPlayer player && ranged(source, player) && target instanceof Mob mob
                && isThreat(mob)) {
            if (isAlly(player, mob.getTarget())) {
                multiplier *= TacticianMath.passiveMultiplier(SkillService.bonus(player, Skill.TACTICIAN));
            }
            int headshot = TalentService.rank(player, Skill.TACTICIAN, "tactician_headshot");
            if (headshot > 0 && source.getDirectEntity() instanceof Projectile projectile
                    && TacticianMath.headshot(hitY(projectile, mob), mob.getEyeY())) {
                multiplier *= TacticianMath.headshotMultiplier(headshot);
            }
            int crossfire = TalentService.rank(player, Skill.TACTICIAN, "crossfire");
            if (crossfire > 0) {
                multiplier *= TacticianMath.crossfireMultiplier(crossfire,
                        friendsBetween(player, mob, Double.POSITIVE_INFINITY));
            }
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /** A friend's damage on a mob someone marked, times this. The marker's own damage is not boosted. */
    private static double markMultiplier(@Nullable ServerPlayer marker) {
        if (marker == null) {
            return TacticianMath.markMultiplier(1.0, false);
        }
        return TacticianMath.markMultiplier(ProcService.power(marker, Skill.TACTICIAN),
                TalentService.rank(marker, Skill.TACTICIAN, "mark_long") > 0);
    }

    /**
     * Where a projectile meets the mob, as a height. The projectile is still where it was a tick
     * ago when the hit is dealt, so trace its last move into the mob's box.
     */
    private static double hitY(Projectile projectile, LivingEntity mob) {
        Vec3 from = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        return mob.getBoundingBox().inflate(0.3).clip(from.subtract(motion), from.add(motion))
                .map(hit -> hit.y).orElse(projectile.getY());
    }

    // ---- Clear Line ------------------------------------------------------------------------------

    /** Clear Line: your projectiles fly on through other players. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || !(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (projectile.getOwner() instanceof ServerPlayer shooter && isAlly(shooter, hit.getEntity())
                && TalentService.rank(shooter, Skill.TACTICIAN, "clear_line") > 0) {
            event.setCanceled(true);
        }
    }

    /**
     * Clear Line, the second half: nothing you shoot or throw hurts another player (a firework's
     * blast, a splash of Harming, a lingering cloud). Your own hand still does.
     */
    public static boolean clearLine(ServerPlayer victim, DamageSource source) {
        return source.getEntity() instanceof ServerPlayer shooter && shooter != victim
                && source.getDirectEntity() != shooter && source.getDirectEntity() != null
                && TalentService.rank(shooter, Skill.TACTICIAN, "clear_line") > 0;
    }

    // ---- The hit lands: XP, Called Shot, Suppressing Fire, Covering Fire -------------------------

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getNewDamage() > 0)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity culprit = source.getEntity();
        if (victim instanceof ServerPlayer hurt) {
            // Only a mob (nobody's pet) makes danger: two friends hitting each other count for nothing.
            if (culprit instanceof Mob mob && !owned(mob)) {
                mob.getPersistentData().putUUID(HURT_FRIEND, hurt.getUUID());
                mob.getPersistentData().putLong(HURT_AT, now(mob));
            }
            return;
        }
        if (!(culprit instanceof ServerPlayer player) || !ranged(source, player) || !(victim instanceof Mob mob)
                || !isThreat(mob)) {
            return;
        }
        float damage = event.getNewDamage();
        if (counts(player)) {
            paySupport(player, mob, damage);
            if (overwatch(player, mob)) {
                payOverwatch(player, mob, player.distanceTo(mob));
            }
        }
        coveringFire(player, mob);
        if (ProcService.fire(player, Skill.TACTICIAN, mob)) {
            mark(player, mob);
        }
        if (ActiveService.isFrenzied(player, Skill.TACTICIAN)) {
            suppress(player, mob);
        }
    }

    // ---- XP source 1: support ------------------------------------------------------------------

    /**
     * Pays a ranged hit on a mob that is after another player: per point of damage, at most 20 per
     * hit and 40 per mob. Public so a GameTest can drive it. Returns the XP.
     */
    public static float paySupport(ServerPlayer player, Mob mob, float damage) {
        if (!counts(player) || !isAlly(player, mob.getTarget())) {
            return 0f;
        }
        CompoundTag data = mob.getPersistentData();
        float already = data.getFloat(SUPPORT_PAID);
        double counted = TacticianMath.supportCounted(Math.min(damage, mob.getMaxHealth()), already);
        double xp = TacticianMath.supportXp(counted, ProficiencyConfig.tacticianSupportXp());
        float paid = pay(player, mob, xp, SOURCE_SUPPORT);
        if (paid > 0) {
            data.putFloat(SUPPORT_PAID, (float) (already + counted));
        }
        return paid;
    }

    // ---- XP source 2: rescue -------------------------------------------------------------------

    /**
     * Pays a ranged kill of a mob that hurt another player in the last 5 s, with that player within
     * 32 blocks of you. Public so a GameTest can drive it. Returns the XP.
     */
    public static float payRescue(ServerPlayer player, Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!counts(player) || !data.hasUUID(HURT_FRIEND)
                || !TacticianMath.engaged(data.getLong(HURT_AT), now(mob))) {
            return 0f;
        }
        ServerPlayer friend = online(player.server, data.getUUID(HURT_FRIEND));
        double range = TacticianMath.RESCUE_RADIUS * TacticianMath.RESCUE_RADIUS;
        if (!isAlly(player, friend) || friend.distanceToSqr(player) > range) {
            return 0f;
        }
        return pay(player, mob, TacticianMath.rescueXp(ProficiencyConfig.tacticianRescueXp(), worth(mob)),
                SOURCE_RESCUE);
    }

    // ---- XP source 3: Overwatch ----------------------------------------------------------------

    /**
     * Pays a ranged hit in Overwatch, by distance: the mob is fighting a friend, and a friend
     * stands between you and it. At most 3 per mob. Public so a GameTest can drive it.
     */
    public static float payOverwatch(ServerPlayer player, Mob mob, double distance) {
        CompoundTag data = mob.getPersistentData();
        int paid = data.getInt(OVERWATCH_PAID);
        if (!counts(player) || paid >= TacticianMath.OVERWATCH_PAYS_PER_MOB || engagedFriend(player, mob) == null) {
            return 0f;
        }
        double xp = TacticianMath.overwatchXp(distance, ProficiencyConfig.tacticianOverwatchXp());
        float got = pay(player, mob, xp, SOURCE_OVERWATCH);
        if (got > 0) {
            data.putInt(OVERWATCH_PAID, paid + 1);
        }
        return got;
    }

    /**
     * Pays through the spot rule, then Hammer and Anvil: a mob that is fighting a Charger (not
     * you) pays double. The spot counts the XP before the doubling.
     */
    private static float pay(ServerPlayer player, Mob at, double xp, String source) {
        if (!(xp > 0)) {
            return 0f;
        }
        double room = TacticianMath.spotRoom(recentNear(player, at.getX(), at.getZ(), now(player)));
        double base = Math.min(xp, room);
        if (!(base > 0)) {
            return 0f;
        }
        noteSpot(player, at, base);
        double amount = fightingACharger(player, at) ? base * TacticianMath.PAIR_XP : base;
        return SkillService.grant(player, Skill.TACTICIAN, amount, source);
    }

    /**
     * Hammer and Anvil: the mob is fighting a Charger who is not you and has Charger level 10 or
     * more (a Charger's melee hit in the last 5 s, or it is after a player in Spearhead).
     */
    public static boolean fightingACharger(ServerPlayer player, Mob mob) {
        ServerPlayer charger = ChargerEvents.chargerFighting(mob);
        return charger != null && charger != player
                && SkillService.level(charger, Skill.CHARGER) >= TacticianMath.PAIR_LEVEL;
    }

    // ---- The spot rule -------------------------------------------------------------------------

    /** How much base Tactician XP this player got within 12 blocks of here in the last 5 minutes. */
    public static double recentNear(ServerPlayer player, double x, double z, long now) {
        ArrayDeque<Spot> spots = SPOTS.get(player.getUUID());
        if (spots == null) {
            return 0.0;
        }
        synchronized (spots) {
            spots.removeIf(spot -> !TacticianMath.inSpotWindow(spot.tick(), now));
            double range = TacticianMath.SPOT_RADIUS * TacticianMath.SPOT_RADIUS;
            double near = 0.0;
            for (Spot spot : spots) {
                double dx = spot.x() - x;
                double dz = spot.z() - z;
                if (dx * dx + dz * dz <= range) {
                    near += spot.xp();
                }
            }
            return near;
        }
    }

    private static void noteSpot(ServerPlayer player, Entity at, double xp) {
        ArrayDeque<Spot> spots = SPOTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (spots) {
            spots.add(new Spot(now(player), at.getX(), at.getZ(), xp));
            while (spots.size() > 512) {
                spots.poll();
            }
        }
    }

    // ---- Called Shot: the mark -------------------------------------------------------------------

    /** The live mark on this mob, or null. */
    @Nullable
    public static Mark liveMark(Entity mob) {
        Mark mark = MARKS.get(mob.getUUID());
        if (mark == null || !TacticianMath.markHolds(mark.until(), now(mob))) {
            return null;
        }
        return mark;
    }

    /** Every live mark. For GameTests. */
    public static List<Mark> marks() {
        return List.copyOf(MARKS.values());
    }

    /**
     * Called Shot lands: the mob is marked for 8 s (longer with Painted Target and Field Marshal).
     * It glows red for everyone (a scoreboard team colour, so not Hunter's Mark's white), every
     * player within 48 blocks of it gets the crosshair icon over its head, and friends hear a
     * short sound and read "Amman marked Zombie" on the action bar. Returns the mark.
     */
    public static Mark mark(ServerPlayer marker, LivingEntity mob) {
        long now = now(mob);
        boolean painted = TalentService.rank(marker, Skill.TACTICIAN, "mark_long") > 0;
        boolean marshal = TalentService.rank(marker, Skill.TACTICIAN, "field_marshal") > 0;
        int ticks = TacticianMath.markTicks(painted, marshal);

        Mark old = MARKS.remove(mob.getUUID());
        boolean fresh = old == null || !old.marker().equals(marker.getUUID())
                || !TacticianMath.markHolds(old.until(), now);
        dropOldest(marker, mob.getServer());

        Set<UUID> audience = audience(marker, mob);
        boolean joined = (old != null && old.joinedTeam()) || joinTeam(mob);
        Mark mark = new Mark(marker.getUUID(), mob.getUUID(), mob.getId(), mob.level().dimension(), now + ticks,
                Set.copyOf(audience), joined);
        MARKS.put(mob.getUUID(), mark);
        if (old != null) {
            // Anyone the old mark told who is not told again loses the icon now.
            for (UUID id : old.receivers()) {
                if (!audience.contains(id)) {
                    ServerPlayer gone = online(mob.getServer(), id);
                    if (gone != null) {
                        ProficiencyNetwork.sendCalledShot(gone, mob.getId(), 0, "");
                    }
                }
            }
        }

        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), marker);
        String name = marker.getGameProfile().getName();
        for (UUID id : audience) {
            ServerPlayer viewer = online(mob.getServer(), id);
            if (viewer == null) {
                continue;
            }
            ProficiencyNetwork.sendCalledShot(viewer, mob.getId(), ticks, name);
            if (fresh && viewer != marker) {
                viewer.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5f, 1.6f);
                viewer.displayClientMessage(Component.translatable("proficiency.tactician.marked",
                        marker.getDisplayName(), mob.getDisplayName()).withStyle(ChatFormatting.RED), true);
            }
        }
        if (TalentService.rank(marker, Skill.TACTICIAN, "mark_opens") > 0) {
            ChargerEvents.reopenFirstBlood(mob);
        }
        return mark;
    }

    /**
     * Who is told about a mark: the Tactician who placed it, and every other player within 48
     * blocks of the mob, in its world. Nobody farther away.
     */
    public static Set<UUID> audience(ServerPlayer marker, LivingEntity mob) {
        Set<UUID> audience = new HashSet<>();
        audience.add(marker.getUUID());
        if (!(mob.level() instanceof ServerLevel level)) {
            return audience;
        }
        double range = TacticianMath.MARK_AUDIENCE_RADIUS * TacticianMath.MARK_AUDIENCE_RADIUS;
        for (ServerPlayer other : level.players()) {
            if (other != marker && other.isAlive() && !other.isSpectator() && other.distanceToSqr(mob) <= range) {
                audience.add(other.getUUID());
            }
        }
        return audience;
    }

    /** One Tactician keeps at most 4 marks: a fifth drops the oldest. */
    private static void dropOldest(ServerPlayer marker, @Nullable MinecraftServer server) {
        List<Mark> mine = new ArrayList<>();
        for (Mark mark : MARKS.values()) {
            if (mark.marker().equals(marker.getUUID())) {
                mine.add(mark);
            }
        }
        if (mine.size() < TacticianMath.MARKS_PER_PLAYER) {
            return;
        }
        mine.sort(Comparator.comparingLong(Mark::until));
        for (int i = 0; i <= mine.size() - TacticianMath.MARKS_PER_PLAYER; i++) {
            endMark(mine.get(i), server, true);
        }
    }

    /**
     * Ends a mark: takes the mob out of the red team (if this mark put it there) and tells everyone
     * who got the icon to drop it. {@code early} also takes the glow off a mob that is still alive.
     */
    public static void endMark(Mark mark, @Nullable MinecraftServer server, boolean early) {
        if (!MARKS.remove(mark.mob(), mark)) {
            return;
        }
        if (server == null) {
            return;
        }
        if (mark.joinedTeam()) {
            leaveTeam(server.getScoreboard(), mark.mob().toString());
        }
        ServerLevel level = server.getLevel(mark.dimension());
        Entity entity = level == null ? null : level.getEntity(mark.mob());
        if (early && entity instanceof LivingEntity living && living.isAlive()) {
            living.removeEffect(MobEffects.GLOWING);
        }
        for (UUID id : mark.receivers()) {
            ServerPlayer viewer = online(server, id);
            if (viewer != null) {
                ProficiencyNetwork.sendCalledShot(viewer, mark.entityId(), 0, "");
            }
        }
    }

    /** Every half second: marks that ran out, or whose mob is gone, end. Public for GameTests. */
    public static int sweepMarks(MinecraftServer server) {
        int ended = 0;
        for (Mark mark : List.copyOf(MARKS.values())) {
            ServerLevel level = server.getLevel(mark.dimension());
            Entity entity = level == null ? null : level.getEntity(mark.mob());
            boolean alive = entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved();
            long now = level == null ? server.overworld().getGameTime() : level.getGameTime();
            if (!alive || !TacticianMath.markHolds(mark.until(), now)) {
                endMark(mark, server, false);
                ended++;
            }
        }
        return ended;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 == 0 && !MARKS.isEmpty()) {
            sweepMarks(event.getServer());
        }
    }

    // ---- The red team ----------------------------------------------------------------------------

    /** Puts the mob in the red team, unless it is already in some team. Returns whether it joined. */
    private static boolean joinTeam(LivingEntity mob) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        Scoreboard board = server.getScoreboard();
        String name = mob.getScoreboardName();
        if (board.getPlayersTeam(name) != null) {
            return false;
        }
        PlayerTeam team = board.getPlayerTeam(TEAM);
        if (team == null) {
            team = board.addPlayerTeam(TEAM);
        }
        team.setColor(ChatFormatting.RED);
        return board.addPlayerToTeam(name, team);
    }

    private static void leaveTeam(Scoreboard board, String name) {
        PlayerTeam team = board.getPlayerTeam(TEAM);
        if (team != null && board.getPlayersTeam(name) == team) {
            board.removePlayerFromTeam(name, team);
        }
    }

    /** Empties the red team: after a crash it may still hold mobs from the last run. */
    private static void emptyTeam(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        PlayerTeam team = board.getPlayerTeam(TEAM);
        if (team == null) {
            return;
        }
        for (String name : List.copyOf(team.getPlayers())) {
            board.removePlayerFromTeam(name, team);
        }
    }

    /** Whether this mob is in the red team now. For GameTests. */
    public static boolean inRedTeam(LivingEntity mob) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return false;
        }
        PlayerTeam team = server.getScoreboard().getPlayersTeam(mob.getScoreboardName());
        return team != null && TEAM.equals(team.getName());
    }

    // ---- Hammer and Anvil, the Charger side ----------------------------------------------------

    /**
     * A Charger's first blood on a mob another player marked hits x1.5 harder, x2 when either of
     * the two has the Hammer and Anvil synergy. Registered with {@link ChargerEvents}.
     */
    public static double hammerAndAnvil(ServerPlayer charger, LivingEntity target) {
        Mark mark = liveMark(target);
        if (mark == null || mark.marker().equals(charger.getUUID())) {
            return 1.0;
        }
        ServerPlayer marker = online(charger.server, mark.marker());
        boolean synergy = TalentService.hasSynergy(charger, "hammer_and_anvil")
                || (marker != null && TalentService.hasSynergy(marker, "hammer_and_anvil"));
        return TacticianMath.pairFirstBlood(true, synergy);
    }

    // ---- Suppressing Fire ------------------------------------------------------------------------

    /**
     * Suppressing Fire, on each ranged hit while it runs: the mob is slowed (Slowness II for 3 s,
     * III with Field Marshal), and if it is after another player it turns on you. It still counts
     * as fighting that player for 5 s, so pulling it off them is not a loss.
     */
    public static void suppress(ServerPlayer player, Mob mob) {
        boolean marshal = TalentService.rank(player, Skill.TACTICIAN, "field_marshal") > 0;
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TacticianMath.SUPPRESS_SLOW_TICKS,
                TacticianMath.suppressAmplifier(marshal)), player);
        if (isAlly(player, mob.getTarget())) {
            CompoundTag data = mob.getPersistentData();
            data.putUUID(PULLED_FROM, mob.getTarget().getUUID());
            data.putLong(PULLED_AT, now(mob));
            mob.setTarget(player);
        }
    }

    // ---- Covering Fire (with Guardian) -----------------------------------------------------------

    /**
     * Covering Fire: a ranged hit from a Tactician (level 10) on a mob that is after a Guardian
     * (level 10) makes that mob hit players 25% softer for 3 s. With the synergy on either of the
     * two, 40% for 6 s. Returns whether the mob is now covered.
     */
    public static boolean coveringFire(ServerPlayer player, Mob mob) {
        if (level(player) < TacticianMath.PAIR_LEVEL || !(mob.getTarget() instanceof ServerPlayer guardian)
                || !isAlly(player, guardian)
                || SkillService.level(guardian, Skill.GUARDIAN) < TacticianMath.GUARDIAN_LEVEL) {
            return false;
        }
        boolean synergy = TalentService.hasSynergy(player, "covering_fire")
                || TalentService.hasSynergy(guardian, "covering_fire");
        CompoundTag data = mob.getPersistentData();
        data.putLong(COVER_UNTIL, now(mob) + TacticianMath.coverTicks(synergy));
        data.putBoolean(COVER_STRONG, synergy);
        return true;
    }

    private static void coveringFireCut(ServerPlayer victim, DamageSource source, LivingIncomingDamageEvent event) {
        if (!(source.getEntity() instanceof Mob mob)) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(COVER_UNTIL) || now(mob) >= data.getLong(COVER_UNTIL)) {
            return;
        }
        event.setAmount((float) (event.getAmount() * TacticianMath.coverMultiplier(data.getBoolean(COVER_STRONG))));
    }

    // ---- Deaths: rescue kills, and what a mark does when its mob dies ----------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof Player) {
            return;
        }
        DamageSource source = event.getSource();
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (killer != null && victim instanceof Mob mob && ranged(source, killer) && isThreat(mob)) {
            payRescue(killer, mob);
        }
        Mark mark = MARKS.get(victim.getUUID());
        if (mark != null) {
            markedDeath(mark, victim, source, killer);
        }
    }

    /**
     * A marked mob died. Field Marshal: a friend's kill takes 3 s off the marker's Suppressing
     * Fire. Quartermaster: the marker's own ranged kill may give the arrow back. Focus Fire: the
     * mark jumps to the nearest hostile within 8 blocks. Then the mark ends.
     */
    private static void markedDeath(Mark mark, LivingEntity victim, DamageSource source, @Nullable ServerPlayer killer) {
        MinecraftServer server = victim.getServer();
        ServerPlayer marker = online(server, mark.marker());
        endMark(mark, server, false);
        if (marker == null) {
            return;
        }
        long now = now(victim);
        if (killer != null && killer != marker && TalentService.rank(marker, Skill.TACTICIAN, "field_marshal") > 0) {
            ProficiencyAttachments.of(marker).shortenCooldown(Skill.TACTICIAN,
                    TacticianMath.FIELD_MARSHAL_COOLDOWN_TICKS, now);
        }
        if (killer == marker) {
            refund(marker, source);
        }
        if (TalentService.rank(marker, Skill.TACTICIAN, "mark_jump") > 0) {
            Mob next = jumpTarget(victim);
            if (next != null) {
                mark(marker, next);
            }
        }
    }

    /** Quartermaster: a 1 in 3 chance per rank to get the arrow back. Never for a free arrow. */
    public static boolean refund(ServerPlayer marker, DamageSource source) {
        int rank = TalentService.rank(marker, Skill.TACTICIAN, "mark_refund");
        if (rank <= 0 || !(source.getDirectEntity() instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident
                || arrow.pickup != AbstractArrow.Pickup.ALLOWED) {
            return false;
        }
        if (marker.getRandom().nextDouble() >= TacticianMath.refundChance(rank)) {
            return false;
        }
        marker.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW));
        return true;
    }

    /** Focus Fire: the nearest hostile mob within 8 blocks of the one that died, not already marked. */
    @Nullable
    private static Mob jumpTarget(LivingEntity dead) {
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        double range = TacticianMath.MARK_JUMP_RADIUS * TacticianMath.MARK_JUMP_RADIUS;
        for (Mob mob : dead.level().getEntitiesOfClass(Mob.class,
                dead.getBoundingBox().inflate(TacticianMath.MARK_JUMP_RADIUS))) {
            double distance = mob.distanceToSqr(dead);
            if (mob == dead || !mob.isAlive() || !(mob instanceof Enemy) || owned(mob) || distance > range
                    || liveMark(mob) != null) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    // ---- Forgetting ----------------------------------------------------------------------------

    /** Drops everything kept for this player, and ends their marks. On logout, and for GameTests. */
    public static void forget(UUID player, @Nullable MinecraftServer server) {
        SPOTS.remove(player);
        for (Mark mark : List.copyOf(MARKS.values())) {
            if (mark.marker().equals(player)) {
                endMark(mark, server, true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID(), player.server);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        emptyTeam(event.getServer());
    }

    /** Nothing carries into the next world (singleplayer and integrated servers reuse the JVM). */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        emptyTeam(event.getServer());
        MARKS.clear();
        SPOTS.clear();
    }
}
