package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.HoldQueue;
import dev.amman.proficiency.skill.KillXp;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The grant hook body (docs/HEARTH-SPEC.md, "Charge: what feeds what"), the Masonry and Decorating
 * hold queue's way in, and the combat filter.
 *
 * <ul>
 * <li>A paid grant to a player who is home (in range of a Hearth that is not smouldering) mirrors
 * {@code beforeRested x mirrorShare} into the pool the data maps its skill to, through the rolling
 * hourly cap ({@link ChargeRules#route} says which grants count).</li>
 * <li>A pool with a hold (Masonry, Decorating) does not get it at once. The placement handler pays
 * the XP before the Hearth's placement hook runs, so the share waits here for that hook in the same
 * tick, which names the block's position; it then waits in the Hearth's hold queue, saved with the
 * block entity, and pays only if the block still stands when it comes due. A build share with no
 * placement behind it (a pack rule that pays Masonry for a craft) holds nothing and pays nothing.</li>
 * <li>Breaking a block drops what waits on its position.</li>
 * <li>A kill feeds {@code combatShare} of its XP to every pool, only for a siege mob killed in the
 * range of this Hearth while it is under siege, or a member of an active raid in range
 * ({@link ChargeEvents}).</li>
 * </ul>
 */
final class ChargeGrants {

    /** One share waiting for its placement. */
    private record Share(HearthPool pool, double amount, String source) {
    }

    /** The shares one player's placement paid this tick, and the Hearth they were home at. */
    private static final class Pending {
        final long tick;
        final String dimension;
        final long home;
        final List<Share> shares = new ArrayList<>(2);

        Pending(long tick, String dimension, long home) {
            this.tick = tick;
            this.dimension = dimension;
            this.home = home;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private ChargeGrants() {
    }

    static void forget(UUID player) {
        PENDING.remove(player);
    }

    /** The {@code GRANT} listener. */
    static void onGrant(HearthHooks.Grant grant) {
        ServerPlayer player = grant.player();
        if (SkillTools.isFakePlayer(player)) {
            // The grant path never pays a machine; this keeps it so if that ever changes.
            return;
        }
        ChargeRules.Route route = ChargeRules.route(grant.skill(), grant.source(), grant.play(), grant.firstTime());
        if (route == ChargeRules.Route.NONE) {
            return;
        }
        BlockPos home = HearthRange.homeOf(player);
        if (home == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        HearthBlockEntity hearth = HearthRange.entity(level, home);
        if (hearth == null || hearth.state().smouldering()) {
            return;
        }
        HearthData data = Hearths.data();
        if (route == ChargeRules.Route.KILL) {
            kill(grant, level, home, hearth, data);
            return;
        }
        HearthPool pool = ChargeRules.pool(grant.skill(), data);
        if (pool == null) {
            return;
        }
        double amount = ChargeRules.mirror(grant.beforeRested(), data);
        if (amount <= 0) {
            return;
        }
        HearthData.PoolSpec spec = data.pool(pool);
        if (spec != null && spec.holdSeconds() > 0) {
            waitForPlacement(player, level, home, pool, amount, grant.skill().id());
            return;
        }
        HearthCharge.credit(level, hearth, player, pool, grant.skill().id(), amount, true, grant.skill());
    }

    private static void kill(HearthHooks.Grant grant, ServerLevel level, BlockPos home, HearthBlockEntity hearth,
            HearthData data) {
        String type = grant.source().substring(KillXp.KILL_PREFIX.length());
        ChargeEvents.Kill kill = ChargeEvents.current(type, level.getGameTime());
        if (kill == null) {
            return;
        }
        // A siege mob feeds only the Hearth under siege it died at; a raider only as a raid's member.
        boolean feeds = kill.siegeHearth() == home.asLong()
                || ChargeRules.raidFeeds(kill.raid(), HearthRange.inRange(level, home, kill.where()),
                        kill.raidCentre() != null && HearthRange.inRange(level, home, kill.raidCentre()));
        if (!feeds) {
            return;
        }
        double share = ChargeRules.combat(grant.beforeRested(), data);
        if (share <= 0) {
            return;
        }
        for (HearthPool pool : HearthPool.VALUES) {
            HearthCharge.credit(level, hearth, grant.player(), pool, ChargeRules.COMBAT_SOURCE, share, true,
                    grant.skill());
        }
    }

    private static void waitForPlacement(ServerPlayer player, ServerLevel level, BlockPos home, HearthPool pool,
            double amount, String source) {
        long now = level.getGameTime();
        String dimension = level.dimension().location().toString();
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || pending.tick != now || pending.home != home.asLong()
                || !pending.dimension.equals(dimension)) {
            pending = new Pending(now, dimension, home.asLong());
            PENDING.put(player.getUUID(), pending);
        }
        pending.shares.add(new Share(pool, amount, source));
    }

    /** The {@code BLOCK_PLACED} listener: the shares this placement paid start their hold at its position. */
    static void onPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity placer) {
        if (!(placer instanceof ServerPlayer player)) {
            return;
        }
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || pending.tick != level.getGameTime()
                || !pending.dimension.equals(level.dimension().location().toString())) {
            return;
        }
        HearthBlockEntity hearth = HearthRange.entity(level, BlockPos.of(pending.home));
        if (hearth == null) {
            return;
        }
        HoldQueue holds = hearth.state().holds();
        HearthData data = Hearths.data();
        String block = ChargeTick.blockId(placed);
        String name = player.getGameProfile().getName();
        long now = level.getGameTime();
        for (Share share : pending.shares) {
            if (holds.size() >= ChargeRules.MAX_HOLDS) {
                break;
            }
            HearthData.PoolSpec spec = data.pool(share.pool());
            holds.add(pos.asLong(), new HoldQueue.Held(player.getUUID(), name, share.pool(), share.source(), block,
                    share.amount(), ChargeRules.holdDue(now, spec == null ? 0 : spec.holdSeconds())));
        }
        hearth.setChanged();
    }

    /** The {@code BLOCK_BROKEN} listener: whatever waits on this position is dropped. */
    static void onBroken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        long key = pos.asLong();
        HearthBlockEntity at = HearthRange.hearthEntityAt(level, pos);
        drop(at, key);
        HearthBlockEntity home = HearthRange.homeEntity(player);
        if (home != null && home != at) {
            drop(home, key);
        }
    }

    private static void drop(@Nullable HearthBlockEntity hearth, long pos) {
        if (hearth != null && !hearth.state().holds().drop(pos).isEmpty()) {
            hearth.setChanged();
        }
    }
}
