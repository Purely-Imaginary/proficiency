package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthPos;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.stages.HearthStructure.Check;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps a Hearth's built stage true to what stands (docs/HEARTH-SPEC.md, "Breaking a layer": the
 * ceiling drops, charge is held, never cut, and offerings are not owed again).
 *
 * <ul>
 * <li>A block placed or broken in a Hearth's 9x9x7 box marks it dirty; the next Hearth second
 * re-checks it if the last check is 30 s old ({@link #RECHECK_TICKS}). Every 2 minutes it re-checks
 * anyway, for what no event reports (explosions, pistons, editors). A Hearth that was not lit here
 * (a command, an editor, a test) is adopted in its first second: its stage counts as paid and its
 * build is first read at those same times.</li>
 * <li>The built stage is {@code min(what stands, highest offering paid)}; the ceiling is that,
 * capped by Character ({@link HearthState#ceiling}). The blockstate's {@code stage} shows the ceiling
 * (1 at least): a rise and a fallen layer within a second, a Character drop only once it has held
 * for {@link #FALL_DELAY_TICKS}, so Character hovering at a threshold never flickers the Hearth.</li>
 * <li>A change of the shown ceiling is told to everyone in range, with why it fell, at most once per
 * {@link #TELL_GAP_TICKS} per Hearth: changes inside that gap fold into one line once it ends, and
 * nothing is said if the ceiling came back to where the last line left it. A layer that now stands
 * and only lacks its offering is told once ("The Hall stands. Offer a gold block to seal it.").</li>
 * </ul>
 *
 * The lane's own fields live in the block entity's {@code laneData("stages")}.
 */
public final class HearthStageCheck {

    /** At most one check per 30 s after a block change in the box. */
    public static final long RECHECK_TICKS = 600;
    /** A check every 2 minutes whatever happened. */
    public static final long PERIODIC_TICKS = 2400;
    /** A Character drop shows (blockstate, line) only once it has held this long: 5 s. */
    public static final long FALL_DELAY_TICKS = 100;
    /** At most one fall or rise line per Hearth in this long: 30 s. */
    public static final long TELL_GAP_TICKS = 600;

    static final String LANE = "stages";
    static final String LAST_CHECK = "lastCheck";
    static final String DIRTY = "dirty";
    /** The ceiling the blockstate shows (and the stage lines were about). */
    static final String CEILING = "ceiling";
    /** The ceiling the last fall or rise line named, and when it was said. */
    static final String TOLD = "told";
    static final String TOLD_AT = "toldAt";
    /** When a Character drop below the shown ceiling began (absent: none pending). */
    static final String LOW_SINCE = "lowSince";
    static final String READY = "ready";
    static final String SEALERS = "sealers";

    private HearthStageCheck() {
    }

    static CompoundTag lane(HearthBlockEntity hearth) {
        return hearth.laneData(LANE);
    }

    /** The once-a-second part: a re-check when one is due, then the blockstate and the announcements. */
    static void tick(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        CompoundTag lane = lane(hearth);
        long now = level.getGameTime();
        if (!lane.contains(LAST_CHECK)) {
            adopt(hearth, lane, now);
            sync(level, pos, hearth, null);
            return;
        }
        long last = lane.getLong(LAST_CHECK);
        boolean due = now < last
                || lane.getBoolean(DIRTY) && now - last >= RECHECK_TICKS
                || now - last >= PERIODIC_TICKS;
        Check check = due ? recheck(level, pos, hearth) : null;
        sync(level, pos, hearth, check);
    }

    /**
     * A Hearth this lane never checked: one placed by a command, an editor or a test rather than lit
     * here (lighting stamps the first check). Its stage counts as paid, and its build is first read at
     * the usual times (the 2-minute check, or 30 s after a block in its box changes), not in its first
     * second, so a Hearth set up by hand does not fall to nothing before anyone can look at it.
     */
    private static void adopt(HearthBlockEntity hearth, CompoundTag lane, long now) {
        HearthState state = hearth.state();
        if (state.stage() > state.highestOffering()) {
            state.setHighestOffering(state.stage());
        }
        markChecked(lane, now);
        hearth.setChanged();
    }

    /** The build was just read (or taken as read): the next check counts from {@code now}. */
    static void markChecked(CompoundTag lane, long now) {
        lane.putLong(LAST_CHECK, now);
        lane.putBoolean(DIRTY, false);
    }

    /**
     * Reads the build now and stores the built stage ({@code min(what stands, highest offering)}).
     * Returns the check. The ceiling and blockstate follow in {@link #sync}.
     */
    public static Check recheck(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        return recheck(level, pos, hearth, StageBlocks.probe(level, pos, Hearths.data()));
    }

    /** {@link #recheck(ServerLevel, BlockPos, HearthBlockEntity)} with a probe the caller already has. */
    static Check recheck(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, HearthStructure.Probe probe) {
        Check check = HearthStructure.check(probe);
        HearthState state = hearth.state();
        if (state.stage() > state.highestOffering()) {
            // Only a command or an editor sets a stage above what was offered: it counts as paid.
            state.setHighestOffering(state.stage());
        }
        int stage = HearthStructure.stage(check.built(), state.highestOffering());
        markChecked(lane(hearth), level.getGameTime());
        if (stage != state.stage()) {
            state.setStage(stage);
        }
        hearth.setChanged();
        return check;
    }

    /**
     * Re-checks at once and applies the result (blockstate, announcements, state sends): what a test
     * or another lane calls when it cannot wait for the timer.
     */
    public static Check recheckNow(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        return recheckNow(level, pos, hearth, StageBlocks.probe(level, pos, Hearths.data()));
    }

    static Check recheckNow(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, HearthStructure.Probe probe) {
        Check check = recheck(level, pos, hearth, probe);
        sync(level, pos, hearth, check);
        return check;
    }

    /**
     * The blockstate follows the ceiling (a Character drop after {@link #FALL_DELAY_TICKS}); a change
     * is sent to who is home and told at most once per {@link #TELL_GAP_TICKS}. {@code check} null when
     * none ran.
     */
    static void sync(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, @Nullable Check check) {
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        CompoundTag lane = lane(hearth);
        long now = level.getGameTime();
        int live = state.ceiling(data);
        if (!lane.contains(CEILING)) {
            lane.putInt(CEILING, live);
            hearth.setChanged();
        }
        int shown = lane.getInt(CEILING);
        int next = shown;
        boolean pending = false;
        if (live > shown || live < shown && state.stage() < shown) {
            // A rise, or a layer that fell: at once (the structure is re-read at most every 30 s anyway).
            next = live;
        } else if (live < shown) {
            // Only Character holds it lower: it has to stay low a while before the Hearth shows it.
            long since = lane.contains(LOW_SINCE) ? lane.getLong(LOW_SINCE) : now;
            if (since > now) {
                since = now;
            }
            if (now - since >= FALL_DELAY_TICKS) {
                next = live;
            } else {
                pending = true;
                if (!lane.contains(LOW_SINCE) || lane.getLong(LOW_SINCE) != since) {
                    lane.putLong(LOW_SINCE, since);
                    hearth.setChanged();
                }
            }
        }
        if (!pending && lane.contains(LOW_SINCE)) {
            lane.remove(LOW_SINCE);
            hearth.setChanged();
        }
        showCeiling(level, pos, next);
        if (next != shown) {
            lane.putInt(CEILING, next);
            hearth.setChanged();
            HearthPresence.update(level, pos, hearth);
        }
        tell(level, pos, hearth, next, check, now);
        if (check != null) {
            announceReady(level, pos, hearth, check);
        }
    }

    /**
     * The fall or rise line, at most once per {@link #TELL_GAP_TICKS}: it names where the shown
     * ceiling stands against the last line, so a ceiling that went down and back up inside the gap
     * says nothing at all.
     */
    private static void tell(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, int shown, @Nullable Check check,
            long now) {
        CompoundTag lane = lane(hearth);
        if (!lane.contains(TOLD)) {
            // A Hearth saved before the gap existed: its last line was about the shown ceiling.
            lane.putInt(TOLD, shown);
            hearth.setChanged();
            return;
        }
        int told = lane.getInt(TOLD);
        if (shown == told) {
            return;
        }
        long at = lane.contains(TOLD_AT) ? lane.getLong(TOLD_AT) : Long.MIN_VALUE;
        if (at != Long.MIN_VALUE && now >= at && now - at < TELL_GAP_TICKS) {
            return;
        }
        lane.putInt(TOLD, shown);
        lane.putLong(TOLD_AT, now);
        hearth.setChanged();
        announce(level, pos, hearth, told, shown, check);
    }

    /**
     * The ceiling was just told by the player's own moment (a lighting or a sealed stage): show it,
     * and count it as the last line, so the second does not also say "stands again".
     */
    static void settle(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, int ceiling) {
        CompoundTag lane = lane(hearth);
        lane.putInt(CEILING, ceiling);
        lane.putInt(TOLD, ceiling);
        lane.remove(LOW_SINCE);
        hearth.setChanged();
        showCeiling(level, pos, ceiling);
    }

    /**
     * The once-a-second part for one Hearth, exactly as its tick runs it (a re-check when one is due,
     * then the blockstate and the lines): for a test that drives the clock itself.
     */
    public static void second(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        tick(level, pos, hearth);
    }

    /** The {@code stage} property follows the ceiling (1..6); the smouldering flag is left alone. */
    static void showCeiling(ServerLevel level, BlockPos pos, int ceiling) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(HearthBlocks.HEARTH.get())) {
            return;
        }
        int shown = Math.max(1, Math.min(HearthData.TIERS, ceiling));
        if (current.getValue(HearthBlock.STAGE) != shown) {
            level.setBlock(pos, current.setValue(HearthBlock.STAGE, shown), Block.UPDATE_ALL);
        }
    }

    private static void announce(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, int was, int ceiling,
            @Nullable Check check) {
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        Component line;
        if (ceiling > was) {
            line = StageText.rose(ceiling);
            StageFx.rose(level, pos);
        } else {
            line = StageText.fell(ceiling, why(level, pos, state, ceiling, check, data));
            StageFx.fell(level, pos);
        }
        for (ServerPlayer player : HearthPresence.inRange(level, pos)) {
            StageText.chat(player, line);
        }
    }

    /** Why the ceiling sits where it does: the first broken block of the build, or the Character missing. */
    private static Component why(ServerLevel level, BlockPos pos, HearthState state, int ceiling,
            @Nullable Check check, HearthData data) {
        if (state.stage() > ceiling) {
            int next = Math.min(HearthData.TIERS, ceiling + 1);
            return StageText.characterShort(state.character(), data.character().forStage(next));
        }
        Check read = check != null ? check : HearthStructure.check(StageBlocks.probe(level, pos, data));
        if (read.problem() != null) {
            return StageText.problem(read.problem());
        }
        return StageText.stageName(Math.max(1, ceiling));
    }

    /**
     * The next layer stands and only its offering is missing: say so once, until the layer falls
     * again. The offering names the item ("Offer a gold block at the Hearth to seal it").
     */
    private static void announceReady(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, Check check) {
        HearthState state = hearth.state();
        CompoundTag lane = lane(hearth);
        int next = state.highestOffering() + 1;
        boolean standing = next >= 2 && next <= HearthData.TIERS && check.built() >= next;
        int told = lane.getInt(READY);
        if (!standing) {
            if (told != 0) {
                lane.putInt(READY, 0);
                hearth.setChanged();
            }
            return;
        }
        if (told == next) {
            return;
        }
        lane.putInt(READY, next);
        hearth.setChanged();
        Component line = StageText.ready(next, StageBlocks.offeringName(Hearths.data(), next));
        for (ServerPlayer player : HearthPresence.inRange(level, pos)) {
            StageText.chat(player, line);
        }
    }

    /**
     * A block changed at {@code pos}: every Hearth whose check box holds it is marked dirty. Walks the
     * dimension's index (a handful of entries), so a placement far from any Hearth costs a few compares.
     */
    static void touched(ServerLevel level, BlockPos pos) {
        var store = HearthIndexData.get(level).store();
        if (store.size() == 0) {
            return;
        }
        LongArrayList hits = null;
        for (LongIterator it = store.all().iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            int dx = pos.getX() - HearthPos.unpackX(packed);
            int dy = pos.getY() - HearthPos.unpackY(packed);
            int dz = pos.getZ() - HearthPos.unpackZ(packed);
            if (HearthStructure.inBox(dx, dy, dz) && (dx != 0 || dy != 0 || dz != 0)) {
                if (hits == null) {
                    hits = new LongArrayList(1);
                }
                hits.add(packed);
            }
        }
        if (hits == null) {
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            HearthBlockEntity hearth = HearthRange.entity(level, BlockPos.of(hits.getLong(i)));
            if (hearth != null) {
                lane(hearth).putBoolean(DIRTY, true);
                hearth.setChanged();
            }
        }
    }

    /** The ceiling a Hearth shows for a chat line: the blockstate's (its ceiling), never below I. */
    static int shownStage(HearthBlockEntity hearth) {
        CompoundTag lane = lane(hearth);
        int ceiling = lane.contains(CEILING) ? lane.getInt(CEILING) : hearth.state().ceiling(Hearths.data());
        return Math.max(1, Math.min(HearthData.TIERS, ceiling));
    }
}
