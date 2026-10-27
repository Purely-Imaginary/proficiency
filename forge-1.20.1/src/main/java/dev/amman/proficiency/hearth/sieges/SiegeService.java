package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.WarmthMath;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.random.RandomGenerator;

/**
 * A Hearth's siege, from the roll to the outcome (docs/HEARTH-SPEC.md, "Sieges"). Called once a
 * second for every ticking Hearth ({@code HearthHooks.HEARTH_TICK}); everything it decides comes from
 * {@link SiegeRules} and the lane's pure helpers.
 *
 * <ul>
 * <li><b>Idle</b>: while the condition holds (first horn heard, the whole active group home at full
 * Warmth, ceiling III+, a pool at tier I+, cooldown served, sieges on, not Peaceful), one roll every
 * {@code rollSeconds}; a hit starts the countdown.</li>
 * <li><b>Countdown</b>: due {@code warningTicks} after a hit (a horn: {@code hornMinDelaySeconds}),
 * then the next dusk. At that dusk someone active must be home, else it waits a night (at most
 * {@code postponeNights}) and then fizzles (the cooldown counts half served). Nightfalls slept through
 * while the chunk was unloaded, or skipped by a bed, are caught up as postponements; a bed may not
 * sleep through the dusk that starts it ({@link SiegeClock#bedSkipsStart}).</li>
 * <li><b>Active</b>: {@code waves} waves from the edge of range, the next when half of the last is
 * down; siege mobs next to the Hearth fill the douse meter. Every wave down before dawn, each mob
 * killed by a player or a pet (or by its own side, {@link SiegeTally}): defended; one taken by a moat,
 * a golem or a portal makes it unearned (neither). Meter full: doused. Dawn, or nobody home for
 * {@code abandonGraceSeconds}: neither. A tracked mob leaves the roster only through its death or its
 * removal event, never because its chunk is not loaded.</li>
 * </ul>
 *
 * <p>State: the core fields on {@link HearthState} (phase, due time, horn, postponements, wave,
 * douse, defence streak, the two "last siege ended" clocks, night-before) and the lane's own tag on
 * the block entity ({@link #LANE}: siege id, tracked mobs, kills, who was there). Who a siege concerns
 * lives in {@link SiegeWorldData}, readable while the Hearth is unloaded.
 */
public final class SiegeService {

    public static final String LANE = "sieges";

    /** What a siege mob's goal learns when it asks after its siege. */
    public enum Status { RUNNING, OVER, UNKNOWN }

    /**
     * Test seam: Hearth keys whose natural once-a-second tick leaves the siege alone, so a GameTest
     * can drive {@link #second} with its own clock, players and dice.
     */
    public static final Set<String> MANUAL = ConcurrentHashMap.newKeySet();

    static final String K_ID = "id";
    static final String K_LAST_GAME = "lastGame";
    static final String K_LAST_DAY = "lastDay";
    static final String K_MOBS = "mobs";
    static final String K_WAVE_SPAWNED = "waveSpawned";
    static final String K_KILLS = "kills";
    static final String K_NAMES = "names";
    static final String K_PRESENT = "present";
    static final String K_ABSENT = "absent";
    static final String K_WARM = "warm";
    static final String K_GROUP = "group";
    static final String K_LEFT = "left";
    static final String K_DOUSE_SENT = "douseSent";
    static final String K_STARTED = "started";
    static final String K_FAILURES = "failures";
    static final String K_NIGHT_SEEN = "nightSeen";
    static final String K_UNEARNED = "unearned";
    /** The siege's level, 1..5, when a horn set it; 0 (absent) for a rolled siege: the ceiling's level. */
    static final String K_LEVEL = "level";

    /** A tick gap longer than this means the Hearth was not ticking (its chunk was unloaded). */
    static final long GAP_TICKS = 40L;
    /** Seconds of failed wave spawns (no ground anywhere on the ring) before the siege fizzles. */
    static final int SPAWN_FAILURE_LIMIT = 30;

    private static final Map<String, Integer> ROLL_IN = new ConcurrentHashMap<>();

    private SiegeService() {
    }

    // ---- The second ----------------------------------------------------------------------------

