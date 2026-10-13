package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Who is home", written once a second by each Hearth (docs/HEARTH-SPEC.md, "Warmth": the Hearth's
 * tick only writes {@code inRangeOf = pos, until = gameTime + 40} on each player it sees; the player
 * loop owns everything that follows from it). A player who walks in gets the Hearth's state
 * ({@link HearthStatePayload#ENTER}) once, on the edge, and so does a player who logs in or respawns at
 * home ({@link #joined}).
 */
public final class HearthPresence {

    /**
     * Players who joined at a Hearth whose chunk was not loaded yet ({@link #joined}): the Hearth's first tick
     * sends them its state. Emptied on logout and when the server stops.
     */
    private static final Set<UUID> OWED = new HashSet<>();

    private HearthPresence() {
    }

    /** Whether this player counts as standing at a Hearth: alive and not a spectator. */
    static boolean counts(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator();
    }

    /** The players inside the Hearth's cylinder right now (alive, not spectators). */
    public static List<ServerPlayer> inRange(ServerLevel level, BlockPos hearth) {
        List<ServerPlayer> out = new ArrayList<>(2);
        for (ServerPlayer player : level.players()) {
            if (counts(player) && HearthRange.homeIs(level, hearth, player.position())) {
                out.add(player);
            }
        }
        return out;
    }

    /** The once-a-second body: flag everyone in the cylinder, and send the state to who just came in. */
    static void tick(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        long now = level.getGameTime();
        String dimension = level.dimension().location().toString();
        long packed = hearth.packedPos();
        for (ServerPlayer player : level.players()) {
            if (!counts(player) || !HearthRange.homeIs(level, pos, player.position())) {
                continue;
            }
            PlayerHearth data = HearthAttachments.of(player);
            boolean already = data.inRange(dimension, now) && data.inRangePos() == packed;
            data.seenBy(packed, dimension, now);
            boolean owed = !OWED.isEmpty() && OWED.remove(player.getUUID());
            if (!already || owed) {
                HearthNet.sendState(player, HearthNet.stateOf(hearth, HearthStatePayload.ENTER));
            }
        }
    }

    /**
     * A player joined the world (a login, or a respawn with a new player object): one who joins at a Hearth gets
     * its state now. The in-range flag is saved with the player, so a relog at home is no arrival (it stays
     * silent, and {@link #tick} sends nothing on it), but the new client has no state yet: the tracker showed no
     * Comfort and Character lines and no "needs" until something else sent one (polish pass, Forge 1.20.1 real
     * client, 2026-10-10). If that Hearth's chunk is not loaded yet the state is owed and its first tick sends it;
     * a player whose flag ran out is an arrival for the next tick anyway.
     */
    public static void joined(ServerPlayer player) {
        UUID id = player.getUUID();
        OWED.remove(id);
        ServerLevel level = player.serverLevel();
        PlayerHearth data = HearthAttachments.of(player);
        if (!counts(player) || !data.inRange(level.dimension().location().toString(), level.getGameTime())) {
            return;
        }
        BlockPos pos = BlockPos.of(data.inRangePos());
        HearthBlockEntity hearth = HearthRange.entity(level, pos);
        if (hearth == null) {
            if (level.isLoaded(pos)) {
                return;
            }
            OWED.add(id);
        } else if (HearthRange.inRange(level, pos, player.position())) {
            HearthNet.sendState(player, HearthNet.stateOf(hearth, HearthStatePayload.ENTER));
        }
    }

    /** The player left: nothing is owed to them any more. */
    public static void forget(UUID player) {
        OWED.remove(player);
    }

    /** The server stops. */
    public static void forgetAll() {
        OWED.clear();
    }

    /** The Hearth at {@code pos} is gone: whoever it had flagged is not home any more, at once. */
    static void clear(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        long packed = pos.asLong();
        long now = level.getGameTime();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PlayerHearth data = HearthAttachments.of(player);
            if (data.inRangePos() == packed && dimension.equals(data.inRangeDim()) && data.inRangeUntil() >= now) {
                data.clearInRange();
            }
        }
    }

    /** Sends the Hearth's state to everyone in range ({@link HearthStatePayload#UPDATE}): a stage changed. */
    static void update(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        List<ServerPlayer> players = inRange(level, pos);
        if (players.isEmpty()) {
            return;
        }
        HearthStatePayload state = HearthNet.stateOf(hearth, HearthStatePayload.UPDATE);
        for (ServerPlayer player : players) {
            HearthNet.sendState(player, state);
        }
    }
}
