package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.event.CourageEvents;
import dev.amman.proficiency.hearth.BoonType;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.TempHp;
import dev.amman.proficiency.hearth.WarmthMath;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.skill.SurvivalStreak;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player side of the Hearth, driven by the once-a-second player loop reading the in-range flag
 * the Hearth's tick writes (docs/HEARTH-SPEC.md, "Warmth", "Temp HP", "Setting out and coming home",
 * the boons table). Everything here ends within 2 s of leaving range, the Hearth breaking or
 * smouldering, or {@code hearth.enabled = false}: each second asks again.
 *
 * <ul>
 * <li><b>Warmth</b>: rises to full in the readiness time (Homecoming's Comfort-capped tier, plus any
 * {@link HearthEffects#addWarmthBonus} bonus), not within {@code combatSeconds} of damage; drains at
 * twice the rate away; 0 at a smouldering Hearth; full after a real night's sleep in range.</li>
 * <li><b>Hearty</b>: the exhaustion factor the watcher applies ({@link HearthEffectEvents}),
 * {@code 1 - value x Warmth}, or Provisioned's fading value away from home.</li>
 * <li><b>Comfort regen</b>: one heart per interval out of combat, scaled by Warmth; at full health,
 * full Warmth and out of combat it fills temp HP instead ({@link HearthTempHp}).</li>
 * <li><b>Fortified</b>: a mob's hit at home is cut by {@code value x Warmth} ({@link #incoming}).</li>
 * <li><b>Edges</b>: leaving a Hearth that still burns at full Warmth is a set-out: Provisioned
 * (Hearty carried out for the readiness time). The server decides every edge and sends it as a HUD
 * flag; the client plays the cue for it.</li>
 * <li><b>HUD</b>: the one sender of {@code hearth_hud}, on a change the client cannot predict.</li>
 * </ul>
 */
public final class PlayerEffects {

    /** Per-player state that is not worth saving: rebuilt within a second of a login. */
    static final class Live {
        /** Seconds left after a login in which a missing in-range flag is not a departure. */
        int grace;
        double regen;
        long riseStart = PlayerHearth.NEVER;
        long nightSleep = Long.MIN_VALUE;
        long homePos;
        String homeDim = "";
        double homeHearty;
        int homeReadiness;
        /** The exhaustion factor the watcher applies: 1 = no boon. */
        volatile double hunger = 1.0;
        HomeRules.Hud hud;
        /** The edge flag of the last payload that carried one (tests read it). */
        int lastEdge;
    }

    private static final Map<UUID, Live> LIVE = new ConcurrentHashMap<>();

    private PlayerEffects() {
    }

    static Live live(ServerPlayer player) {
        return LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
    }

    static void forget(UUID player) {
        LIVE.remove(player);
    }

    static void forgetAll() {
        LIVE.clear();
    }

    /**
     * A login: the Hearth has not seen the player yet, so the first seconds hold (no drain, no edge, no
     * HUD payload). Public for the GameTests, which log a mock player in by hand.
     */
    public static void loggedIn(ServerPlayer player) {
        Live live = new Live();
        live.grace = HomeRules.LOGIN_GRACE_SECONDS;
        LIVE.put(player.getUUID(), live);
    }

    /** Tests: ends the login grace at once (a mock player logs in when it is made). */
    public static void endGrace(ServerPlayer player) {
        live(player).grace = 0;
    }

    /** The exhaustion factor the watcher applies to this player now: 1 = no Hearty. */
    public static double hungerFactor(ServerPlayer player) {
        Live live = LIVE.get(player.getUUID());
        return live == null ? 1.0 : live.hunger;
    }

    // ---- Tiers ----------------------------------------------------------------------------------

    /** The effective tier of the pool giving {@code boon} at this Hearth; 0 when no pool gives it. */
    public static int tier(HearthState state, BoonType boon, HearthData data) {
        HearthPool pool = data.poolWithBoon(boon);
        return pool == null ? 0 : state.effectiveTier(pool, data);
    }

    /** The boon number of {@code boon} at a tier (seconds per heart for regen, fractions otherwise). */
    public static double value(BoonType boon, int tier, HearthData data) {
        HearthPool pool = data.poolWithBoon(boon);
        return pool == null ? 0.0 : HearthRules.boonValue(pool, tier, data);
    }

    // ---- The per-player second -------------------------------------------------------------------

    /**
     * {@code PLAYER_SECOND}: every online player, once a second, also with the Hearth switched off.
     * Public for the GameTests, which step it by hand.
     */
    public static void second(ServerPlayer player) {
        PlayerHearth data = HearthAttachments.of(player);
        Live live = live(player);
        boolean loginHold = live.grace > 0;
        if (loginHold) {
            live.grace--;
        }
        HearthData d = Hearths.data();
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        boolean enabled = ProficiencyConfig.hearthEnabled();
        boolean alive = player.isAlive() && !player.isSpectator();

        HearthBlockEntity hearth = enabled && alive ? HearthRange.homeEntity(player) : null;
        boolean smouldering = hearth != null && hearth.state().smouldering();
        boolean home = hearth != null && !smouldering;
        HearthState state = home ? hearth.state() : null;
        boolean combat = WarmthMath.inCombat(data.lastDamage(), now, d.combatSeconds());
        if (player.isSleeping() && !level.isDay()) {
            live.nightSleep = now;
        }

        int readiness = home ? tier(state, BoonType.READINESS, d) : 0;
        int hearty = home ? tier(state, BoonType.HUNGER_SLOW, d) : 0;
        int regen = home ? tier(state, BoonType.REGEN_OUT_OF_COMBAT, d) : 0;

        // Warmth. Right after a login the Hearth has not seen the player yet: hold, do not drain.
        float before = data.warmth();
        boolean grace = loginHold && !home && data.wasHome();
        double rise = WarmthMath.withSpeedBonus(HearthRules.warmthRiseSeconds(readiness, d),
                HearthEffects.warmthBonus(player));
        double drain = d.warmth().drainSeconds();
        if (smouldering) {
            data.setWarmth(0.0);
        } else if (!grace) {
            data.setWarmth(snap(WarmthMath.step(before, home, combat, 1.0, rise, drain)));
        }
        double warmth = data.warmth();
        warmthTelemetry(player, live, home, before, warmth, now);

        // The edge of home: Provisioned here, and the cue the client plays from the HUD payload.
        HomeRules.Edge edge = HomeRules.Edge.NONE;
        if (!grace) {
            edge = HomeRules.edge(data.wasHome(), home, WarmthMath.isFull(before),
                    !home && walkedAway(player, live, enabled));
            if (edge == HomeRules.Edge.SET_OUT) {
                setOut(data, live, now, d);
            }
            data.setWasHome(home);
        }
        if (home) {
            live.homePos = hearth.packedPos();
            live.homeDim = level.dimension().location().toString();
            live.homeHearty = value(BoonType.HUNGER_SLOW, hearty, d);
            live.homeReadiness = readiness;
        }

        // Hearty: the home value by Warmth, or Provisioned away from home.
        if (!enabled && data.provisionedStrength() > 0) {
            data.provision(0.0, PlayerHearth.NEVER, PlayerHearth.NEVER);
        }
        double provisioned = alive ? data.provisionedAt(now) : 0.0;
        if (!(provisioned > 0) && data.provisionedStrength() > 0 && now >= data.provisionedUntil()) {
            data.provision(0.0, PlayerHearth.NEVER, PlayerHearth.NEVER);
        }
        double heartyHome = home ? value(BoonType.HUNGER_SLOW, hearty, d) : 0.0;
        live.hunger = EffectsMath.exhaustionFactor(EffectsMath.hearty(heartyHome, warmth, provisioned));

        // Comfort regen, and temp HP at full health.
        regen(player, data, live, home && alive, combat, warmth, regen, d);
        HearthTempHp.tidy(player, data);

        if (home) {
            boonTelemetry(player, state, warmth, d);
        } else if (provisioned > 0 && data.provisionedStrength() > 0) {
            TelemetryService.hearth(player, Telemetry.HEARTH_BOON, BoonType.HUNGER_SLOW.id(), "provisioned",
                    provisioned / data.provisionedStrength());
        }

        double rate = smouldering ? 0.0 : WarmthMath.ratePerSecond(warmth, home, combat, rise, drain);
        if (!grace) {
            // While a login holds, the client hears nothing: no "away" flash before the Hearth has
            // seen a player who logged out at home.
            hud(player, data, live, home, hearth, smouldering, rate, edge, now);
        }
    }

    /**
     * Warmth is saved as a float, and sixty float steps of 1/60 can land a hair under full: within
     * {@link #SNAP} of either end it is the end, so a 60 s rise is full at 60 s, not 61.
     */
    private static double snap(double warmth) {
        if (warmth > 1.0 - SNAP) {
            return 1.0;
        }
        return warmth < SNAP ? 0.0 : warmth;
    }

    private static final double SNAP = 1e-4;

    /**
     * Whether a player who is no longer home walked (or travelled) away from a Hearth that still
     * burns. Not when the Hearth broke, smoulders or the switch is off (nobody walked), and not when
     * the player still stands inside its cylinder (the flag lapsed for another reason).
     */
    private static boolean walkedAway(ServerPlayer player, Live live, boolean enabled) {
        if (!enabled || live.homeDim.isEmpty() || !player.isAlive()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().toString().equals(live.homeDim)) {
            return true;
        }
        BlockPos pos = BlockPos.of(live.homePos);
        if (!level.isLoaded(pos)) {
            return true;
        }
        HearthBlockEntity hearth = HearthRange.entity(level, pos);
        if (hearth == null || hearth.state().smouldering()) {
            return false;
        }
        return !HearthRange.inRange(level, pos, player.position());
    }

    /**
     * Leaving at full Warmth: Hearty goes with you as Provisioned, fading over the readiness time
     * (Homecoming's Comfort-capped tier). With no Hearty there is nothing to carry.
     */
    private static void setOut(PlayerHearth data, Live live, long now, HearthData d) {
        double strength = live.homeHearty;
        if (!(strength > 0)) {
            return;
        }
        double minutes = HearthRules.provisionedMinutes(live.homeReadiness, d);
        data.provision(strength, now, WarmthMath.provisionedUntil(now, minutes));
    }

    private static void regen(ServerPlayer player, PlayerHearth data, Live live, boolean home, boolean combat,
            double warmth, int tier, HearthData d) {
        HearthPool pool = d.poolWithBoon(BoonType.REGEN_OUT_OF_COMBAT);
        if (!home || tier <= 0 || pool == null) {
            live.regen = 0.0;
            return;
        }
        HearthData.PoolSpec spec = d.pool(pool);
        live.regen = EffectsMath.regenProgress(live.regen, warmth, spec.value(tier), combat);
        if (!EffectsMath.heartDue(live.regen)) {
            return;
        }
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal((float) TempHp.STEP);
            live.regen = 0.0;
        } else if (WarmthMath.isFull(warmth) && HearthTempHp.step(player, data, tier, spec.absorptionPerTier())) {
            live.regen = 0.0;
        }
    }

    // ---- Telemetry ------------------------------------------------------------------------------

    /** {@code hearth_warm}: seconds from the start of a rise to full ("rise"), and bed fills ("bed"). */
    private static void warmthTelemetry(ServerPlayer player, Live live, boolean home, double before, double warmth,
            long now) {
        if (!home) {
            live.riseStart = PlayerHearth.NEVER;
            return;
        }
        if (WarmthMath.isFull(warmth)) {
            if (!WarmthMath.isFull(before) && live.riseStart != PlayerHearth.NEVER) {
                TelemetryService.hearth(player, Telemetry.HEARTH_WARM, "rise", "",
                        Math.max(1, now - live.riseStart) / 20.0);
            }
            live.riseStart = PlayerHearth.NEVER;
        } else if (live.riseStart == PlayerHearth.NEVER) {
            live.riseStart = now - 20;
        }
    }

    /** {@code hearth_boon}: Warmth-weighted seconds per boon (1 per second for the unscaled ones). */
    private static void boonTelemetry(ServerPlayer player, HearthState state, double warmth, HearthData d) {
        for (BoonType boon : BoonType.values()) {
            int tier = tier(state, boon, d);
            if (tier <= 0) {
                continue;
            }
            boolean scaled = boon == BoonType.HUNGER_SLOW || boon == BoonType.REGEN_OUT_OF_COMBAT
                    || boon == BoonType.MOB_DAMAGE_REDUCTION;
            TelemetryService.hearth(player, Telemetry.HEARTH_BOON, boon.id(), HearthRules.roman(tier),
                    scaled ? warmth : 1.0);
        }
    }

    // ---- The HUD --------------------------------------------------------------------------------

    /**
     * The one {@code hearth_hud} sender. The edge of this second rides on the payload as its flag
     * ({@link #edgeFlag}): the client plays the cue for that flag ("By the Hearth", "Left the
     * Hearth's warmth", "Set out from the Hearth"), so what the player sees and hears follows the
     * server's rules (a login, a Hearth that smoulders, breaks or is switched off under the player is
     * no edge) and is played once. The edge flag is not remembered in {@link Live#hud}: the next
     * second does not resend for it, and a resend never repeats it.
     */
    private static void hud(ServerPlayer player, PlayerHearth data, Live live, boolean home,
            @Nullable HearthBlockEntity hearth, boolean smouldering, double rate, HomeRules.Edge edge, long now) {
        int flags = 0;
        if (hearth != null && SurvivalStreak.enabled()) {
            flags |= HearthHudPayload.FLAG_STREAK_SAFE;
        }
        if (smouldering) {
            flags |= HearthHudPayload.FLAG_SMOULDERING;
        }
        if (HearthTempHp.active(player)) {
            flags |= HearthHudPayload.FLAG_TEMP_HP;
        }
        HomeRules.Hud next = new HomeRules.Hud(home, hearth == null ? 0L : hearth.packedPos(), data.warmth(),
                (float) rate, data.provisionedStrength(), data.provisionedFrom(), data.provisionedUntil(),
                (float) data.tales(), flags, now);
        int edgeFlag = edgeFlag(edge);
        boolean dirty = HearthNet.takeHudDirty(player);
        if (!dirty && edgeFlag == 0 && !HomeRules.needsSend(live.hud, next, now)) {
            return;
        }
        live.hud = next;
        HearthNet.sendHud(player, new HearthHudPayload(home, next.hearthPos(), next.warmth(), next.rate(),
                next.provisioned(), next.provisionedFrom(), next.provisionedUntil(), next.tales(), flags | edgeFlag));
        if (edgeFlag != 0) {
            live.lastEdge = edgeFlag;
        }
    }

    /** The HUD flag for an edge, 0 for none. */
    static int edgeFlag(HomeRules.Edge edge) {
        return switch (edge) {
            case ENTER -> HearthHudPayload.FLAG_EDGE_HOME;
            case LEAVE -> HearthHudPayload.FLAG_EDGE_LEFT;
            case SET_OUT -> HearthHudPayload.FLAG_EDGE_SET_OUT;
            case NONE -> 0;
        };
    }

    /** Tests: the edge flag of the last payload that carried one (0: none yet), and forgets it. */
    public static int takeLastEdge(ServerPlayer player) {
        Live live = LIVE.get(player.getUUID());
        if (live == null) {
            return 0;
        }
        int edge = live.lastEdge;
        live.lastEdge = 0;
        return edge;
    }

    /** The HUD payload the client was last sent, or null (tests). */
    @Nullable
    public static HomeRules.Hud lastHud(ServerPlayer player) {
        Live live = LIVE.get(player.getUUID());
        return live == null ? null : live.hud;
    }

    // ---- Hooks ----------------------------------------------------------------------------------

    /**
     * {@code SLEEP}: a real night's sleep in range of a lit Hearth fills Warmth at once (the hook
     * only fires after {@code sleepCounter} reached 100). A day nap a mod allows does nothing.
     */
    static void slept(ServerPlayer player) {
        Live live = live(player);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (!HomeRules.realSleep(player.isSleepingLongEnough(), !level.isDay(), live.nightSleep, now)
                || !HearthRange.isHome(player)) {
            return;
        }
        PlayerHearth data = HearthAttachments.of(player);
        if (!WarmthMath.isFull(data.warmth())) {
            TelemetryService.hearth(player, Telemetry.HEARTH_WARM, "bed", "", 1.0);
        }
        data.setWarmth(1.0);
        live.riseStart = PlayerHearth.NEVER;
        HearthNet.markHudDirty(player);
    }

    /**
     * {@code INCOMING_DAMAGE}: marks combat (no Warmth rise for {@code combatSeconds}) and applies
     * Fortified to a mob's hit at home.
     */
    static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getAmount() > 0)) {
            return;
        }
        PlayerHearth data = HearthAttachments.of(player);
        data.setLastDamage(player.level().getGameTime());
        if (!fortifies(event.getSource().getEntity())) {
            return;
        }
        HearthBlockEntity hearth = HearthRange.homeEntity(player);
        if (hearth == null || hearth.state().smouldering()) {
            return;
        }
        HearthData d = Hearths.data();
        int tier = tier(hearth.state(), BoonType.MOB_DAMAGE_REDUCTION, d);
        if (tier <= 0) {
            return;
        }
        double factor = EffectsMath.fortifiedFactor(value(BoonType.MOB_DAMAGE_REDUCTION, tier, d), data.warmth());
        if (factor < 1.0) {
            event.setAmount((float) (event.getAmount() * factor));
        }
    }

    /**
     * Whether Fortified cuts a hit from this attacker (abuse check 5): a mob, never a player and never
     * a tamed animal of a player (an owner who is offline still counts as a player). Falls, fire,
     * lava and the void have no attacker at all.
     */
    public static boolean fortifies(@Nullable Entity attacker) {
        if (!(attacker instanceof Mob) || attacker instanceof Player) {
            return false;
        }
        if (attacker instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            LivingEntity owner = owned.getOwner();
            return owner != null && !(owner instanceof Player);
        }
        return true;
    }

    /** {@code DEATH}: the respawned player starts clean (no temp HP, a fresh HUD, no Hearty). */
    static void died(ServerPlayer player, PlayerHearth data, double talesLost) {
        HearthTempHp.clear(player, data);
        forget(player.getUUID());
        HearthNet.markHudDirty(player);
    }

    /** Whether a hit soaked by temp HP away from home was a boss's (for {@code hearth_absorb}). */
    static boolean bossHit(@Nullable Entity attacker) {
        return attacker instanceof LivingEntity living && CourageEvents.isBoss(living);
    }
}
