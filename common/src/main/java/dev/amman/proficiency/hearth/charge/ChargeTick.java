package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.HoldQueue;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The charge lane's part of a Hearth's once-a-second tick: lazy decay (only non-AFK survival or
 * adventure presence holds it off), the Masonry and Decorating holds that came due (not while it
 * smoulders), the tier watch, and the tracker rows for whoever is in range (at most every 10 s, and
 * only after a change). The block entity is marked changed when charge moved, and for clocks alone
 * at most once a minute.
 */
public final class ChargeTick {

    /** Tracker rows go out at most this often per Hearth, and only after a change. */
    static final long TRACKER_EVERY_TICKS = 200L;

    /** A hold whose block sits in an unloaded chunk is looked at again this much later. */
    static final long HOLD_RETRY_TICKS = 200L;

    /**
     * Decay clocks that moved with no charge changing (someone home, an empty Hearth) are saved at
     * most this often: after a crash, at most a minute at home counts as time away.
     */
    static final long CLOCK_SAVE_TICKS = 1200L;

    /** What one loaded Hearth needs between seconds and never saves. */
    static final class Live {
        boolean started;
        boolean trackerDirty;
        long trackerSent = Long.MIN_VALUE;
        long clocksSaved = Long.MIN_VALUE;
        /** The charges before this second's decay, reused every second. */
        final double[] before = new double[HearthPool.COUNT];
    }

    /** By block entity: a new object (a chunk loaded again) starts fresh, which is what forces the catch-up. */
    private static final Map<HearthBlockEntity, Live> LIVE = new WeakHashMap<>();

    private ChargeTick() {
    }

    static synchronized Live live(HearthBlockEntity hearth) {
        return LIVE.computeIfAbsent(hearth, h -> new Live());
    }

    /** Something the tracker shows changed at this Hearth; rows go out with its next tick. */
    static void markTrackerDirty(HearthBlockEntity hearth) {
        live(hearth).trackerDirty = true;
    }

    /** The {@code HEARTH_TICK} listener. */
    static void onSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        long now = level.getGameTime();
        HearthState state = hearth.state();
        HearthData data = Hearths.data();
        Live live = live(hearth);
        boolean first = !live.started;
        live.started = true;

        for (HearthPool pool : HearthPool.VALUES) {
            live.before[pool.ordinal()] = state.charge(pool);
        }
        long visited = state.lastVisited();
        long decayed = state.lastDecay();
        if (ChargeRules.decayStep(state, now, someoneHome(level, pos, now), first, data)) {
            hearth.setChanged();
            live.clocksSaved = now;
            if (movedVisibly(live.before, state)) {
                live.trackerDirty = true;
            }
        } else if ((state.lastVisited() != visited || state.lastDecay() != decayed)
                && (live.clocksSaved == Long.MIN_VALUE || now < live.clocksSaved
                        || now - live.clocksSaved >= CLOCK_SAVE_TICKS)) {
            // Only the clocks moved: worth a save now and then, not every second.
            hearth.setChanged();
            live.clocksSaved = now;
        }
        settleHolds(level, hearth, now);
        ChargeTiers.check(level, hearth);
        flushTracker(level, pos, hearth, live, now);
    }

    /**
     * Someone holds decay off: an online, living, non-AFK player in survival or adventure, in range.
     * AFK is the survival streak's own notion (no XP in its active window), so an alt parked by the
     * fire, a spectator or a creative builder does not count.
     */
    public static boolean someoneHome(ServerLevel level, BlockPos pos, long now) {
        long window = ProficiencyConfig.streakActiveWindowTicks();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.gameMode.isSurvival()
                    && !SkillTools.isFakePlayer(player) && HearthRange.inRange(level, pos, player.position())
                    && ProficiencyAttachments.of(player).isActive(now, window)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Credits every hold due by {@code now} whose block still stands; drops the rest. A hold whose
     * block is in an unloaded chunk waits. While the Hearth smoulders nothing settles: the holds wait,
     * as a grant at a smouldering Hearth feeds nothing, and pay after the relight if their blocks
     * still stand. Returns how much charge went in. Public for the GameTests, which cannot wait ten
     * minutes.
     */
    public static double settleHolds(ServerLevel level, HearthBlockEntity hearth, long now) {
        HoldQueue holds = hearth.state().holds();
        if (holds.isEmpty() || now < holds.nextDue() || hearth.state().smouldering()) {
            return 0.0;
        }
        List<HoldQueue.Due> due = holds.takeDue(now);
        if (due.isEmpty()) {
            return 0.0;
        }
        double credited = 0.0;
        BlockPos pos = hearth.getBlockPos();
        for (HoldQueue.Due entry : due) {
            HoldQueue.Held held = entry.held();
            BlockPos at = BlockPos.of(entry.pos());
            if (!level.isLoaded(at)) {
                holds.add(entry.pos(), new HoldQueue.Held(held.player(), held.name(), held.pool(), held.source(),
                        held.block(), held.amount(), now + HOLD_RETRY_TICKS));
                continue;
            }
            if (!ChargeRules.stillStands(held.block(), blockId(level.getBlockState(at)))) {
                continue;
            }
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(held.player());
            boolean dots = online != null && pos.equals(HearthRange.homeOf(online));
            credited += HearthCharge.credit(level, hearth, held.player(), held.name(), held.pool(), held.source(),
                    held.amount(), true, dots ? online : null, null);
        }
        hearth.setChanged();
        return credited;
    }

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    static void flushTracker(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, Live live, long now) {
        if (!live.trackerDirty || live.trackerSent != Long.MIN_VALUE && now - live.trackerSent < TRACKER_EVERY_TICKS) {
            return;
        }
        live.trackerDirty = false;
        live.trackerSent = now;
        List<ServerPlayer> near = ChargeTiers.playersInRange(level, pos);
        if (near.isEmpty()) {
            return;
        }
        HearthTrackerPayload payload = new HearthTrackerPayload(hearth.packedPos(),
                PoolRow.of(hearth.state(), Hearths.data()));
        for (ServerPlayer player : near) {
            HearthNet.sendTracker(player, payload);
        }
    }

    /** Decay worth redrawing a bar for: half a point of charge in any pool. */
    private static boolean movedVisibly(double[] before, HearthState state) {
        for (HearthPool pool : HearthPool.VALUES) {
            if (Math.abs(before[pool.ordinal()] - state.charge(pool)) >= 0.5) {
                return true;
            }
        }
        return false;
    }
}
