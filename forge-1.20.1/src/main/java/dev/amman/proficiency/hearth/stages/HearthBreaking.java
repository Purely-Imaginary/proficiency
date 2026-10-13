package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Putting a Hearth out (common's {@code hearth.stages.HearthBreaking}): a 3 s sneak-hold through the
 * normal block-break path, so claim mods protect it like any block.
 *
 * <p>The hold is the block's break speed. The client draws and finishes it with
 * {@link #destroyProgress(Player)}: 1/60 a tick while the sneak key is down. The server does not
 * trust the sneak key at the moment the break arrives: it counts, once a server tick, the ticks the
 * player really held sneak since they started on this Hearth ({@link #tickHolds}), and
 * {@link #destroyProgress(Player, BlockPos)} turns that count into the break speed vanilla checks.
 * A client that sneaks only for the last tick gets 1/60 of a hold, not a broken Hearth.
 *
 * <p>The foundation ported the holds, the server's break speed, the once-a-tick count and the stop
 * clear, and wires the tick and the stop ({@code HearthCommonEvents.onServerTick} /
 * {@code onServerStopping}); the forge-server lane added the action-bar hint on a swing without
 * sneaking and {@link #onBroken} (the feeders told, the hold dropped at once).
 */
public final class HearthBreaking {

    /** The hold, in ticks: 3 s. */
    public static final int HOLD_TICKS = 60;
    /** A hold nobody finished is dropped after 30 s. */
    static final int MAX_HOLD_TICKS = 600;
    /**
     * The sneaked ticks below which the server's break speed reads 0: vanilla's own tolerance for a
     * finished break (70% of the hold, {@code ServerPlayerGameMode}'s STOP check).
     */
    static final int MIN_SNEAKED = HOLD_TICKS * 7 / 10;

    /** One hold per player, server thread only. Empty unless someone is breaking a Hearth right now. */
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private HearthBreaking() {
    }

    /** One player's hold on one Hearth: ticks since they started, ticks of those they sneaked. */
    private static final class Hold {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        int elapsed;
        int sneaked;

        Hold(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        boolean at(Player player, BlockPos where) {
            return pos.equals(where) && dimension.equals(player.level().dimension());
        }
    }

    /** The client's rule: 1/60 a tick while sneaking, whatever the tool; 0 otherwise. */
    public static float destroyProgress(Player player) {
        return player.isShiftKeyDown() ? 1.0f / HOLD_TICKS : 0.0f;
    }

    /**
     * The block's break speed. On the client, {@link #destroyProgress(Player)}. On the server, the
     * sneak ticks counted since the hold started, spread over the ticks it has run: vanilla multiplies
     * this by the ticks since the start, so the product is the true share of the hold that was
     * sneaked (its 70% tolerance for lag included). No hold on this Hearth reads 0, and neither does a
     * hold with less than {@link #MIN_SNEAKED} ticks sneaked: after a refused STOP, vanilla's late
     * finish ({@code hasDelayedDestroy}) multiplies by the ticks since that older swing, so a new swing
     * with sneak would otherwise finish after a few ticks (a modified client: swing 3 s unsneaked, STOP,
     * swing again sneaking; found while porting to Forge, 2026-10-09).
     */
    public static float destroyProgress(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer server)) {
            return destroyProgress(player);
        }
        Hold hold = HOLDS.get(server.getUUID());
        if (hold == null || !hold.at(server, pos) || hold.sneaked < MIN_SNEAKED) {
            return 0.0f;
        }
        return hold.sneaked / ((float) HOLD_TICKS * (hold.elapsed + 1));
    }

    /**
     * A swing at the Hearth (the start of a break, server side): a new hold begins, and a swing
     * without sneaking says how it is done on the action bar.
     */
    public static void onAttack(ServerPlayer player, BlockPos pos) {
        HOLDS.put(player.getUUID(), new Hold(player.level().dimension(), pos.immutable()));
        // Creative breaks at once and never gets here; the abilities say so even for a mock player.
        if (!player.isShiftKeyDown() && !player.getAbilities().instabuild) {
            StageText.actionBar(player, StageText.sneakToBreak());
        }
    }

    /**
     * Once a server tick ({@code HearthCommonEvents.onServerTick}): each hold runs a tick, and counts it
     * if the player is sneaking. A hold ends when the Hearth or the player is gone, the player is dead
     * or elsewhere, or it ran 30 s.
     */
    public static void tickHolds(MinecraftServer server) {
        if (HOLDS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Hold> entry = it.next();
            Hold hold = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isRemoved() || !player.isAlive() || !hold.dimension.equals(player.level().dimension())
                    || ++hold.elapsed > MAX_HOLD_TICKS || !standing(player.serverLevel(), hold.pos)) {
                it.remove();
                continue;
            }
            if (player.isShiftKeyDown()) {
                hold.sneaked++;
            }
        }
    }

    private static boolean standing(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(HearthBlocks.HEARTH.get());
    }

    /** The server's count for this player's hold on {@code pos}: ticks sneaked, or -1 for no hold there. */
    public static int sneakTicks(ServerPlayer player, BlockPos pos) {
        Hold hold = HOLDS.get(player.getUUID());
        return hold != null && hold.at(player, pos) ? hold.sneaked : -1;
    }

    /** The server stopped: no hold outlives it (an integrated server starts again in the same JVM). */
    public static void forgetAll() {
        HOLDS.clear();
    }

    /** {@code BLOCK_BROKEN} on a Hearth: the break went through, the block is still there. */
    static void onBroken(ServerLevel level, BlockPos pos, ServerPlayer breaker) {
        HOLDS.remove(breaker.getUUID());
        if (level.getBlockEntity(pos) instanceof HearthBlockEntity hearth) {
            int stage = HearthStageCheck.shownStage(hearth);
            Set<UUID> feeders = HearthOfferings.feeders(hearth);
            Component told = StageText.putOut(breaker, pos, stage);
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player != breaker && feeders.contains(player.getUUID())) {
                    StageText.chat(player, told);
                }
            }
            StageText.chat(breaker, StageText.putOutSelf());
        }
        HearthPresence.clear(level, pos);
        StageFx.putOut(level, pos);
    }
}
