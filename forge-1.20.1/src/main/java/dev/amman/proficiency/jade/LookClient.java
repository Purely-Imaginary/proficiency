package dev.amman.proficiency.jade;

import dev.amman.proficiency.net.LookReplyPayload;
import dev.amman.proficiency.net.LookRequestPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The client's one-block cache of the server's answer to "what would this block pay me" (Forge 1.20.1,
 * where Jade 11 cannot carry it). It asks again every 10 ticks while you keep looking (every 20 after an
 * empty answer), never more than one question in flight, never faster than every 5 ticks, never to a server
 * without the mod's channel, and drops the answer the moment the crosshair, the world or the block changes.
 * Touches no Jade class, so the reply handler can run without Jade.
 */
public final class LookClient {

    private static final long REFRESH_TICKS = 10;
    /** An empty answer (dirt for a stick) is asked again only this often. */
    private static final long QUIET_TICKS = 20;
    private static final long PATIENCE_TICKS = 40;
    /** The least gap between two questions, so the server's own per-player limit never drops one. */
    static final long MIN_GAP_TICKS = 5;

    private static long pos = Long.MIN_VALUE;
    private static BlockState state;
    private static CompoundTag data;
    private static long askedAt = Long.MIN_VALUE;
    private static long answeredAt = Long.MIN_VALUE;
    private static long sentAt = Long.MIN_VALUE;

    private LookClient() {
    }

    /** Client thread: the reply for the block we asked about. */
    public static void accept(LookReplyPayload payload) {
        if (payload.pos() == pos) {
            data = payload.data();
            answeredAt = now();
        }
    }

    /**
     * The newest answer for this block, or an empty tag while none has come yet; sends a question when the
     * answer is missing or older than a refresh.
     */
    public static CompoundTag get(BlockPos at, BlockState current) {
        long now = now();
        long packed = at.asLong();
        if (packed != pos || state != current) {
            pos = packed;
            state = current;
            data = null;
            askedAt = Long.MIN_VALUE;
            answeredAt = Long.MIN_VALUE;
        }
        long refresh = data != null && data.isEmpty() ? QUIET_TICKS : REFRESH_TICKS;
        boolean stale = data == null || now - answeredAt >= refresh;
        boolean inFlight = askedAt > answeredAt && now - askedAt < PATIENCE_TICKS;
        boolean spaced = sentAt == Long.MIN_VALUE || now < sentAt || now - sentAt >= MIN_GAP_TICKS;
        if (stale && !inFlight && spaced && canAsk()) {
            askedAt = now;
            sentAt = now;
            ProficiencyNetwork.sendToServer(new LookRequestPayload(packed));
        }
        return data == null ? new CompoundTag() : data;
    }

    /** Only a server that has the mod's channel is asked: a vanilla or foreign server would get an unknown packet. */
    private static boolean canAsk() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && ProficiencyNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    private static long now() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }
}
