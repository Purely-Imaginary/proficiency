package dev.amman.proficiency.event;

import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.compat.BlockDropsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A refund is a loan against a block that stays. Master Mason, the refund chances and the
 * Engineering procs hand the block back at placement; if the player could then pick the block up
 * and keep both, place and pick up would make a free block every cycle (and a free machine, for
 * Engineering). So a refunded block remembers how many items it was refunded, and whoever takes
 * the block back repays them:
 * <ul>
 *   <li>A normal break: the block's own drop is removed first, and any refund beyond that one item
 *       is taken from the breaker's inventory.</li>
 *   <li>A pick-up that fires no break event (a Create wrench, an AE2 wrench, any click-to-dismantle
 *       tool): the player's click is watched for one more tick, and if the block is gone the whole
 *       refund is taken from their inventory.</li>
 * </ul>
 * What the inventory cannot cover (the items were dropped or used) becomes a small debt that the
 * player's next refunds pay off, so there is no way to dodge it by emptying your pockets or by
 * logging out (a server restart does forgive it: the debt is memory only). A
 * refunded block destroyed some other way (an explosion, lava) repays nothing: it was lost, which
 * is what placing it would have cost anyway without the talent.
 */
public final class RefundGuard {

    /** Items still owed per player, as item to count. Memory only: a restart forgives it. */
    private static final Map<UUID, Map<Item, Integer>> DEBT = new ConcurrentHashMap<>();

    private static final int MAX_DEBT = 256;

    /** A click on a refunded block, checked on the player's next tick. */
    private record Watch(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, Item item, long due) {
    }

    private static final Map<UUID, List<Watch>> WATCHES = new ConcurrentHashMap<>();

    /**
     * Refunds waiting for the player's next tick. Forge posts the place event while the held stack
     * is temporarily reset to its size before the placement and puts the post-placement size back
     * afterwards, so a refund merged into the held stack from inside the event was silently wiped
     * (Master Mason refunded nothing in a real Forge client). Where {@link #deferPlacementRefunds}
     * is on, a refund given inside {@link #placing} waits here. Nothing is remembered about it
     * until it is handed over, so a break, a death or a crash before then can neither charge the
     * player for an item they never got nor leave them with a free one.
     */
    private record Pending(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                           BlockPos pos, BlockState state, int count) {
    }

    private static final Map<UUID, List<Pending>> LATER = new ConcurrentHashMap<>();

    /** The player whose placement handler is running on this thread, so only their refunds wait. */
    private static final ThreadLocal<UUID> PLACING = new ThreadLocal<>();

    /** On for Forge only: the other loaders hand the refund over at once and never lose it. */
    public static volatile boolean deferPlacementRefunds;

    private RefundGuard() {
    }

    /** Runs a placement handler; on Forge, this player's refunds it gives wait for their next tick. */
    public static void placing(ServerPlayer player, Runnable placement) {
        if (!deferPlacementRefunds) {
            placement.run();
            return;
        }
        UUID before = PLACING.get();
        PLACING.set(player.getUUID());
        try {
            placement.run();
        } finally {
            if (before == null) {
                PLACING.remove();
            } else {
                PLACING.set(before);
            }
        }
    }

    /**
     * Hands over the refunds held back by {@link #placing}. Runs on the player's tick and at
     * logout. A block that is no longer there (broken in the same tick) or a player who is no
     * longer alive gets nothing, and nothing is marked: the loop is simply not opened.
     */
    public static void flush(ServerPlayer player) {
        List<Pending> waiting = LATER.remove(player.getUUID());
        if (waiting == null) {
            return;
        }
        for (Pending refund : waiting) {
            ServerLevel level = player.server.getLevel(refund.dimension());
            if (!player.isAlive() || level == null || !level.getBlockState(refund.pos()).is(refund.state().getBlock())) {
                continue;
            }
            deliver(player, level, refund.state(), refund.pos(), refund.count());
        }
    }

    // ---- paying out ---------------------------------------------------------------------------

    /**
     * Gives a refund for a block just placed at {@code pos}, less whatever the player still owes,
     * and remembers it so taking the block back repays it. Falling blocks are not refunded: they
     * may not stay where they were placed, and the refund could not follow them.
     */
    public static void giveBack(ServerPlayer player, BlockState placed, BlockPos pos, int count) {
        Item item = placed.getBlock().asItem();
        if (!(item instanceof BlockItem) || placed.getBlock() instanceof net.minecraft.world.level.block.Fallable) {
            return;
        }
        if (count <= 0) {
            return;
        }
        if (PLACING.get() != null && PLACING.get().equals(player.getUUID())) {
            LATER.computeIfAbsent(player.getUUID(), id -> new ArrayList<>())
                    .add(new Pending(player.serverLevel().dimension(), pos.immutable(), placed, count));
            return;
        }
        deliver(player, player.serverLevel(), placed, pos, count);
    }

    /** Pays off debt first, hands over what is left and remembers it against the block. */
    private static void deliver(ServerPlayer player, ServerLevel level, BlockState placed, BlockPos pos, int count) {
        Item item = placed.getBlock().asItem();
        count -= payDebt(player.getUUID(), item, count);
        if (count <= 0) {
            return;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(item, count));
        PlacedBlocks.markRefunded(level, pos, placed, count);
    }

    private static int payDebt(UUID player, Item item, int refund) {
        Map<Item, Integer> owed = DEBT.get(player);
        if (owed == null) {
            return 0;
        }
        int paid = 0;
        synchronized (owed) {
            Integer debt = owed.get(item);
            if (debt != null) {
                paid = Math.min(debt, refund);
                if (paid >= debt) {
                    owed.remove(item);
                } else {
                    owed.put(item, debt - paid);
                }
            }
        }
        return paid;
    }