    /** The Hearth tick listener: the real clocks, every online player, the level's dice. */
    static void onHearthSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        if (MANUAL.contains(hearth.key())) {
            return;
        }
        RandomGenerator dice = level.getRandom()::nextLong;
        second(level, pos, hearth, level.getGameTime(), level.getDayTime(),
                level.getServer().getPlayerList().getPlayers(), dice::nextDouble);
    }

    /** One siege second of one Hearth: {@code now} in game ticks, {@code day} the day time, {@code online} every player. */
    public static void second(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            List<ServerPlayer> online, DoubleSupplier dice) {
        HearthState state = hearth.state();
        if (state.smouldering()) {
            coldHearth(level, pos, online);
        }
        switch (state.siegePhase()) {
            case IDLE -> idle(level, pos, hearth, now, day, online, dice);
            case COUNTDOWN -> countdown(level, pos, hearth, now, day, online);
            case ACTIVE -> active(level, pos, hearth, now, day, online);
        }
    }

    // ---- Idle: the condition and the roll ---------------------------------------------------------

    private static void idle(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            List<ServerPlayer> online, DoubleSupplier dice) {
        String key = hearth.key();
        if (!conditionHolds(level, pos, hearth, now, online)) {
            ROLL_IN.remove(key);
            return;
        }
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "condition", "", 1.0);
        int every = (int) Math.max(1, Math.round(Hearths.data().siege().rollSeconds()));
        int left = ROLL_IN.getOrDefault(key, every) - 1;
        if (left > 0) {
            ROLL_IN.put(key, left);
            return;
        }
        ROLL_IN.put(key, every);
        roll(level, pos, hearth, now, day, dice.getAsDouble(), group(online, now));
    }

    /** The facts of the condition at this Hearth now; {@code online} is every player on the server. */
    public static SiegeRules.Facts facts(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now,
            List<ServerPlayer> online) {
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        int[] warm = warmGroup(level, pos, now, online);
        long playNow = HearthWorldData.get(level.getServer()).playTicks();
        return new SiegeRules.Facts(state.sieged(), warm[1], warm[0], state.ceiling(data), state.maxEffectiveTier(data),
                state.playHoursSinceSiege(playNow), state.ticksSinceSiege(now), ProficiencyConfig.hearthSieges(),
                level.getDifficulty() == Difficulty.PEACEFUL, state.siegePhase() != SiegeRules.Phase.IDLE);
    }

    public static boolean conditionHolds(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now,
            List<ServerPlayer> online) {
        return SiegeRules.conditionHolds(facts(level, pos, hearth, now, online), Hearths.data().siege());
    }

    /**
     * One roll, the condition taken as held: with a hit the countdown starts for {@code group} and
     * true comes back. {@code random} is uniform in [0, 1).
     */
    public static boolean roll(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            double random, Collection<ServerPlayer> group) {
        HearthData.Siege s = Hearths.data().siege();
        double hours = hearth.state().playHoursSinceSiege(HearthWorldData.get(level.getServer()).playTicks());
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "roll", "", SiegeRules.chance(hours, s));
        if (!SiegeRules.roll(hours, random, s)) {
            return false;
        }
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "hit", "", 1.0);
        beginCountdown(level, pos, hearth, now, day, false, group, null);
        return true;
    }

    /** The condition and one roll together, without the once-a-minute pacing (the roll path GameTests use). */
    public static boolean rollIfReady(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            double random, List<ServerPlayer> online) {
        return conditionHolds(level, pos, hearth, now, online)
                && roll(level, pos, hearth, now, day, random, group(online, now));
    }

    // ---- The horn -------------------------------------------------------------------------------

    /**
     * A Siege Horn blown by {@code player}: the checks, then a siege at the next nightfall at least
     * {@code hornMinDelaySeconds} away. The reason comes back; on {@code OK} the caller consumes the horn.
     */
    public static HornCheck.Result blowHorn(ServerPlayer player) {
        return blowHorn(player, 1);
    }

    /**
     * A horn of siege level {@code hornLevel} (1..5): it needs the ceiling {@link
     * SiegeRules#ceilingForLevel} and calls a siege of that level, whatever the ceiling would roll.
     */
    public static HornCheck.Result blowHorn(ServerPlayer player, int hornLevel) {
        ServerLevel level = player.serverLevel();
        HearthData data = Hearths.data();
        BlockPos at = HearthRange.hearthAt(level, player.blockPosition());
        HearthBlockEntity hearth = at == null ? null : HearthRange.entity(level, at);
        HearthState state = hearth == null ? null : hearth.state();
        // The horn's cooldown runs from the last siege here. Before the first horn there was none (the
        // lighting seeds the clock for the roll only), so a freshly built Homestead may call at once.
        double hours = state == null ? 0.0 : !state.sieged() ? Double.POSITIVE_INFINITY
                : state.playHoursSinceSiege(HearthWorldData.get(level.getServer()).playTicks());
        HornCheck.Result result = HornCheck.check(hornLevel, ProficiencyConfig.hearthSieges(),
                level.getDifficulty() == Difficulty.PEACEFUL, hearth != null,
                state != null && state.smouldering(), state == null ? 0 : state.ceiling(data),
                state != null && state.siegePhase() != SiegeRules.Phase.IDLE, hours, data.siege());
        if (result != HornCheck.Result.OK) {
            refuse(player, result, hours, data.siege(), hornLevel);
            return result;
        }
        long now = level.getGameTime();
        List<ServerPlayer> concerned = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other != player && !other.isSpectator() && inRange(level, at, other)) {
                concerned.add(other);
            }
        }
        concerned.add(0, player);
        SiegeNotices.hornCall(player, hornLevel);
        beginCountdown(level, at, hearth, now, level.getDayTime(), true, concerned, player, hornLevel);
        TelemetryService.hearth(player, Telemetry.HEARTH_SIEGE, "horn", String.valueOf(hornLevel), 1.0);
        return result;
    }

    private static void refuse(ServerPlayer player, HornCheck.Result result, double hours, HearthData.Siege s,
            int hornLevel) {
        int need = SiegeRules.ceilingForLevel(hornLevel);
        switch (result) {
            case OFF -> SiegeNotices.bar(player, "horn.off", ChatFormatting.RED);
            case PEACEFUL -> SiegeNotices.bar(player, "horn.peaceful", ChatFormatting.RED);
            case NO_HEARTH -> SiegeNotices.bar(player, "horn.no_hearth", ChatFormatting.RED);
            case SMOULDERING -> SiegeNotices.bar(player, "horn.smouldering", ChatFormatting.RED);
            case CEILING -> {
                if (hornLevel <= 1) {
                    SiegeNotices.bar(player, "horn.ceiling", ChatFormatting.RED);
                } else {
                    SiegeNotices.bar(player, "horn.ceiling_level", ChatFormatting.RED,
                            HearthBlocks.hornOf(hornLevel).getDescription(), HearthRules.roman(need),
                            Component.translatable("proficiency.hearth.stages.name." + need));
                }
            }
            case BUSY -> SiegeNotices.bar(player, "horn.busy", ChatFormatting.GOLD);
            case COOLDOWN -> SiegeNotices.bar(player, "horn.cooldown", ChatFormatting.RED,
                    HornCheck.minutesLeft(hours, s));
            default -> {
            }
        }
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    // ---- Countdown ------------------------------------------------------------------------------

    /**
     * A siege is coming: due {@code warningTicks} from now (a horn: {@code hornMinDelaySeconds}), then
     * the next dusk. {@code concerned} hear the horn, read the line and get the panel; whoever enters
     * range later is told then.
     */
    public static void beginCountdown(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            boolean byHorn, Collection<ServerPlayer> concerned, @Nullable ServerPlayer blower) {
        beginCountdown(level, pos, hearth, now, day, byHorn, concerned, blower, 0);
    }

    /** As above, with the siege's level (1..5; 0 = the ceiling's, read when the waves come). */
    public static void beginCountdown(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            boolean byHorn, Collection<ServerPlayer> concerned, @Nullable ServerPlayer blower, int siegeLevel) {
        HearthData.Siege s = Hearths.data().siege();
        HearthState state = hearth.state();
        state.setSiegePhase(SiegeRules.Phase.COUNTDOWN);
        state.setSiegeDue(byHorn ? SiegeRules.hornDue(now, s) : SiegeRules.rolledDue(now, s));
        state.setSiegeByHorn(byHorn);
        state.setPostponed(0);
        state.setWave(0);
        state.setDouse(0);
        state.setNightBefore(SiegeRules.isNight(day));
        if (byHorn) {
            state.setSieged(true);
        }
        CompoundTag lane = resetLane(hearth);
        lane.putLong(K_ID, now);
        if (siegeLevel > 0) {
            lane.putInt(K_LEVEL, Math.min(SiegeRules.MAX_LEVEL, siegeLevel));
        }
        lane.putLong(K_LAST_GAME, now);
        lane.putLong(K_LAST_DAY, day);
        int[] warm = warmGroup(level, pos, now, level.getServer().getPlayerList().getPlayers());
        lane.putInt(K_WARM, warm[0]);
        lane.putInt(K_GROUP, warm[1]);
        hearth.setChanged();
        ROLL_IN.remove(hearth.key());

        SiegeWorldData world = SiegeWorldData.get(level.getServer());
        world.open(hearth.key(), dimension(level), pos.asLong());
        for (ServerPlayer player : concerned) {
            world.concern(hearth.key(), player.getUUID());
        }
        world.update(hearth.key(), SiegePanelPayload.COUNTDOWN, 0, s.waves(), 0, 0L);

        List<ServerPlayer> told = List.copyOf(concerned);
        if (byHorn) {
            if (blower != null) {
                Component horn = HearthBlocks.hornOf(Math.max(1, siegeLevel)).getDescription();
                SiegeNotices.chat(told, "announce.horn", ChatFormatting.GOLD, blower.getDisplayName(), horn,
                        HearthRules.roman(Math.max(1, siegeLevel)));
                SiegeNotices.bar(blower, "horn.blown", ChatFormatting.GOLD, horn);
            } else {
                // An operator's call from the console names nobody (it read "? blew the Siege Horn").
                SiegeNotices.chat(told, "announce.called", ChatFormatting.GOLD);
            }
        } else {
            SiegeNotices.chat(told, "announce.rolled", ChatFormatting.GOLD);
            SiegeNotices.distantHorn(told, level, pos, 64.0f);
        }
        sendCountdown(level, hearth, told, now, day);
        refreshState(level, hearth, level.getServer().getPlayerList().getPlayers());
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, byHorn ? "called" : "rolled", "",
                told.size());
    }

    private static void countdown(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            List<ServerPlayer> online) {
        HearthData.Siege s = Hearths.data().siege();
        HearthState state = hearth.state();
        CompoundTag lane = hearth.laneData(LANE);
        if (!ProficiencyConfig.hearthSieges() || level.getDifficulty() == Difficulty.PEACEFUL) {
            cancel(level, pos, hearth, now);
            return;
        }
        long lastGame = lane.getLong(K_LAST_GAME);
        long lastDay = lane.getLong(K_LAST_DAY);
        boolean unloaded = now - lastGame > GAP_TICKS;
        boolean slept = !unloaded && SiegeClock.dayJumped(lastGame, lastDay, now, day);
        if (lane.contains(K_LAST_GAME) && (unloaded || slept)) {
            // Unloaded, or a night slept through (a bed or /time jumps the day clock past dusk): nobody
            // stood at the fire at the nightfalls in between, so each one due is a postponement. A jump
            // that lands in a night that began after the last check leaves that night's fall to the live
            // check below (night now, not night before), as if the sun had set this second.
            long until = day;
            boolean freshNight = false;
            if (slept && SiegeRules.isNight(day)) {
                long fell = SiegeClock.nightFell(day);
                freshNight = fell > lastDay;
                until = freshNight ? fell : day;
            }
            int missed = SiegeClock.missedNightfalls(lastGame, lastDay, now, until,
                    state.siegeDue(), s.postponeNights() - state.postponed() + 1);
            for (int i = 0; i < missed; i++) {
                if (state.postponed() < s.postponeNights()) {
                    postpone(level, hearth, now, day, slept ? "postponed.slept" : "postponed");
                } else {
                    end(level, pos, hearth, SiegeRules.Outcome.FIZZLED, now, online);
                    return;
                }
            }
            state.setNightBefore(!freshNight && SiegeClock.nightBeforeLiveStep(day));
            if (slept && missed == 0) {
                // Slept through a night before the due time: the dusk it waits for is now closer in game
                // time than the panel's countdown says.
                sendCountdown(level, hearth, concerned(level, hearth), now, day);
            }
        }
        lane.putLong(K_LAST_GAME, now);
        lane.putLong(K_LAST_DAY, day);

        // Anyone who comes home during the countdown is told.
        SiegeWorldData world = SiegeWorldData.get(level.getServer());
        for (ServerPlayer player : online) {
            if (fighter(player) && inRange(level, pos, player) && world.concern(hearth.key(), player.getUUID())) {
                SiegeNotices.chat(List.of(player), "announce.late", ChatFormatting.GOLD);
                SiegeNotices.distantHorn(player, level, pos, 0.8f);
                sendCountdown(level, hearth, List.of(player), now, day);
            }
        }
        // "Warm n/m" on the panel follows the group, sent only when it changes.
        int[] warm = warmGroup(level, pos, now, online);
        if (warm[0] != lane.getInt(K_WARM) || warm[1] != lane.getInt(K_GROUP)) {
            lane.putInt(K_WARM, warm[0]);
            lane.putInt(K_GROUP, warm[1]);
            sendCountdown(level, hearth, concerned(level, hearth), now, day);
        }

        boolean night = SiegeRules.isNight(day);
        if (SiegeRules.startsNow(now, state.siegeDue(), night, state.nightBefore())) {
            boolean someoneHome = !homeActive(level, pos, now, online).isEmpty();
            switch (SiegeRules.atNightfall(someoneHome, state.postponed(), s)) {
                case START -> start(level, pos, hearth, now, day, online);
                case POSTPONE -> postpone(level, hearth, now, day, "postponed");
                case FIZZLE -> {
                    end(level, pos, hearth, SiegeRules.Outcome.FIZZLED, now, online);
                    return;
                }
            }
        }
        state.setNightBefore(night);
        hearth.setChanged();
    }

    private static void postpone(ServerLevel level, HearthBlockEntity hearth, long now, long day, String line) {
        HearthData.Siege s = Hearths.data().siege();
        HearthState state = hearth.state();
        state.setPostponed(state.postponed() + 1);
        hearth.setChanged();
        List<ServerPlayer> concerned = concerned(level, hearth);
        SiegeNotices.chat(concerned, line, ChatFormatting.GRAY, state.postponed(), s.postponeNights());
        sendCountdown(level, hearth, concerned, now, day);
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "postpone", "", 1.0);
    }

    // ---- Active ---------------------------------------------------------------------------------

    /** Nightfall with someone home: the siege begins and the first wave comes. */
    public static void start(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            List<ServerPlayer> online) {
        HearthData.Siege s = Hearths.data().siege();
        HearthState state = hearth.state();
        state.setSiegePhase(SiegeRules.Phase.ACTIVE);
        state.setWave(0);
        state.setDouse(0);
        CompoundTag lane = hearth.laneData(LANE);
        lane.putLong(K_STARTED, now);
        lane.put(K_MOBS, new ListTag());
        lane.put(K_KILLS, new CompoundTag());
        lane.put(K_NAMES, new CompoundTag());
        lane.put(K_PRESENT, new ListTag());
        lane.putInt(K_ABSENT, 0);
        lane.putInt(K_FAILURES, 0);
        lane.putInt(K_WAVE_SPAWNED, 0);
        lane.putInt(K_LEFT, 0);
        lane.putInt(K_DOUSE_SENT, 0);
        lane.putInt(K_UNEARNED, 0);
        lane.putLong(K_LAST_GAME, now);
        lane.putLong(K_LAST_DAY, day);
        lane.putBoolean(K_NIGHT_SEEN, SiegeRules.isNight(day));
        hearth.setChanged();

        SiegeWorldData world = SiegeWorldData.get(level.getServer());
        world.open(hearth.key(), dimension(level), pos.asLong());
        List<ServerPlayer> home = home(level, pos, online);
        for (ServerPlayer player : home) {
            world.concern(hearth.key(), player.getUUID());
            if (active(player, now)) {
                addPresent(lane, player);
            }
        }
        world.update(hearth.key(), SiegePanelPayload.WAVES, 0, s.waves(), 0, now);
        List<ServerPlayer> concerned = concerned(level, hearth);
        // The panel plays the raid horn on every new wave (the client lane): no second horn from here.
        SiegeNotices.chat(concerned, "begin", ChatFormatting.RED);
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "start", "", homeActive(level, pos, now, online).size());
        RandomGenerator dice = level.getRandom()::nextLong;
        spawnWave(level, pos, hearth, now, online, dice);
        sendWaves(level, hearth, concerned(level, hearth));
        refreshState(level, hearth, online);
    }

    private static void active(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, long day,
            List<ServerPlayer> online) {
        HearthData.Siege s = Hearths.data().siege();
        HearthState state = hearth.state();
        CompoundTag lane = hearth.laneData(LANE);
        if (!ProficiencyConfig.hearthSieges() || level.getDifficulty() == Difficulty.PEACEFUL) {
            cancel(level, pos, hearth, now);
            return;
        }
        long graceTicks = Math.round(s.abandonGraceSeconds() * 20.0);
        if (lane.contains(K_LAST_GAME) && now - lane.getLong(K_LAST_GAME) > Math.max(GAP_TICKS, graceTicks)) {
            // The chunk was unloaded: nobody was home.
            end(level, pos, hearth, SiegeRules.Outcome.ABANDONED, now, online);
            return;
        }
        lane.putLong(K_LAST_GAME, now);
        lane.putLong(K_LAST_DAY, day);

        SiegeWorldData world = SiegeWorldData.get(level.getServer());
        List<ServerPlayer> home = home(level, pos, online);
        for (ServerPlayer player : home) {
            if (active(player, now)) {
                addPresent(lane, player);
            }
            if (world.concern(hearth.key(), player.getUUID())) {
                sendWaves(level, hearth, List.of(player));
            }
        }
        if (home.isEmpty()) {
            int absent = lane.getInt(K_ABSENT) + 1;
            lane.putInt(K_ABSENT, absent);
            hearth.setChanged();
            if (absent >= Math.max(1, Math.round(s.abandonGraceSeconds()))) {
                end(level, pos, hearth, SiegeRules.Outcome.ABANDONED, now, online);
            }
            return;
        }
        lane.putInt(K_ABSENT, 0);
        // Dawn: day after a night of this siege. One started by day (the op command) runs until the
        // dawn after the coming night.
        if (SiegeRules.isNight(day)) {
            lane.putBoolean(K_NIGHT_SEEN, true);
        } else if (lane.getBoolean(K_NIGHT_SEEN)) {
            end(level, pos, hearth, SiegeRules.Outcome.DAWN, now, online);
            return;
        }

        List<Tracked> standing = tracked(level, lane);
        int near = 0;
        int current = 0;
        double reach = s.douseRadius() * s.douseRadius();
        for (Tracked t : standing) {
            if (t.wave() == state.wave()) {
                current++;
            }
            Mob mob = t.mob();
            // A rider (a spider jockey's skeleton) reaches the fire only on its mount: one hand on it.
            if (mob == null || mob.isPassenger()) {
                continue;
            }
            double dx = mob.getX() - (pos.getX() + 0.5);
            double dz = mob.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= reach && Math.abs(mob.getY() - pos.getY()) <= 3.0) {
                near++;
            }
        }
        double before = state.douse();
        double after = DouseMeter.step(before, near, 1.0, s);
        if (after != before) {
            state.setDouse(after);
            douseCues(level, pos, home, near, before, after, s);
        }
        if (DouseMeter.full(after, s)) {
            end(level, pos, hearth, SiegeRules.Outcome.DOUSED, now, online);
            return;
        }

        if (state.wave() < s.waves() && SiegeRules.nextWaveDue(lane.getInt(K_WAVE_SPAWNED), current, s)) {
            RandomGenerator dice = level.getRandom()::nextLong;
            if (!spawnWave(level, pos, hearth, now, online, dice)) {
                int failures = lane.getInt(K_FAILURES) + 1;
                lane.putInt(K_FAILURES, failures);
                if (failures >= SPAWN_FAILURE_LIMIT) {
                    end(level, pos, hearth, SiegeRules.Outcome.FIZZLED, now, online);
                    return;
                }
            }
            standing = tracked(level, lane);
        } else if (state.wave() >= s.waves() && standing.isEmpty()) {
            // Every wave down: a defence only when players or their pets brought every one of them down.
            end(level, pos, hearth, SiegeTally.cleared(lane.getInt(K_UNEARNED)), now, online);
            return;
        }

        int douseStep = DouseMeter.percent(state.douse(), s) / 10;
        if (standing.size() != lane.getInt(K_LEFT) || douseStep != lane.getInt(K_DOUSE_SENT)) {
            lane.putInt(K_LEFT, standing.size());
            lane.putInt(K_DOUSE_SENT, douseStep);
            sendWaves(level, hearth, concerned(level, hearth));
        }
        hearth.setChanged();
    }

    private static void douseCues(ServerLevel level, BlockPos pos, List<ServerPlayer> home, int near, double before,
            double after, HearthData.Siege s) {
        if (near > 0) {
            SiegeNotices.puff(level, pos, ParticleTypes.SMOKE, Math.min(12, 2 + DouseMeter.percent(after, s) / 10),
                    0.25, 0.01);
        }
        int warning = DouseMeter.crossed(before, after, s);
        if (warning > 0) {
            SiegeNotices.at(level, pos, SoundEvents.FIRE_EXTINGUISH, 0.7f, 0.8f + warning / 200f);
            SiegeNotices.bar(home, "douse", warning >= 80 ? ChatFormatting.RED : ChatFormatting.GOLD, warning);
        }
    }

    /** Spawns the next wave; false when not one mob found ground (the wave is not counted then). */
    static boolean spawnWave(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now,
            List<ServerPlayer> online, RandomGenerator dice) {
        HearthData data = Hearths.data();
        HearthData.Siege s = data.siege();
        HearthState state = hearth.state();
        CompoundTag lane = hearth.laneData(LANE);
        int key = SiegeRules.levelKey(siegeLevel(state, lane, data));
        List<ServerPlayer> home = home(level, pos, online);
        int playersHome = Math.max(1, homeActive(level, pos, now, online).size());
        int size = SiegeRules.waveSize(key, playersHome, s);
        List<String> ids = SiegeWaves.compose(size, key, s, dice);
        double[] gates = SiegeWaves.gateAngles(ids.size(), dice);
        long siegeId = lane.getLong(K_ID);
        int wave = state.wave() + 1;
        ListTag mobs = lane.getList(K_MOBS, Tag.TAG_COMPOUND);
        int spawned = 0;
        int fielded = 0;
        Set<Integer> from = new java.util.TreeSet<>();
        for (int i = 0; i < ids.size(); i++) {
            double gate = gates[i % gates.length];
            Mob mob = SiegeSpawner.spawn(level, pos, ids.get(i), gate, siegeId, home, s.spawnClearance(), dice);
            if (mob == null) {
                continue;
            }
            spawned++;
            from.add(SiegeWaves.direction(SiegeWaves.dx(gate, 1), SiegeWaves.dz(gate, 1)));
            track(mobs, mob, wave);
            fielded++;
            for (Entity rider : mob.getPassengers()) {
                if (rider instanceof Mob riding && SiegeMobs.isSiegeMob(riding)) {
                    track(mobs, riding, wave);
                    fielded++;
                }
            }
        }
        lane.put(K_MOBS, mobs);
        if (spawned == 0) {
            hearth.setChanged();
            return false;
        }
        state.setWave(wave);
        // Riders count too: the next wave comes when half of everything this wave fielded is down.
        lane.putInt(K_WAVE_SPAWNED, fielded);
        lane.putInt(K_FAILURES, 0);
        hearth.setChanged();
        SiegeWorldData.get(level.getServer()).update(hearth.key(), SiegePanelPayload.WAVES, wave, s.waves(),
                mobs.size(), lane.getLong(K_STARTED));

        List<ServerPlayer> concerned = concerned(level, hearth);
        SiegeNotices.chat(concerned, "wave", ChatFormatting.RED, wave, s.waves(), SiegeNotices.directions(from));
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "wave", String.valueOf(key), spawned);
        return true;
    }

    // ---- Kills, mobs ----------------------------------------------------------------------------

    /** A tracked siege mob and the wave it came with; {@code mob} is null while its chunk is not loaded (it still stands). */
    record Tracked(@Nullable Mob mob, int wave) {
    }

    private static void track(ListTag mobs, Mob mob, int wave) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("u", mob.getUUID());
        entry.putInt("w", wave);
        mobs.add(entry);
    }

    /**
     * Every siege mob still standing, loaded or not. A mob leaves the roster only through its death
     * ({@link #onSiegeMobDeath}) or its removal ({@link #onSiegeMobLeave}); one whose chunk is not
     * loaded (after a restart, before its entities are back) still stands, so it is never taken for
     * a dead one and cannot end a siege as defended.
     */
    static List<Tracked> tracked(ServerLevel level, CompoundTag lane) {
        ListTag mobs = lane.getList(K_MOBS, Tag.TAG_COMPOUND);
        List<Tracked> out = new ArrayList<>(mobs.size());
        for (int i = 0; i < mobs.size(); i++) {
            CompoundTag entry = mobs.getCompound(i);
            if (!entry.hasUUID("u")) {
                continue;
            }
            Entity entity = level.getEntity(entry.getUUID("u"));
            out.add(new Tracked(entity instanceof Mob mob && mob.isAlive() ? mob : null, entry.getInt("w")));
        }
        return out;
    }

    /** The roster entry of {@code id}, or null. */
    @Nullable
    private static CompoundTag entry(CompoundTag lane, UUID id) {
        ListTag mobs = lane.getList(K_MOBS, Tag.TAG_COMPOUND);
        for (int i = 0; i < mobs.size(); i++) {
            CompoundTag entry = mobs.getCompound(i);
            if (entry.hasUUID("u") && entry.getUUID("u").equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Takes a mob off the roster; false when it was not on it. */
    private static boolean untrack(CompoundTag lane, UUID id) {
        ListTag mobs = lane.getList(K_MOBS, Tag.TAG_COMPOUND);
        for (int i = 0; i < mobs.size(); i++) {
            CompoundTag entry = mobs.getCompound(i);
            if (entry.hasUUID("u") && entry.getUUID("u").equals(id)) {
                mobs.remove(i);
                lane.put(K_MOBS, mobs);
                return true;
            }
        }
        return false;
    }

    /** The living, loaded siege mobs of a Hearth's running siege (the bell's alarm makes them glow); empty when none. */
    public static List<Mob> siegeMobs(ServerLevel level, HearthBlockEntity hearth) {
        if (hearth.state().siegePhase() != SiegeRules.Phase.ACTIVE) {
            return List.of();
        }
        List<Mob> out = new ArrayList<>();
        for (Tracked t : tracked(level, hearth.laneData(LANE))) {
            if (t.mob() != null) {
                out.add(t.mob());
            }
        }
        return out;
    }

    /** The loaded Hearth whose running siege this siege mob belongs to, or null. */
    @Nullable
    private static HearthBlockEntity siegeOf(ServerLevel level, Entity mob) {
        long packed = SiegeMobs.hearthOf(mob);
        if (packed == dev.amman.proficiency.hearth.HearthIndexStore.NONE) {
            return null;
        }
        HearthBlockEntity hearth = HearthRange.entity(level, BlockPos.of(packed));
        if (hearth == null || hearth.state().siegePhase() != SiegeRules.Phase.ACTIVE
                || hearth.laneData(LANE).getLong(K_ID) != SiegeMobs.siegeIdOf(mob)) {
            return null;
        }
        return hearth;
    }

    /** A siege mob died: off the roster, and a kill for the player (or the pet's owner) with the credit. */
    static void onSiegeMobDeath(LivingEntity mob, DamageSource source) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        HearthBlockEntity hearth = siegeOf(level, mob);
        if (hearth != null) {
            fell(level, hearth, mob, source);
        }
    }

    private static void fell(ServerLevel level, HearthBlockEntity hearth, LivingEntity mob, DamageSource source) {
        CompoundTag lane = hearth.laneData(LANE);
        boolean onRoster = untrack(lane, mob.getUUID());
        ServerPlayer killer = creditOf(mob, source);
        switch (SiegeTally.fall(killer != null, byFellowSiegeMob(mob, source))) {
            case KILL -> {
                CompoundTag kills = lane.getCompound(K_KILLS);
                String id = killer.getUUID().toString();
                kills.putInt(id, kills.getInt(id) + 1);
                lane.put(K_KILLS, kills);
                CompoundTag names = lane.getCompound(K_NAMES);
                names.putString(id, killer.getGameProfile().getName());
                lane.put(K_NAMES, names);
                HearthCharge.siegeKill(hearth, killer.getUUID(), killer.getGameProfile().getName());
                TelemetryService.hearth(killer, Telemetry.HEARTH_SIEGE, "kill",
                        BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), 1.0);
            }
            case FRIENDLY -> {
            }
            case UNEARNED -> {
                if (onRoster) {
                    unearned(level, hearth, mob);
                }
            }
        }
        hearth.setChanged();
        rosterChanged(level, hearth);
    }

    /**
     * A siege mob left its level without dying: sent away by its own siege (ignored), unloaded with its
     * chunk (still stands), converted (a zombie drowned, a skeleton froze: the new mob takes its place
     * on the roster and its siege mark), a creeper that blew itself up at the defenders (nobody's kill,
     * no harm), or gone some other way (a portal, a mod): not killed by a player, so unearned. A death
     * the death event did not see (no Hearth then) is settled here from its last damage.
     */
    static void onSiegeMobLeave(Mob mob, ServerLevel level) {
        Entity.RemovalReason reason = mob.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER || SiegeMobs.sentAway(mob)) {
            return;
        }
        HearthBlockEntity hearth = siegeOf(level, mob);
        if (hearth == null) {
            return;
        }
        CompoundTag lane = hearth.laneData(LANE);
        CompoundTag entry = entry(lane, mob.getUUID());
        if (entry == null) {
            return;
        }
        if (reason == Entity.RemovalReason.KILLED) {
            DamageSource last = mob.getLastDamageSource();
            fell(level, hearth, mob, last != null ? last : level.damageSources().generic());
            return;
        }
        Mob into = reason == Entity.RemovalReason.DISCARDED ? convertedFrom(level, mob) : null;
        if (into != null) {
            BlockPos at = hearth.getBlockPos();
            SiegeSpawner.prepare(into, at, lane.getLong(K_ID));
            if (!SiegeGoal.has(into)) {
                into.goalSelector.addGoal(1, new SiegeGoal(into, at, lane.getLong(K_ID)));
            }
            entry.putUUID("u", into.getUUID());
            hearth.setChanged();
            return;
        }
        untrack(lane, mob.getUUID());
        boolean blewUp = reason == Entity.RemovalReason.DISCARDED && mob instanceof Creeper creeper
                && creeper.getSwelling(1.0f) >= 1.0f;
        if (!blewUp) {
            unearned(level, hearth, mob);
        }
        hearth.setChanged();
        rosterChanged(level, hearth);
    }

    /**
     * The mob a conversion just put in this one's place: {@code Mob.convertTo} adds the new mob at the
     * very same position, then discards the old one, all in one tick.
     */
    @Nullable
    private static Mob convertedFrom(ServerLevel level, Mob mob) {
        Vec3 at = mob.position();
        for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(0.5),
                m -> m != mob && m.isAlive() && m.tickCount <= 1)) {
            if (other.position().distanceToSqr(at) < 1.0E-6) {
                return other;
            }
        }
        return null;
    }

    /**
     * A fall no player or pet dealt; the first one tells the siege's players it can no longer be won
     * outright. Not counted while Peaceful or sieges-off clears the mobs (the siege is being called off).
     */
    private static void unearned(ServerLevel level, HearthBlockEntity hearth, Entity mob) {
        if (!ProficiencyConfig.hearthSieges() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        CompoundTag lane = hearth.laneData(LANE);
        int before = lane.getInt(K_UNEARNED);
        lane.putInt(K_UNEARNED, before + 1);
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "unearned",
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), 1.0);
        if (before == 0) {
            SiegeNotices.chat(concerned(level, hearth), "unearned", ChatFormatting.GRAY);
        }
    }

    /** A mob fewer: the panel and its login copy follow. */
    private static void rosterChanged(ServerLevel level, HearthBlockEntity hearth) {
        CompoundTag lane = hearth.laneData(LANE);
        int left = tracked(level, lane).size();
        lane.putInt(K_LEFT, left);
        SiegeWorldData.get(level.getServer()).update(hearth.key(), SiegePanelPayload.WAVES, hearth.state().wave(),
                Hearths.data().siege().waves(), left, lane.getLong(K_STARTED));
        sendWaves(level, hearth, concerned(level, hearth));
    }

    /** The player with the kill: the killer, a pet's owner, or the last player who hurt it. */
    @Nullable
    static ServerPlayer creditOf(LivingEntity mob, DamageSource source) {
        ServerPlayer player = playerOf(source.getEntity());
        return player != null ? player : playerOf(mob.getKillCredit());
    }

    /** Killed by its own side: an arrow, fangs or a blast from another siege mob. */
    static boolean byFellowSiegeMob(LivingEntity mob, DamageSource source) {
        Entity by = source.getEntity();
        Entity direct = source.getDirectEntity();
        return by != mob && by != null && SiegeMobs.isSiegeMob(by)
                || direct != mob && direct != null && SiegeMobs.isSiegeMob(direct);
    }

    @Nullable
    private static ServerPlayer playerOf(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return SkillTools.isFakePlayer(player) ? null : player;
        }
        if (entity instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
            return SkillTools.isFakePlayer(owner) ? null : owner;
        }
        return null;
    }

    /** What a siege mob's goal is told: its siege is running, over, or its Hearth is not loaded. */
    static Status status(ServerLevel level, BlockPos hearthPos, long siegeId) {
        if (!level.isLoaded(hearthPos)) {
            return Status.UNKNOWN;
        }
        HearthBlockEntity hearth = HearthRange.entity(level, hearthPos);
        if (hearth == null || hearth.state().siegePhase() != SiegeRules.Phase.ACTIVE
                || hearth.laneData(LANE).getLong(K_ID) != siegeId) {
            return Status.OVER;
        }
        return Status.RUNNING;
    }

    /** A leftover goes: a puff of smoke, no drops, and its removal is not a fall. */
    static void sendAway(ServerLevel level, Entity mob) {
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 10,
                0.3, 0.4, 0.3, 0.02);
        SiegeMobs.markSentAway(mob);
        mob.discard();
    }

    // ---- Outcomes -------------------------------------------------------------------------------

    /**
     * The siege ends. Defended: a burst outside the cap to every pool (credited by kills), a Siege
     * Trophy of the siege level's tier from the embers (the burst scaled by level against ceiling), the defence streak and the ledger. Doused: the
     * Hearth smoulders (boons off, Warmth 0 in range, every pool loses {@code dousedLoss}) until relit
     * with flint and steel. Dawn, abandoned, fizzled, unearned: neither. Leftovers go; the cooldown
     * starts now (a fizzle counts it half served).
     */
    public static void end(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, SiegeRules.Outcome outcome,
            long now, List<ServerPlayer> online) {
        HearthData data = Hearths.data();
        HearthData.Siege s = data.siege();
        HearthState state = hearth.state();
        CompoundTag lane = hearth.laneData(LANE);
        List<ServerPlayer> concerned = concerned(level, hearth);
        for (ServerPlayer player : home(level, pos, online)) {
            if (!concerned.contains(player)) {
                concerned.add(player);
            }
        }
        sendAwayLoaded(level, lane);
        long playNow = HearthWorldData.get(level.getServer()).playTicks();
        int waves = state.wave();
        int doused = DouseMeter.percent(state.douse(), s);
        state.setDefenceStreak(SiegeRules.streakAfter(state.defenceStreak(), outcome));
        switch (outcome) {
            case DEFENDED -> defended(level, pos, hearth, lane, concerned);
            case DOUSED -> {
                HearthCharge.cut(level, hearth, s.dousedLoss());
                setSmouldering(level, pos, hearth, true);
                coldHearth(level, pos, online);
                SiegeNotices.at(level, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.5f, 0.8f);
                SiegeNotices.puff(level, pos, ParticleTypes.LARGE_SMOKE, 30, 0.5, 0.03);
                SiegeNotices.chat(concerned, "outcome.doused", ChatFormatting.DARK_RED);
            }
            case DAWN -> SiegeNotices.chat(concerned, "outcome.dawn", ChatFormatting.GRAY);
            case ABANDONED -> SiegeNotices.chat(concerned, "outcome.abandoned", ChatFormatting.GRAY);
            case FIZZLED -> SiegeNotices.chat(concerned, "outcome.fizzled", ChatFormatting.GRAY);
            case UNEARNED -> SiegeNotices.chat(concerned, "outcome.unearned", ChatFormatting.GRAY);
        }
        state.setLastSiegeEnd(outcome == SiegeRules.Outcome.FIZZLED ? SiegeRules.fizzledEnd(playNow, now, s)
                : SiegeRules.endedAt(playNow, now));
        resetToIdle(level, hearth);

        SiegePanelPayload panel = new SiegePanelPayload(SiegePanelPayload.OUTCOME, pos.asLong(), dimension(level),
                now, waves, s.waves(), 0, outcome.ordinal(), doused, 100);
        for (ServerPlayer player : concerned) {
            HearthNet.sendSiegePanel(player, panel);
        }
        refreshHome(level, hearth, online);
        TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "outcome", outcome.id(), 1.0);
    }

    private static void defended(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, CompoundTag lane,
            List<ServerPlayer> concerned) {
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        int streak = state.defenceStreak();
        Map<UUID, Integer> kills = new LinkedHashMap<>();
        CompoundTag killTag = lane.getCompound(K_KILLS);
        for (String id : killTag.getAllKeys()) {
            UUID uuid = uuid(id);
            if (uuid != null) {
                kills.put(uuid, killTag.getInt(id));
            }
        }
        Set<UUID> present = new LinkedHashSet<>();
        for (Tag tag : lane.getList(K_PRESENT, Tag.TAG_INT_ARRAY)) {
            present.add(NbtUtils.loadUUID(tag));
        }
        present.addAll(kills.keySet());
        CompoundTag names = lane.getCompound(K_NAMES);
        List<ServerPlayer> there = new ArrayList<>();
        List<UUID> gone = new ArrayList<>();
        for (UUID id : present) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                there.add(player);
            } else {
                gone.add(id);
            }
        }
        // The burst goes through the Charge lane: outside the cap, into the ledger as "siege", split by
        // kills (evenly over who is still here when nobody killed anything), with its tier-ups and dots.
        int siegeLevel = siegeLevel(state, lane, data);
        double scale = SiegeRules.burstScale(siegeLevel, state.ceiling(data));
        for (HearthPool pool : HearthPool.VALUES) {
            TelemetryService.hearth(null, Telemetry.HEARTH_SIEGE, "burst", pool.id(),
                    SiegeRules.burst(state.charge(pool), streak, data, scale));
        }
        HearthCharge.siegeBurst(level, hearth, kills, there, streak, scale);
        HearthCharge.siegeDefended(hearth, there);
        for (UUID id : gone) {
            // Was there, logged off before the last mob fell: the defence still counts for them.
            state.ledger().addSiegeDefended(id, nameOf(level, names, id));
        }
        int tier = SiegeRules.trophyTier(siegeLevel);
        ItemStack trophy = SiegeTrophyItem.of(tier, level.getDayTime() / HearthData.TICKS_PER_DAY + 1);
        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, trophy,
                0.0, 0.25, 0.0);
        drop.setPickUpDelay(20);
        drop.setUnlimitedLifetime();
        drop.setGlowingTag(true);
        level.addFreshEntity(drop);
        SiegeNotices.puff(level, pos, ParticleTypes.FLAME, 24, 0.4, 0.05);
        SiegeNotices.puff(level, pos, ParticleTypes.TOTEM_OF_UNDYING, 30, 0.6, 0.3);
        // At the Hearth for whoever stands there; the panel plays the challenge chime to the defenders.
        SiegeNotices.at(level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f);
        SiegeNotices.chat(concerned, "outcome.defended", ChatFormatting.GOLD, streak);
        SiegeNotices.chat(concerned, "trophy.won", ChatFormatting.YELLOW,
                SiegeNotices.tierName(tier));
        HearthEvents.SIEGE_DEFENDED.fire(new HearthEvents.SiegeDefended(level, pos, Map.copyOf(kills), there, siegeLevel));
    }

    /**
     * Ends a siege quietly (sieges switched off, Peaceful, the op command): no reward, no loss, panel
     * cleared, the cooldown from now whether it was counting down or running (so switching Peaceful off
     * again is not a sure roll), and the Hearth panel's siege line back to quiet for the players in range.
     */
    public static void cancel(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now) {
        cancel(level, pos, hearth, now, true);
    }

    /** A Hearth broken with a siege pending or running: the siege goes, no Hearth state is sent for a block that is going. */
    static void cancelBroken(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now) {
        cancel(level, pos, hearth, now, false);
    }

    private static void cancel(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, long now, boolean refresh) {
        List<ServerPlayer> concerned = concerned(level, hearth);
        sendAwayLoaded(level, hearth.laneData(LANE));
        hearth.state().setLastSiegeEnd(SiegeRules.endedAt(HearthWorldData.get(level.getServer()).playTicks(), now));
        resetToIdle(level, hearth);
        SiegePanelPayload clear = new SiegePanelPayload(SiegePanelPayload.CLEAR, pos.asLong(), dimension(level), 0L,
                0, 0, 0, -1, 0, 0);
        for (ServerPlayer player : concerned) {
            HearthNet.sendSiegePanel(player, clear);
        }
        if (refresh) {
            refreshState(level, hearth, level.getServer().getPlayerList().getPlayers());
        }
    }

    /** Every siege field back to "nothing pending": the one reset both {@link #end} and {@link #cancel} use. */
    private static void resetToIdle(ServerLevel level, HearthBlockEntity hearth) {
        HearthState state = hearth.state();
        state.setSiegePhase(SiegeRules.Phase.IDLE);
        state.setSiegeDue(HearthState.NEVER);
        state.setSiegeByHorn(false);
        state.setPostponed(0);
        state.setWave(0);
        state.setDouse(0);
        resetLane(hearth);
        hearth.setChanged();
        ROLL_IN.remove(hearth.key());
        SiegeWorldData.get(level.getServer()).close(hearth.key());
    }

    /** The loaded leftovers go in smoke; unloaded ones go when they load and their goal finds the siege over. */
    private static void sendAwayLoaded(ServerLevel level, CompoundTag lane) {
        for (Tracked t : tracked(level, lane)) {
            if (t.mob() != null) {
                sendAway(level, t.mob());
            }
        }
    }

    // ---- Smouldering ----------------------------------------------------------------------------

    /** Sets the doused look and the state flag together (boons read the flag, the block shows it). */
    public static void setSmouldering(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, boolean on) {
        hearth.state().setSmouldering(on);
        hearth.setChanged();
        BlockState block = level.getBlockState(pos);
        if (block.is(HearthBlocks.HEARTH.get()) && block.getValue(HearthBlock.SMOULDERING) != on) {
            level.setBlock(pos, block.setValue(HearthBlock.SMOULDERING, on), Block.UPDATE_ALL);
        }
    }

    /** Flint and steel on a smouldering Hearth: it burns again (boons back, Warmth can rise). */
    public static void relight(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, @Nullable ServerPlayer by) {
        setSmouldering(level, pos, hearth, false);
        SiegeNotices.at(level, pos, SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);
        SiegeNotices.at(level, pos, SoundEvents.FIRECHARGE_USE, 0.5f, 1.3f);
        SiegeNotices.puff(level, pos, ParticleTypes.FLAME, 16, 0.3, 0.04);
        if (by != null) {
            SiegeNotices.bar(by, "relit", ChatFormatting.GOLD);
        }
        refreshHome(level, hearth, level.getServer().getPlayerList().getPlayers());
    }

    /** While a Hearth smoulders, nobody in range keeps any Warmth. */
    static void coldHearth(ServerLevel level, BlockPos pos, List<ServerPlayer> online) {
        for (ServerPlayer player : online) {
            if (inRange(level, pos, player)) {
                PlayerHearth data = HearthAttachments.of(player);
                if (data.warmth() > 0) {
                    data.setWarmth(0);
                    HearthNet.markHudDirty(player);
                }
            }
        }
    }

    // ---- Panels ---------------------------------------------------------------------------------

    /** The players a Hearth's siege concerns who are online now. */
    public static List<ServerPlayer> concerned(ServerLevel level, HearthBlockEntity hearth) {
        List<ServerPlayer> out = new ArrayList<>();
        SiegeWorldData.Watch watch = SiegeWorldData.get(level.getServer()).watch(hearth.key());
        if (watch == null) {
            return out;
        }
        for (UUID id : watch.concerned()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    /** Sends the panel for the siege's current phase to {@code players} (the bell's alarm pops it up with this). */
    public static void showPanel(ServerLevel level, HearthBlockEntity hearth, Collection<ServerPlayer> players) {
        switch (hearth.state().siegePhase()) {
            case COUNTDOWN -> sendCountdown(level, hearth, players, level.getGameTime(), level.getDayTime());
            case ACTIVE -> sendWaves(level, hearth, players);
            default -> {
            }
        }
    }

    /**
     * The countdown panel: {@code due} is the game time the siege is expected to start (the dusk after
     * the due time, from today's clocks), {@code warm} of {@code group} the active group home and warm.
     */
    static void sendCountdown(ServerLevel level, HearthBlockEntity hearth, Collection<ServerPlayer> players, long now,
            long day) {
        HearthState state = hearth.state();
        CompoundTag lane = hearth.laneData(LANE);
        SiegePanelPayload panel = new SiegePanelPayload(SiegePanelPayload.COUNTDOWN, hearth.packedPos(),
                dimension(level), SiegeClock.expectedStart(state.siegeDue(), now, day), 0,
                Hearths.data().siege().waves(), 0, -1, lane.getInt(K_WARM), lane.getInt(K_GROUP));
        for (ServerPlayer player : players) {
            HearthNet.sendSiegePanel(player, panel);
        }
    }

    /** The waves panel: {@code due} is when the siege began, {@code left} the living siege mobs, {@code warm} the douse meter in percent of {@code group} 100. */
    static void sendWaves(ServerLevel level, HearthBlockEntity hearth, Collection<ServerPlayer> players) {
        HearthState state = hearth.state();
        CompoundTag lane = hearth.laneData(LANE);
        HearthData.Siege s = Hearths.data().siege();
        int left = tracked(level, lane).size();
        int douse = DouseMeter.percent(state.douse(), s);
        SiegePanelPayload panel = new SiegePanelPayload(SiegePanelPayload.WAVES, hearth.packedPos(), dimension(level),
                lane.getLong(K_STARTED), state.wave(), s.waves(), left, -1, douse, 100);
        // What the panel shows now is what a concerned player logging in gets back.
        SiegeWorldData.get(level.getServer()).shown(hearth.key(), left, douse);
        for (ServerPlayer player : players) {
            HearthNet.sendSiegePanel(player, panel);
        }
    }

    /** A phase change (countdown, start): the panel's siege line, to the players in range. */
    static void refreshState(ServerLevel level, HearthBlockEntity hearth, List<ServerPlayer> online) {
        HearthStatePayload statePayload = HearthNet.stateOf(hearth, HearthStatePayload.UPDATE);
        for (ServerPlayer player : online) {
            if (inRange(level, hearth.getBlockPos(), player)) {
                HearthNet.sendState(player, statePayload);
            }
        }
    }

    /** After a burst, a douse or a relight: the state and tracker rows to the players in range. */
    static void refreshHome(ServerLevel level, HearthBlockEntity hearth, List<ServerPlayer> online) {
        HearthStatePayload statePayload = HearthNet.stateOf(hearth, HearthStatePayload.UPDATE);
        HearthTrackerPayload tracker = new HearthTrackerPayload(hearth.packedPos(),
                PoolRow.of(hearth.state(), Hearths.data()));
        for (ServerPlayer player : online) {
            if (inRange(level, hearth.getBlockPos(), player)) {
                HearthNet.sendState(player, statePayload);
                HearthNet.sendTracker(player, tracker);
            }
        }
    }

    /** Sends a logging-in player the panels of every siege that concerns them, wherever they are. */
    static void onLogin(ServerPlayer player) {
        SiegeWorldData world = SiegeWorldData.get(player.server);
        HearthWorldData mirror = HearthWorldData.get(player.server);
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, SiegeWorldData.Watch> entry : world.watches().entrySet()) {
            SiegeWorldData.Watch watch = entry.getValue();
            if (!watch.concerned().contains(player.getUUID())) {
                continue;
            }
            long due = mirror.siegeDue(entry.getKey());
            if (due == HearthState.NEVER) {
                stale.add(entry.getKey());
                continue;
            }
            ServerLevel level = levelOf(player, watch.dimension);
            if (level == null) {
                continue;
            }
            SiegePanelPayload panel = watch.phase == SiegePanelPayload.WAVES
                    ? new SiegePanelPayload(SiegePanelPayload.WAVES, watch.pos, watch.dimension, watch.started,
                            watch.wave, watch.waves, watch.left, -1, watch.douse, 100)
                    : new SiegePanelPayload(SiegePanelPayload.COUNTDOWN, watch.pos, watch.dimension,
                            SiegeClock.expectedStart(due, level.getGameTime(), level.getDayTime()), 0, watch.waves, 0,
                            -1, 0, 0);
            HearthNet.sendSiegePanel(player, panel);
        }
        for (String key : stale) {
            world.close(key);
        }
    }

    @Nullable
    private static ServerLevel levelOf(ServerPlayer player, String dimension) {
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    // ---- Who is where ---------------------------------------------------------------------------

    /** Survival or adventure, and a real player (not a machine's stand-in). The game mode is read, not {@code isCreative()}. */
    static boolean fighter(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer().isSurvival() && !SkillTools.isFakePlayer(player);
    }

    /** Not AFK: skill XP inside the survival streak's active window (its own notion of active). */
    static boolean active(ServerPlayer player, long now) {
        return ProficiencyAttachments.of(player).isActive(now, ProficiencyConfig.streakActiveWindowTicks());
    }

    /** Alive, not a spectator, in this level and inside the Hearth's cylinder. */
    static boolean inRange(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return player.level() == level && player.isAlive() && !player.isSpectator()
                && HearthRange.inRange(level, pos, player.position());
    }

    /** Survival and adventure players home at this Hearth, AFK or not. */
    static List<ServerPlayer> home(ServerLevel level, BlockPos pos, List<ServerPlayer> online) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : online) {
            if (fighter(player) && inRange(level, pos, player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Of those, the active ones: who counts at nightfall and for the wave size. */
    static List<ServerPlayer> homeActive(ServerLevel level, BlockPos pos, long now, List<ServerPlayer> online) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : home(level, pos, online)) {
            if (active(player, now)) {
                out.add(player);
            }
        }
        return out;
    }

    /** The active group: who a rolled siege concerns. */
    static List<ServerPlayer> group(List<ServerPlayer> online, long now) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : online) {
            if (fighter(player) && active(player, now)) {
                out.add(player);
            }
        }
        return out;
    }

    /** {home and warm, active group} for the countdown panel's "Warm n/m". */
    static int[] warmGroup(ServerLevel level, BlockPos pos, long now, List<ServerPlayer> online) {
        int warm = 0;
        int group = 0;
        for (ServerPlayer player : online) {
            if (!fighter(player) || !active(player, now)) {
                continue;
            }
            group++;
            if (inRange(level, pos, player) && WarmthMath.isFull(HearthAttachments.of(player).warmth())) {
                warm++;
            }
        }
        return new int[] {warm, group};
    }

    private static void addPresent(CompoundTag lane, ServerPlayer player) {
        ListTag present = lane.getList(K_PRESENT, Tag.TAG_INT_ARRAY);
        for (Tag tag : present) {
            if (NbtUtils.loadUUID(tag).equals(player.getUUID())) {
                return;
            }
        }
        present.add(NbtUtils.createUUID(player.getUUID()));
        lane.put(K_PRESENT, present);
        CompoundTag names = lane.getCompound(K_NAMES);
        names.putString(player.getUUID().toString(), player.getGameProfile().getName());
        lane.put(K_NAMES, names);
    }

    private static String nameOf(ServerLevel level, CompoundTag names, UUID id) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        String saved = names.getString(id.toString());
        return saved.isEmpty() ? id.toString().substring(0, 8) : saved;
    }

    @Nullable
    private static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** This siege's level: the horn's when one set it, else the ceiling's (III -> I .. VI -> IV). */
    static int siegeLevel(HearthState state, CompoundTag lane, HearthData data) {
        int set = lane.getInt(K_LEVEL);
        return set > 0 ? Math.min(SiegeRules.MAX_LEVEL, set) : Math.max(1, SiegeRules.levelForCeiling(state.ceiling(data)));
    }

    private static CompoundTag resetLane(HearthBlockEntity hearth) {
        CompoundTag lane = hearth.laneData(LANE);
        for (String key : new ArrayList<>(lane.getAllKeys())) {
            lane.remove(key);
        }
        return lane;
    }

    static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** Forgets the roll pacing (server stop). */
    static void forget() {
        ROLL_IN.clear();
    }
}
