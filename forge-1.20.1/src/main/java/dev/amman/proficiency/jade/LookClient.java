package dev.amman.proficiency.jade;

import dev.amman.proficiency.look.LookPacing;
import dev.amman.proficiency.net.LookReplyPayload;
import dev.amman.proficiency.net.LookRequestPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The client's one-block cache of the server's answer to "what would this block pay me" (Forge 1.20.1,
 * where Jade 11 cannot carry it). A new crosshair target, a new block state or a different held item (item
 * or NBT) drops the cache and asks at once (at most one question a tick); while you keep looking it asks again
 * every 10 ticks (20 after an empty answer), never more than one in flight, never to a server without the
 * mod's channel. A "retry" reply (the server's rate guard) is asked again the next tick. Touches no Jade
 * class, so the reply handler can run without Jade. The rules are {@link LookPacing}.
 */
public final class LookClient {

    private static long pos = Long.MIN_VALUE;
    private static BlockState state;
    private static ItemStack held = ItemStack.EMPTY;
    private static CompoundTag data;
    private static long askedAt = Long.MIN_VALUE;
    private static long answeredAt = Long.MIN_VALUE;
    private static long sentAt = Long.MIN_VALUE;
    /** Debug: the tick the current target first showed, and a one-line log when its data lands. */
    private static long targetSince = Long.MIN_VALUE;
    private static final boolean DEBUG = Boolean.getBoolean("proficiency.lookDebug");

    private LookClient() {
    }

    /** Client thread: the reply for the block we asked about. */
    public static void accept(LookReplyPayload payload) {
        if (payload.pos() != pos) {
            return;
        }
        if (payload.data().getBoolean(LookPacing.RETRY)) {
            // Throttled: forget the question so the next tick asks again.
            askedAt = Long.MIN_VALUE;
            return;
        }
        data = payload.data();
        answeredAt = now();
        if (DEBUG && targetSince != Long.MIN_VALUE && data.contains(dev.amman.proficiency.look.XpLookData.SKILL)) {
            dev.amman.proficiency.Proficiency.LOG.info("LOOKLAG target-to-line {} ticks", answeredAt - targetSince);
            targetSince = Long.MIN_VALUE;
        }
    }

    /**
     * The newest answer for this block, or an empty tag while none has come yet; sends a question when the
     * target or the held item changed, or the answer is missing or older than a refresh.
     */
    public static CompoundTag get(BlockPos at, BlockState current) {
        long now = now();
        long packed = at.asLong();
        ItemStack inHand = Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getMainHandItem();
        boolean changed = packed != pos || state != current || !ItemStack.isSameItemSameTags(held, inHand);
        if (changed) {
            pos = packed;
            state = current;
            held = inHand.copy();
            data = null;
            askedAt = Long.MIN_VALUE;
            answeredAt = Long.MIN_VALUE;
            targetSince = now;
        }
        boolean inFlight = askedAt != Long.MIN_VALUE && askedAt > answeredAt && now - askedAt < LookPacing.PATIENCE;
        if (LookPacing.shouldAsk(changed, data != null, data != null && data.isEmpty(), now, answeredAt, sentAt, inFlight)
                && canAsk()) {
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