    // ---- taking back --------------------------------------------------------------------------

    /**
     * A refunded block is being broken by a player: its own drop goes, and the rest of the refund
     * comes out of their inventory. Called first in the drops handler.
     */
    public static void onDrops(BlockDropsEvent event, ServerPlayer player) {
        int owed = PlacedBlocks.consumeRefunded(event.getLevel(), event.getPos(), event.getState());
        if (owed <= 0) {
            return;
        }
        Item item = event.getState().getBlock().asItem();
        owed = removeOwnDrops(event, owed);
        if (owed > 0 && !dev.amman.proficiency.skill.SkillTools.isFakePlayer(player)) {
            repay(player, item, owed);
        }
    }

    /** Takes up to {@code owed} of the block's own item out of its drops; returns what is still owed. */
    private static int removeOwnDrops(BlockDropsEvent event, int owed) {
        Item item = event.getState().getBlock().asItem();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (owed > 0 && it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack stack = drop.getItem();
            if (stack.getItem() != item) {
                continue;
            }
            int take = Math.min(owed, stack.getCount());
            owed -= take;
            if (take >= stack.getCount()) {
                it.remove();
            } else {
                stack.shrink(take);
                drop.setItem(stack);
            }
        }
        return owed;
    }

    /**
     * A refunded block that goes with no player behind it (a torch or door popping off when its
     * support breaks, a flower washed away by water): its own drop is removed and the mark goes.
     * The player already holds the refund, so the loop is closed and nobody is charged.
     */
    public static void onDropsNoBreaker(BlockDropsEvent event) {
        int owed = PlacedBlocks.consumeRefunded(event.getLevel(), event.getPos(), event.getState());
        if (owed > 0) {
            removeOwnDrops(event, owed);
        }
    }

    /** Takes {@code count} of an item from the player's inventory; what is missing becomes debt. */
    static void repay(ServerPlayer player, Item item, int count) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPlain(stack, item)) {
                int take = Math.min(count, stack.getCount());
                stack.shrink(take);
                count -= take;
            }
        }
        if (count > 0) {
            Map<Item, Integer> owed = DEBT.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
            owed.merge(item, count, (a, b) -> Math.min(MAX_DEBT, a + b));
        }
    }

    /** A stack of exactly this item with nothing attached, so a filled shulker box is never taken. */
    private static boolean isPlain(ItemStack stack, Item item) {
        return stack.getItem() == item && ItemStack.isSameItemSameTags(stack, new ItemStack(item));
    }

    /** Items a player still owes for one item type, for tests. */
    public static int debt(UUID player, Item item) {
        Map<Item, Integer> owed = DEBT.get(player);
        return owed == null ? 0 : owed.getOrDefault(item, 0);
    }

    // ---- pick-ups that fire no break event ---------------------------------------------------

    /** A right click landed on this block: if it is a refunded one, look again next tick. */
    public static void watchClick(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (PlacedBlocks.refundedCount(level, pos, state) <= 0) {
            return;
        }
        List<Watch> list = WATCHES.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        synchronized (list) {
            if (list.size() < 16) {
                list.add(new Watch(level.dimension(), pos.immutable(), state.getBlock().asItem(), level.getGameTime() + 1));
            }
        }
    }

    /**
     * Runs on the player's tick. A watched block that is gone while its refunded mark is still
     * there was taken by something that never posted a break: the refund is taken back. A block
     * that was broken the ordinary way has already settled through {@link #onDrops} and cleared
     * its mark, so it is not charged twice.
     */
    public static void tick(ServerPlayer player) {
        flush(player);
        settle(player, false);
    }

    /**
     * Looks at the watched blocks that are due (all of them when {@code everything}), each in the
     * dimension it was clicked in, so changing dimension in between neither escapes the charge nor
     * charges a different block at the same coordinates.
     */
    private static void settle(ServerPlayer player, boolean everything) {
        List<Watch> list = WATCHES.get(player.getUUID());
        if (list == null) {
            return;
        }
        List<Watch> ready = new ArrayList<>();
        synchronized (list) {
            long now = player.serverLevel().getGameTime();
            for (Iterator<Watch> it = list.iterator(); it.hasNext();) {
                Watch watch = it.next();
                if (everything || watch.due() <= now) {
                    ready.add(watch);
                    it.remove();
                }
            }
        }
        for (Watch watch : ready) {
            ServerLevel level = player.server.getLevel(watch.dimension());
            if (level == null) {
                continue;
            }
            int owed = PlacedBlocks.rawRefundedCount(level, watch.pos());
            if (owed <= 0) {
                continue;
            }
            BlockState now = level.getBlockState(watch.pos());
            if (!now.isAir() && !(now.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                // Still a block there. If it changed type (stripped, tilled, waxed, scraped) it
                // stayed put: the refund simply rides on the new state.
                if (PlacedBlocks.refundedCount(level, watch.pos(), now) <= 0) {
                    PlacedBlocks.markRefunded(level, watch.pos(), now, owed);
                }
                continue;
            }
            PlacedBlocks.clearRefunded(level, watch.pos());
            repay(player, watch.item(), owed);
        }
    }

    /**
     * The player is leaving: look at every watched click now instead of dropping them, so a pick-up
     * followed by a disconnect is still repaid (as debt if the pockets cannot cover it). Debt is
     * kept on purpose: logging out must not wipe what you owe.
     */
    public static void forget(ServerPlayer player) {
        flush(player);
        settle(player, true);
        WATCHES.remove(player.getUUID());
    }
}
