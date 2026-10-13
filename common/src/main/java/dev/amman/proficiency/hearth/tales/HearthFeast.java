package dev.amman.proficiency.hearth.tales;

import dev.amman.proficiency.hearth.FeastWindow;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.hearth.net.HearthNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Feast (docs/HEARTH-SPEC.md, "Gathering"): two or more players eating in range within
 * {@code feast.windowSeconds} fill each other's Warmth to 100% and add {@code chargePerExtraEater} to
 * the Feast pool per extra eater, once per in-game day per Hearth. The window logic is
 * {@link FeastWindow}; this applies its outcome to the players and the Hearth.
 */
public final class HearthFeast {

    private HearthFeast() {
    }

    /** A player finished eating: at a lit Hearth the meal goes to that Hearth's Feast window. */
    public static void onEaten(ServerPlayer player, ItemStack food) {
        HearthBlockEntity home = HearthRange.homeEntity(player);
        if (home == null || home.state().smouldering()) {
            return;
        }
        eat(player, home);
    }

    /** The player when they are online, in this level and at home at this Hearth; else null. */
    private static ServerPlayer atTable(ServerLevel level, BlockPos hearthPos, UUID id) {
        ServerPlayer eater = level.getServer().getPlayerList().getPlayer(id);
        return eater != null && eater.level() == level && hearthPos.equals(HearthRange.homeOf(eater)) ? eater : null;
    }

    /** One meal at {@code home}; public to the lane so the tests can drive it without a client. */
    public static FeastWindow.Outcome eat(ServerPlayer player, HearthBlockEntity home) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long day = Math.floorDiv(level.getDayTime(), HearthData.TICKS_PER_DAY);
        HearthData data = Hearths.data();
        HearthState state = home.state();
        HearthAttachments.of(player).setLastMeal(now);
        BlockPos hearthPos = home.getBlockPos();
        // Someone who ate and has since walked away (or logged off) is not at the table. The window asks before it
        // spends the day, so a Feast of one is not a Feast and the day's one is still there for a real one.
        FeastWindow.Outcome outcome = state.feast().onEat(player.getUUID(), now, day, data,
                id -> atTable(level, hearthPos, id) != null);
        if (outcome.kind() == FeastWindow.Kind.NONE) {
            return outcome;
        }
        List<ServerPlayer> warmed = new ArrayList<>();
        for (UUID id : outcome.warmed()) {
            ServerPlayer eater = atTable(level, hearthPos, id);
            if (eater != null) {
                warmed.add(eater);
            }
        }
        HearthPool pool = data.feastPool();
        // The Feast's charge is capped like the mirrored stream and split evenly between the eaters it warmed;
        // HearthCharge.credit keeps the ledger (source feast), telemetry, dots and the tier watch.
        double each = warmed.isEmpty() ? 0.0 : outcome.charge() / warmed.size();
        double added = 0.0;
        for (ServerPlayer eater : warmed) {
            PlayerHearth hearth = HearthAttachments.of(eater);
            hearth.setWarmth(1.0);
            hearth.setLastFeast(now);
            HearthNet.markHudDirty(eater);
            added += HearthCharge.credit(level, home, eater, pool, HearthLedger.FEAST, each, true, null);
        }
        for (ServerPlayer eater : warmed) {
            TalesFx.warmed(eater);
            eater.sendSystemMessage(outcome.kind() == FeastWindow.Kind.STARTED
                    ? TalesText.feastStarted(warmed.size(), added, TalesText.poolName(pool))
                    : TalesText.feastJoined(added, TalesText.poolName(pool)));
        }
        home.setChanged();
        if (!warmed.isEmpty()) {
            HearthEvents.FEAST.fire(new HearthEvents.Feast(level, hearthPos, List.copyOf(warmed)));
        }
        return outcome;
    }
}
