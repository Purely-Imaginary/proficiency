package dev.amman.proficiency.look;

import dev.amman.proficiency.net.LookReplyPayload;
import dev.amman.proficiency.net.LookRequestPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers the Jade plugin's block question on Forge 1.20.1 (see {@link LookRequestPayload}). A request is
 * answered with data only for a loaded block within 16 blocks of the player (anything else gets an empty
 * answer), and at most once every 4 ticks per player (a faster one is dropped), so a modified client cannot use it to scan the world
 * or flood the server.
 */
public final class LookServer {

    static final double REACH_SQ = 16.0 * 16.0;
    static final long MIN_GAP_TICKS = 4;

    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private LookServer() {
    }

    /** Server thread. */
    public static void onRequest(ServerPlayer player, LookRequestPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now >= last && now - last < MIN_GAP_TICKS) {
            // A flood: dropped without an answer, and without moving the clock, so it cannot starve the next one.
            return;
        }
        LAST.put(player.getUUID(), now);
        BlockPos pos = BlockPos.of(payload.pos());
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQ
                || !level.isLoaded(pos)) {
            // Out of reach or not loaded: an empty answer, so the client does not keep a stale one on screen.
            ProficiencyNetwork.sendIfPresent(player, new LookReplyPayload(payload.pos(), new CompoundTag()));
            return;
        }
        CompoundTag data = new CompoundTag();
        XpLookData.block(player, level, pos, data);
        ProficiencyNetwork.sendIfPresent(player, new LookReplyPayload(payload.pos(), data));
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
