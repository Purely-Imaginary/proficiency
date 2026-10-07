package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.ChargerMath;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.amman.proficiency.compat.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Charger: first in, always moving forward (idea 39). It owns the XP (first blood, charge hits,
 * Spearhead kills), the passive (a harder first blood), the first-blood shield, the charge's
 * lifesteal, Spearhead, Breach, Charge! and the tree's mechanics. The numbers are in
 * {@link ChargerMath}.
 *
 * <p>Courage pays when the odds are against you; Guardian pays for protecting someone else.
 * Charger pays for initiative: being the first to hit, closing the distance, and leading a group
 * from the front. Nothing here reads the odds or pays for a hit you take.
 *
 * <p>Anti-farm rules, all here:
 * <ul>
 * <li>Melee only: the hit must come from your own hand (a sword, an axe, a fist), not an arrow.</li>
 * <li>The target must be a threat: a hostile mob, or one that is after you. Never a player,
 * never someone's pet, never a cow.</li>
 * <li>First blood pays once per mob, ever (saved on the mob), and only on a fresh mob (90% of its
 * health or more) that no player hit in the last 10 s. A drop tower's softened mobs never pay.
 * A forced first blood (Charge!, Onslaught, reopenFirstBlood) gets the damage and the shield,
 * never XP of its own.</li>
 * <li>A charge counts only your own movement toward the target (a mob walking up to you is not
 * your charge), and a charge hit spends it: the next one needs another 5 blocks. One mob pays
 * at most 3 charge hits.</li>
 * <li>The spot rule: one spot (12 blocks) pays at most 16 Charger payouts in 5 minutes. A mob farm
 * brings the mobs to you; a charger goes to them.</li>
 * <li>Survival or adventure only.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class ChargerEvents {

    /** On a mob: the last time any player hurt it (melee or not). */
    private static final String HIT_AT = "proficiency_charger_hit_at";
    /** On a mob: first blood already paid XP. */
    public static final String FIRST_BLOOD_PAID = "proficiency_charger_first_paid";
    /** On a mob: how many charge hits it already paid for. */
    public static final String CHARGES_PAID = "proficiency_charger_charges_paid";
    /** On a mob: the last Charger player who hit it in melee, and when (Hammer and Anvil). */
    private static final String FOUGHT_BY = "proficiency_charger_fought_by";
    private static final String FOUGHT_AT = "proficiency_charger_fought_at";
    /** On a mob: until when a Charger's next hit counts as first blood (reopenFirstBlood). */
    private static final String REOPEN_UNTIL = "proficiency_charger_reopen_until";

    public static final String SOURCE_FIRST_BLOOD = "proficiency.xplog.source.charger_first_blood";
    public static final String SOURCE_CHARGE = "proficiency.xplog.source.charger_charge";
    public static final String SOURCE_SPEARHEAD = "proficiency.xplog.source.charger_spearhead";

    private static final ResourceLocation SPEARHEAD_KNOCKBACK = Proficiency.id("charger_spearhead_knockback");

    /** A mob counts as fighting a Charger this long after a Charger's melee hit (5 s). */
    public static final long FOUGHT_TICKS = 100L;

    /** Per player: where they have been, for the charge. */
    private static final Map<UUID, ChargerMath.Trail> TRAILS = new ConcurrentHashMap<>();
    /** Per player: the last charge hit, for lifesteal. */
    private static final Map<UUID, Long> LAST_CHARGE = new ConcurrentHashMap<>();
    /** Per player: the last first blood, for Onslaught. */
    private static final Map<UUID, Long> LAST_FIRST_BLOOD = new ConcurrentHashMap<>();
    /** Per player: the last first-blood shield. */
    private static final Map<UUID, Long> LAST_ABSORB = new ConcurrentHashMap<>();
    /** Per player: until when the next target is a first blood (Charge!, Onslaught). */
    private static final Map<UUID, Long> FORCED = new ConcurrentHashMap<>();
    /** Per player: recent payouts, for the spot rule. */
    private static final Map<UUID, ArrayDeque<Spot>> SPOTS = new ConcurrentHashMap<>();
    /** What an incoming hit decided, read when the hit lands. Keyed by the target. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /** Players who have Spearhead's knockback resistance on them now. */
    private static final Map<UUID, Boolean> BRACED = new ConcurrentHashMap<>();
    /** Other skills' boosts to a first blood's damage (Tactician's Hammer and Anvil). */
    private static final List<FirstBloodBoost> BOOSTS = new CopyOnWriteArrayList<>();

    /** Set while Breach rolls for a sprint attack, so ProcService can raise the chance. */
    private static final ThreadLocal<Boolean> SPRINT_ROLL = ThreadLocal.withInitial(() -> false);

    private record Spot(long tick, double x, double z) {
    }

    private record Pending(UUID player, long tick, boolean firstBlood, boolean natural, boolean forced,
            boolean reopened, double closed, boolean charge, boolean sprint) {
    }

    /** What Spearhead looks like for one player right now. */
    public record Spearhead(boolean active, @Nullable ServerPlayer guardian, boolean synergy) {
        static final Spearhead NONE = new Spearhead(false, null, false);
    }

    /**
     * A boost to a first blood's damage, from another skill. Returns a multiplier (1.0 for none).
     * Tactician's Hammer and Anvil registers one: a first blood on a Called Shot target hits
     * much harder.
     */
    @FunctionalInterface
    public interface FirstBloodBoost {
        double multiplier(ServerPlayer charger, LivingEntity target);
    }

    private ChargerEvents() {
    }

    // ---- The API other skills use (Tactician, idea 40) -----------------------------------------

    public static void addFirstBloodBoost(FirstBloodBoost boost) {
        BOOSTS.add(boost);
    }

    public static boolean removeFirstBloodBoost(FirstBloodBoost boost) {
        return BOOSTS.remove(boost);
    }

    /**
     * The Charger this mob is fighting, or null: a player with Charger hit it in melee in the
     * last 5 s, or it is after a player who is in Spearhead.
     */
    @Nullable
    public static ServerPlayer chargerFighting(LivingEntity mob) {
        MinecraftServer server = mob.getServer();
        if (server == null) {
            return null;
        }
        CompoundTag data = mob.getPersistentData();
        if (data.hasUUID(FOUGHT_BY)) {
            long at = data.getLong(FOUGHT_AT);
            long now = now(mob);
            ServerPlayer by = server.getPlayerList().getPlayer(data.getUUID(FOUGHT_BY));
            if (by != null && by.isAlive() && now >= at && now - at <= FOUGHT_TICKS) {
                return by;
            }
        }
        if (mob instanceof Mob hunter && hunter.getTarget() instanceof ServerPlayer target
                && spearhead(target).active()) {
            return target;
        }
        return null;
    }

    /**
     * The next Charger melee hit on this mob in the next 8 s counts as first blood (the damage
     * and the shield), even if someone hit it. It pays no XP of its own: first blood XP needs a
     * fresh, quiet mob, once per mob, so this is no farm.
     */
    public static void reopenFirstBlood(LivingEntity mob) {
        mob.getPersistentData().putLong(REOPEN_UNTIL, now(mob) + 160L);
    }

    /** Whether this player is in Spearhead now. */
    public static boolean inSpearhead(ServerPlayer player) {
        return spearhead(player).active();
    }

    /** For ProcService: Breach is rolling for a sprint attack. */
    public static boolean sprintRoll() {
        return SPRINT_ROLL.get();
    }

    // ---- Who counts ----------------------------------------------------------------------------

    private static boolean owned(@Nullable Entity entity) {
        return entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null;
    }

    /**
     * A target worth a charge: a hostile mob, or one after you. Never a player or a pet. Not an
     * alive check: a kill reads the mob as it was at the blow.
     */
    public static boolean isThreat(LivingEntity target, ServerPlayer player) {
        if (target instanceof Player || owned(target)) {
            return false;
        }
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == player);
    }

    /** A melee hit: from the player's own hand, not an arrow or a trident they threw. */
    public static boolean melee(DamageSource source, ServerPlayer player) {
        return source.getEntity() == player && source.getDirectEntity() == player;
    }

    /** Another player: alive, not a spectator, not you, in your world. */
    private static boolean isFriend(ServerPlayer player, @Nullable Entity other) {
        return other instanceof ServerPlayer friend && friend != player && friend.isAlive()
                && !friend.isSpectator() && friend.level() == player.level();
    }

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself: the GameTest mock claims creative.
        return player.gameMode.isSurvival() && ProficiencyConfig.enabled(Skill.CHARGER);
    }

    private static int level(ServerPlayer player) {
        return SkillService.level(player, Skill.CHARGER);
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    private static double worth(LivingEntity mob) {
        return ChargerMath.worth(mob.getMaxHealth(), CourageEvents.isBoss(mob));
    }

    // ---- Spearhead -----------------------------------------------------------------------------

    /**
     * Spearhead: a friend within 16 blocks behind you (24 with Spearpoint), relative to where you
     * face, and a hostile mob within 16 blocks ahead. Also says whether a Guardian (level 10 or
     * more) is among the friends behind, and whether one of you has Shield and Spear.
     */
    public static Spearhead spearhead(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive()) {
            return Spearhead.NONE;
        }
        Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
        boolean spearpoint = TalentService.rank(player, Skill.CHARGER, "spearhead_wide") > 0;
        double reach = spearpoint ? ChargerMath.SPEARPOINT_RADIUS : ChargerMath.SPEARHEAD_RADIUS;
        double range = reach * reach;
        boolean friendBehind = false;
        ServerPlayer guardian = null;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (!isFriend(player, other) || other.distanceToSqr(player) > range
                    || !ChargerMath.behind(facing.x, facing.z, other.getX() - player.getX(), other.getZ() - player.getZ())) {
                continue;
            }
            friendBehind = true;
            if (guardian == null && SkillService.level(other, Skill.GUARDIAN) >= ChargerMath.GUARDIAN_LEVEL) {
                guardian = other;
            }
        }
        if (!friendBehind) {
            return Spearhead.NONE;
        }
        double ahead = ChargerMath.SPEARHEAD_RADIUS * ChargerMath.SPEARHEAD_RADIUS;
        boolean mobAhead = !player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(ChargerMath.SPEARHEAD_RADIUS),
                mob -> mob.isAlive() && mob instanceof Enemy && !owned(mob)
                        && mob.distanceToSqr(player) <= ahead
                        && ChargerMath.ahead(facing.x, facing.z, mob.getX() - player.getX(), mob.getZ() - player.getZ()))
                .isEmpty();
        if (!mobAhead) {
            return Spearhead.NONE;
        }
        boolean synergy = guardian != null && (TalentService.hasSynergy(player, "shield_and_spear")
                || TalentService.hasSynergy(guardian, "shield_and_spear"));
        return new Spearhead(true, guardian, synergy);
    }

    /** Spearhead's damage bonus for this player now, or 0 (not in Spearhead, or under level 10). */
    public static double spearheadBonus(ServerPlayer player, Spearhead state) {
        if (!state.active() || level(player) < ChargerMath.SPEARHEAD_LEVEL) {
            return 0.0;
        }
        boolean spearpoint = TalentService.rank(player, Skill.CHARGER, "spearhead_wide") > 0;
        return ChargerMath.spearheadBonus(spearpoint, state.guardian() != null, state.synergy());
    }

    // ---- The trail and Spearhead's knockback resistance ----------------------------------------

    /** The trail for this player, made on first use. */
    public static ChargerMath.Trail trail(ServerPlayer player) {
        return TRAILS.computeIfAbsent(player.getUUID(), id -> new ChargerMath.Trail());
    }

    /** Every 2 ticks: where each player is. Every half second: Spearhead's knockback resistance. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % ChargerMath.TRAIL_STEP_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSpectator() && player.isAlive()) {
                    trail(player).add(now(player), player.getX(), player.getY(), player.getZ());
                }
            }
        }
        if (tick % 10 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                brace(player);
            }
        }
        if (tick % 20 == 0 && !PENDING.isEmpty()) {
            dropStalePending(server.overworld().getGameTime());
        }
    }

    /**
     * A hit that never landed (blocked, cancelled, a mob in its hurt time) leaves its entry behind,
     * and a mob that despawns never comes back for it. Every entry is read the tick it is made,
     * so anything older than one tick is dead weight. Returns how many were dropped (for tests).
     */
    public static int dropStalePending(long now) {
        int dropped = 0;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            long at = it.next().getValue().tick();
            if (at > now || now - at > 1) {
                it.remove();
                dropped++;
            }
        }
        return dropped;
    }

    /** How many hits wait to land now. For GameTests. */
    public static int pendingCount() {
        return PENDING.size();
    }

    /** Puts on or takes off Spearhead's knockback resistance. Public so a GameTest can run it. */
    public static void brace(ServerPlayer player) {
        AttributeInstance resist = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resist == null) {
            return;
        }
        Spearhead state = level(player) >= ChargerMath.SPEARHEAD_LEVEL ? spearhead(player) : Spearhead.NONE;
        if (state.active()) {
            dev.amman.proficiency.compat.Attr.setTransient(resist, dev.amman.proficiency.compat.Attr.mod(SPEARHEAD_KNOCKBACK,
                    ChargerMath.spearheadKnockback(state.guardian() != null), AttributeModifier.Operation.ADDITION));
            BRACED.put(player.getUUID(), true);
        } else if (BRACED.remove(player.getUUID()) != null) {
            resist.removeModifier(dev.amman.proficiency.compat.Attr.uuid(SPEARHEAD_KNOCKBACK));
        }
    }

    // ---- The hit: decide on the way in, pay when it lands --------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (target instanceof ServerPlayer victim) {
            trustTheLine(victim, event);
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || !melee(source, player)
                || !isThreat(target, player)) {
            return;
        }
        long now = now(player);
        CompoundTag data = target.getPersistentData();
        boolean forced = FORCED.getOrDefault(player.getUUID(), -1L) >= now;
        boolean reopened = data.contains(REOPEN_UNTIL) && data.getLong(REOPEN_UNTIL) >= now;
        boolean natural = ChargerMath.firstBlood(data.contains(HIT_AT) ? data.getLong(HIT_AT) : -1L, now,
                target.getHealth(), target.getMaxHealth());
        boolean firstBlood = forced || reopened || natural;

        ChargerMath.Trail trail = trail(player);
        double closed = trail.closed(now, player.getX(), player.getY(), player.getZ(),
                target.getX(), target.getY(), target.getZ());
        boolean charge = ChargerMath.isCharge(closed, TalentService.rank(player, Skill.CHARGER, "charge_short"));

        double multiplier = 1.0;
        if (firstBlood) {
            multiplier *= ChargerMath.firstBloodMultiplier(SkillService.bonus(player, Skill.CHARGER), boosts(player, target));
        }
        // Level first: the Spearhead scan is not free and does nothing under level 10.
        double spear = level(player) >= ChargerMath.SPEARHEAD_LEVEL ? spearheadBonus(player, spearhead(player)) : 0.0;
        if (spear > 0) {
            multiplier *= 1.0 + spear;
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
        PENDING.put(target.getUUID(), new Pending(player.getUUID(), now, firstBlood, natural, forced, reopened,
                closed, charge, player.isSprinting()));
    }

    private static double boosts(ServerPlayer player, LivingEntity target) {
        double total = 1.0;
        for (FirstBloodBoost boost : BOOSTS) {
            double m = boost.multiplier(player, target);
            if (m > 0 && Double.isFinite(m)) {
                total *= m;
            }
        }
        return total;
    }

    /** Post: the hit landed. Pays first blood and the charge, and runs the sustain and Breach. */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || target instanceof Player) {
            return;
        }
        Entity culprit = event.getSource().getEntity();
        if (!(culprit instanceof ServerPlayer player)) {
            return;
        }
        long now = now(target);
        Pending pending = PENDING.remove(target.getUUID());
        CompoundTag data = target.getPersistentData();
        boolean landed = event.getNewDamage() > 0;
        if (landed && pending != null && pending.player().equals(player.getUUID()) && pending.tick() == now
                && melee(event.getSource(), player)) {
            if (level(player) > 0) {
                data.putUUID(FOUGHT_BY, player.getUUID());
                data.putLong(FOUGHT_AT, now);
            }
            if (pending.forced()) {
                FORCED.remove(player.getUUID());
            }
            if (pending.reopened()) {
                data.remove(REOPEN_UNTIL);
            }
            if (pending.firstBlood()) {
                firstBlood(player, target, pending.natural());
            }
            if (pending.charge()) {
                charged(player, target, pending.closed(), pending.sprint());
            }
            lifesteal(player, event.getNewDamage());
        }
        // Any player's hit (an arrow too) ends the quiet a first blood needs.
        if (landed) {
            data.putLong(HIT_AT, now);
        }
    }

    // ---- XP source 1: first blood --------------------------------------------------------------

    /**
     * A first blood landed. A forced one (Charge!, Onslaught, a Tactician's mark) gets the damage
     * and the shield, but only a natural one (a fresh mob no player hit in 10 s) pays XP and
     * shortens Charge! with Warbringer. So Charge! at a drop tower pays nothing for softened mobs.
     */
    private static void firstBlood(ServerPlayer player, LivingEntity target, boolean natural) {
        long now = now(player);
        LAST_FIRST_BLOOD.put(player.getUUID(), now);
        boolean claimed = false;
        if (natural) {
            CompoundTag data = target.getPersistentData();
            boolean open = !data.getBoolean(FIRST_BLOOD_PAID);
            payFirstBlood(player, target);
            // Warbringer only when this mob's one first blood was counted just now (not a spot-rule
            // refusal, not a mob that already paid). Not tied to the XP amount: XP is 0 at level 100.
            claimed = open && data.getBoolean(FIRST_BLOOD_PAID);
        }
        shield(player);
        if (claimed && TalentService.rank(player, Skill.CHARGER, "warbringer") > 0) {
            ProficiencyAttachments.of(player).shortenCooldown(Skill.CHARGER,
                    ChargerMath.WARBRINGER_COOLDOWN_TICKS, now);
        }
    }

    /** Pays a first blood, once per mob. Public so a GameTest can drive it. Returns the XP. */
    public static float payFirstBlood(ServerPlayer player, LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        if (!counts(player) || data.getBoolean(FIRST_BLOOD_PAID) || !spotAllows(player, target)) {
            return 0f;
        }
        double xp = ChargerMath.firstBloodXp(ProficiencyConfig.chargerFirstBloodXp(), worth(target));
        if (!(xp > 0)) {
            return 0f;
        }
        data.putBoolean(FIRST_BLOOD_PAID, true);
        noteSpot(player, target);
        return SkillService.grant(player, Skill.CHARGER, xp, SOURCE_FIRST_BLOOD);
    }

    /**
     * First blood's shield: Absorption I for 8 s (Crash In: longer, II at rank 3), at most once in
     * 10 s, from level 25. With a Guardian behind you in Spearhead (Shield and Spear), they get it too.
     */
    public static boolean shield(ServerPlayer player) {
        long now = now(player);
        if (level(player) < ChargerMath.SUSTAIN_LEVEL
                || !ChargerMath.absorbReady(LAST_ABSORB.getOrDefault(player.getUUID(), -1L), now)) {
            return false;
        }
        LAST_ABSORB.put(player.getUUID(), now);
        int rank = TalentService.rank(player, Skill.CHARGER, "crash_absorb");
        MobEffectInstance absorb = new MobEffectInstance(MobEffects.ABSORPTION,
                ChargerMath.absorbTicks(rank), ChargerMath.absorbAmplifier(rank), false, false, true);
        player.addEffect(absorb);
        Spearhead state = spearhead(player);
        if (state.active() && state.guardian() != null) {
            state.guardian().addEffect(new MobEffectInstance(absorb), player);
        }
        return true;
    }

    // ---- XP source 2: the charge hit -----------------------------------------------------------

    private static void charged(ServerPlayer player, LivingEntity target, double closed, boolean sprint) {
        long now = now(player);
        LAST_CHARGE.put(player.getUUID(), now);
        trail(player).clear();
        payCharge(player, target, closed, sprint);
        SPRINT_ROLL.set(sprint);
        boolean breach;
        try {
            breach = ProcService.fire(player, Skill.CHARGER, target);
        } finally {
            SPRINT_ROLL.set(false);
        }
        if (breach) {
            breach(player);
        }
    }

    /** Pays a charge hit: per block closed, x1.5 sprinting, at most 3 per mob. Returns the XP. */
    public static float payCharge(ServerPlayer player, LivingEntity target, double closed, boolean sprint) {
        CompoundTag data = target.getPersistentData();
        int paid = data.getInt(CHARGES_PAID);
        if (!counts(player) || paid >= ChargerMath.CHARGE_PAYS_PER_MOB || !spotAllows(player, target)) {
            return 0f;
        }
        double xp = ChargerMath.chargeXp(closed, sprint, ProficiencyConfig.chargerChargeXp());
        if (!(xp > 0)) {
            return 0f;
        }
        data.putInt(CHARGES_PAID, paid + 1);
        noteSpot(player, target);
        return SkillService.grant(player, Skill.CHARGER, xp, SOURCE_CHARGE);
    }

    /** Lifesteal, only in the 5 s after a charge hit, from level 25. Returns the health given. */
    public static float lifesteal(ServerPlayer player, float damage) {
        if (level(player) < ChargerMath.SUSTAIN_LEVEL
                || !ChargerMath.lifestealOpen(LAST_CHARGE.getOrDefault(player.getUUID(), -1L), now(player))) {
            return 0f;
        }
        float heal = ChargerMath.lifesteal(damage, TalentService.rank(player, Skill.CHARGER, "charge_lifesteal"));
        if (heal > 0 && player.getHealth() < player.getMaxHealth()) {
            player.heal(heal);
            return heal;
        }
        return 0f;
    }

    /** Breach: hostile mobs in a 4-block cone ahead are pushed back and staggered (Slowness II). */
    public static int breach(ServerPlayer player) {
        Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
        double power = ProcService.power(player, Skill.CHARGER);
        double push = ChargerMath.breachKnockback(power);
        int stagger = ChargerMath.breachStaggerTicks(power);
        int hit = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(ChargerMath.BREACH_RANGE))) {
            if (!mob.isAlive() || !isThreat(mob, player)
                    || !ChargerMath.inCone(facing.x, facing.z, mob.getX() - player.getX(), mob.getZ() - player.getZ())) {
                continue;
            }
            mob.knockback(push, -facing.x, -facing.z);
            mob.hurtMarked = true;
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stagger, 1), player);
            hit++;
        }
        if (hit > 0) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.7f, 0.8f);
        }
        return hit;
    }

    // ---- XP source 3: Spearhead kills, and Onslaught -------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (victim instanceof ServerPlayer dead) {
            forgetFight(dead.getUUID());
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !melee(event.getSource(), player)
                || !isThreat(victim, player)) {
            return;
        }
        long now = now(player);
        if (TalentService.rank(player, Skill.CHARGER, "chain_first_blood") > 0) {
            long last = LAST_FIRST_BLOOD.getOrDefault(player.getUUID(), -1L);
            if (last >= 0 && now >= last && now - last <= ChargerMath.ONSLAUGHT_TICKS) {
                FORCED.put(player.getUUID(), now + ChargerMath.FORCED_FIRST_BLOOD_TICKS);
            }
        }
        if (counts(player) && spearhead(player).active()) {
            paySpearheadKill(player, victim);
        }
    }

    /** Pays a kill in Spearhead. Public so a GameTest can drive it. Returns the XP. */
    public static float paySpearheadKill(ServerPlayer player, LivingEntity victim) {
        if (!counts(player) || !spotAllows(player, victim)) {
            return 0f;
        }
        double xp = ChargerMath.spearheadKillXp(ProficiencyConfig.chargerSpearheadKillXp(), worth(victim));
        if (!(xp > 0)) {
            return 0f;
        }
        noteSpot(player, victim);
        return SkillService.grant(player, Skill.CHARGER, xp, SOURCE_SPEARHEAD);
    }

    // ---- The spot rule -------------------------------------------------------------------------

    private static boolean spotAllows(ServerPlayer player, LivingEntity at) {
        return ChargerMath.spotAllows(recentNear(player, at.getX(), at.getZ(), now(player)));
    }

    /** How many Charger payouts this player got within 12 blocks of here in the last 5 minutes. */
    public static int recentNear(ServerPlayer player, double x, double z, long now) {
        ArrayDeque<Spot> spots = SPOTS.get(player.getUUID());
        if (spots == null) {
            return 0;
        }
        synchronized (spots) {
            spots.removeIf(spot -> !ChargerMath.inSpotWindow(spot.tick(), now));
            double range = ChargerMath.SPOT_RADIUS * ChargerMath.SPOT_RADIUS;
            int near = 0;
            for (Spot spot : spots) {
                double dx = spot.x() - x;
                double dz = spot.z() - z;
                if (dx * dx + dz * dz <= range) {
                    near++;
                }
            }
            return near;
        }
    }

    private static void noteSpot(ServerPlayer player, LivingEntity at) {
        ArrayDeque<Spot> spots = SPOTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (spots) {
            spots.add(new Spot(now(player), at.getX(), at.getZ()));
            // Bounded: far more than the limit is never needed to answer "16 near here?".
            while (spots.size() > 256) {
                spots.poll();
            }
        }
    }

    // ---- Trust the Line ------------------------------------------------------------------------

    /** Trust the Line: in Spearhead, a friend's hit (hand, arrow or pet) on you is cut 50-80%. */
    private static void trustTheLine(ServerPlayer victim, LivingIncomingDamageEvent event) {
        int rank = TalentService.rank(victim, Skill.CHARGER, "trust_line");
        if (rank <= 0 || !fromFriend(victim, event.getSource())) {
            return;
        }
        double multiplier = ChargerMath.trustMultiplier(rank, spearhead(victim).active());
        if (multiplier < 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /** Damage another player caused: by hand, by arrow, or by their pet. */
    public static boolean fromFriend(ServerPlayer victim, DamageSource source) {
        Entity culprit = source.getEntity();
        if (culprit instanceof Player other) {
            return other != victim;
        }
        if (culprit instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            return !pet.getOwnerUUID().equals(victim.getUUID());
        }
        return false;
    }

    // ---- Charge! -------------------------------------------------------------------------------

    /**
     * Charge!, right after ActiveService started it: a forward dash, the next target counts as
     * first blood, and friends behind you get Speed I for 5 s (the war cry). Knockback immunity
     * runs as long as the ability does (onKnockback).
     */
    public static void chargeOut(ServerPlayer player) {
        long now = now(player);
        Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
        double speed = ChargerMath.dashSpeed(TalentService.rank(player, Skill.CHARGER, "warbringer") > 0);
        player.setDeltaMovement(facing.x * speed, Math.max(player.getDeltaMovement().y, 0.2), facing.z * speed);
        player.hurtMarked = true;
        FORCED.put(player.getUUID(), now + ChargerMath.FORCED_FIRST_BLOOD_TICKS);
        warCry(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** The war cry: every friend within 16 blocks behind you gets Speed I for 5 s. Returns how many. */
    public static int warCry(ServerPlayer player) {
        Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
        double range = ChargerMath.SPEARHEAD_RADIUS * ChargerMath.SPEARHEAD_RADIUS;
        int told = 0;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (isFriend(player, other) && other.distanceToSqr(player) <= range
                    && ChargerMath.behind(facing.x, facing.z, other.getX() - player.getX(), other.getZ() - player.getZ())) {
                other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ChargerMath.WAR_CRY_TICKS, 0,
                        false, false, true), player);
                told++;
            }
        }
        return told;
    }

    /** Whether the next target is a first blood for this player (Charge!, Onslaught). For GameTests. */
    public static boolean nextIsFirstBlood(ServerPlayer player) {
        return FORCED.getOrDefault(player.getUUID(), -1L) >= now(player);
    }

    /** Charge!: nothing shoves you while it runs. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ActiveService.isFrenzied(player, Skill.CHARGER)) {
            event.setCanceled(true);
        }
    }

    // ---- Forgetting ----------------------------------------------------------------------------

    private static void forgetFight(UUID player) {
        TRAILS.remove(player);
        LAST_CHARGE.remove(player);
        LAST_FIRST_BLOOD.remove(player);
        FORCED.remove(player);
    }

    /** Drops everything kept for this player. On logout, and for GameTests. */
    public static void forget(UUID player) {
        forgetFight(player);
        LAST_ABSORB.remove(player);
        SPOTS.remove(player);
        BRACED.remove(player);
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().player().equals(player)) {
                it.remove();
            }
        }
    }

    /** Nothing carries into the next world (singleplayer and integrated servers reuse the JVM). */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        TRAILS.clear();
        LAST_CHARGE.clear();
        LAST_FIRST_BLOOD.clear();
        LAST_ABSORB.clear();
        FORCED.clear();
        SPOTS.clear();
        PENDING.clear();
        BRACED.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeInstance resist = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (resist != null) {
                resist.removeModifier(dev.amman.proficiency.compat.Attr.uuid(SPEARHEAD_KNOCKBACK));
            }
        }
        forget(event.getEntity().getUUID());
    }
}
