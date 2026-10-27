package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.export.ExportHub;
import dev.amman.proficiency.export.HearthRow;
import dev.amman.proficiency.export.OfflinePlayers;
import dev.amman.proficiency.export.PlayerRow;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthAutoBuild;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader-side glue for the dashboard export (core's {@code export} package): every
 * {@code export.intervalSeconds} from the once-a-second tick, and at a logout, it writes
 * {@code <world>/proficiency/export/players.json} for the home dashboard. It only reads; nothing here
 * changes a player or a Hearth, and it sends no packet.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1 keeps a hand copy).
 */
public final class ExportService {

    /** More Hearths than this are left out of one file, so a world full of them cannot grow it without bound. */
    static final int MAX_HEARTHS = 200;

    /** Block probes for a Hearth's lane per file; the rest keep the lane found on an earlier file. */
    static final int MAX_PROBES = 12;
    /** Last lane per Hearth, so the probe budget spreads over several files. Touched on the server thread only. */
    private static final java.util.Map<String, String> LANES = new java.util.HashMap<>();

    /** A GameTest points the export at a temporary folder, so it never touches the world's own file. */
    public static volatile Path folderForTest;
    /** A GameTest points the offline scan at a temporary playerdata folder. */
    public static volatile Path playerdataForTest;

    /** Offline players found in {@code playerdata}; remembers each file's mtime so a write re-reads only changed files. */
    private static final OfflinePlayers OFFLINE = new OfflinePlayers();

    private ExportService() {
    }

    /** Once a second, after the players were ticked. */
    public static void afterPlayers(MinecraftServer server) {
        if (!ProficiencyConfig.exportEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = ProficiencyConfig.exportIntervalSeconds() * 1000L;
        Path folder = folder(server);
        // Reopen when the folder is another world's (a single-player JVM keeps its statics between worlds).
        if (!ExportHub.runningOn(folder)) {
            ExportHub.start(folder, server.getWorldData().getLevelName(), now, interval);
        }
        ExportHub.flushDeaths();
        if (ExportHub.due(now, interval)) {
            writeNow(server);
        }
    }

    /** Writes the file now with every online player and every Hearth (the interval's job; a test calls it). */
    public static void writeNow(MinecraftServer server) {
        long now = System.currentTimeMillis();
        ExportHub.mergeOffline(offline(server));
        ExportHub.write(now, online(server, now, null), hearths(server));
    }

    /**
     * Everyone with Proficiency data in the world's player files, offline rows. Parsing is cached by file time and
     * size, so a write after the first costs one directory listing; the profile cache gives the names.
     */
    private static List<PlayerRow> offline(MinecraftServer server) {
        Path dir = playerdataForTest;
        if (dir == null) {
            dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        }
        return OFFLINE.scan(dir, id -> {
            var cache = server.getProfileCache();
            if (cache == null) {
                return null;
            }
            return cache.get(id).map(profile -> profile.getName()).orElse(null);
        });
    }

    /** A player logged out: their last snapshot is kept (offline) and the file is written. */
    public static void left(ServerPlayer player) {
        if (!ProficiencyConfig.exportEnabled() || !ExportHub.running()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !ExportHub.runningOn(folder(server))) {
            return;
        }
        long now = System.currentTimeMillis();
        ExportHub.mergeOffline(offline(server));
        ExportHub.left(now, row(player, now), online(server, now, player));
    }

    /** A death, counted for "deaths this week". */
    public static void death(ServerPlayer player) {
        if (ProficiencyConfig.exportEnabled()) {
            ExportHub.death(player.getUUID(), System.currentTimeMillis());
        }
    }

    /** The server is going down: every player still online is saved as offline, then the session closes. */
    public static void stop(MinecraftServer server) {
        if (ExportHub.running() && ExportHub.runningOn(folder(server))) {
            long now = System.currentTimeMillis();
            ExportHub.leftAll(now, online(server, now, null));
        }
        ExportHub.stop();
    }

    /** {@code <world>/proficiency/export}. */
    public static Path folder(MinecraftServer server) {
        Path forced = folderForTest;
        if (forced != null) {
            return forced;
        }
        return server.getWorldPath(LevelResource.ROOT).resolve("proficiency").resolve("export");
    }

    private static PlayerRow row(ServerPlayer player, long now) {
        return PlayerRow.of(player.getUUID(), player.getGameProfile().getName(), true, now,
                ProficiencyAttachments.of(player), ExportHub.deathsWeek(player.getUUID(), now));
    }

    private static List<PlayerRow> online(MinecraftServer server, long now, ServerPlayer except) {
        List<PlayerRow> rows = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != except) {
                rows.add(row(player, now));
            }
        }
        return rows;
    }

    private static List<HearthRow> hearths(MinecraftServer server) {
        List<HearthRow> rows = new ArrayList<>();
        int probes = 0;
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            HearthIndexData index = HearthIndexData.get(level);
            // A copy: HearthRange.entity can forget a broken Hearth, which edits the set being walked.
            for (long packed : index.store().all().toLongArray()) {
                if (rows.size() >= MAX_HEARTHS) {
                    break;
                }
                BlockPos pos = BlockPos.of(packed);
                int radius = (int) Math.round(index.radius(pos));
                HearthBlockEntity hearth = HearthRange.entity(level, pos);
                if (hearth == null) {
                    rows.add(HearthRow.unloaded(dimension, pos.getX(), pos.getY(), pos.getZ(), radius));
                    continue;
                }
                HearthStatePayload.Build build = HearthAutoBuild.status(hearth);
                String key = dimension + "@" + packed;
                String lane = HearthAutoBuild.markedLane(hearth);
                if (lane != null) {
                    LANES.put(key, lane);
                } else if (probes < MAX_PROBES) {
                    // Not marked ON: the lane is found by probing blocks, so only a few Hearths per file do it.
                    probes++;
                    lane = HearthAutoBuild.laneId(level, pos, hearth);
                    LANES.put(key, lane);
                } else {
                    lane = LANES.getOrDefault(key, "");
                }
                rows.add(HearthRow.of(dimension, pos.getX(), pos.getY(), pos.getZ(), hearth.state(), Hearths.data(),
                        lane, radius, build.on(), build.stage(), build.done(),
                        build.total()));
            }
        }
        return rows;
    }
}
