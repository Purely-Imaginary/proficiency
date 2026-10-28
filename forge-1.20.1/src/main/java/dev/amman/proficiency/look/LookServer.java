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
 * answer). Every request gets a reply: a per-player token bucket ({@link LookPacing.Bucket}, a burst of 6 and
 * ten answers a second after that) stops a flood, and a request over it is answered with a tiny "retry" tag
 * instead of being dropped, so the client asks again at once rather than waiting out its patience. A modified
 * client still cannot use it to scan the world or flood the server (a retry reply is one small packet per request).
 */
public final class LookServer {

    static final double REACH_SQ = 16.0 * 16.0;

    private static final Map<UUID, LookPacing.Bucket> BUCKETS = new ConcurrentHashMap<>();

    private LookServer() {
    }

    /** Server thread. */
    public static void onRequest(ServerPlayer player, LookRequestPayload payload) {
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        ProficiencyNetwork.sendIfPresent(player, reply(player, payload));
    }

    /** The reply to one request: data, empty, or "retry" when the player is over the rate. Never null. */
    public static LookReplyPayload reply(ServerPlayer player, LookRequestPayload payload) {
        ServerLevel level = (ServerLevel) player.level();
        LookPacing.Bucket bucket = BUCKETS.computeIfAbsent(player.getUUID(), id -> new LookPacing.Bucket());
        if (!bucket.take(level.getGameTime())) {
            CompoundTag retry = new CompoundTag();
            retry.putBoolean(LookPacing.RETRY, true);
            return new LookReplyPayload(payload.pos(), retry);
        }
        BlockPos pos = BlockPos.of(payload.pos());
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQ
                || !level.isLoaded(pos)) {
            // Out of reach or not loaded: an empty answer, so the client does not keep a stale one on screen.
            return new LookReplyPayload(payload.pos(), new CompoundTag());
        }
        CompoundTag data = new CompoundTag();
        XpLookData.block(player, level, pos, data);
        return new LookReplyPayload(payload.pos(), data);
    }

    public static void forget(UUID player) {
        BUCKETS.remove(player);
    }
}
