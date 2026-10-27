package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The stages lane's start: registers its Hearth listeners (docs/HEARTH-CONTRACT.md, lane "stages").
 * Owns: lighting, the structure check, stages and offerings, spacing, breaking, the in-range tick body
 * (PlayerHearth.seenBy), hints and the discovery banner.
 * Called once from {@code Hearth.init}.
 *
 * <p>With {@code hearth.enabled = false} every hook below is silent: no lighting, offerings,
 * panel, flags or hints, and an existing Hearth sits as it is. Breaking it is the exception: it still
 * takes the sneak-hold and drops a campfire (block properties; the hold's server tick runs
 * regardless), and putting it out still tells its feeders and plays its effect, since their charge
 * and offerings go with it whatever the switch says.
 */
public final class HearthStages {

    private HearthStages() {
    }

    public static void init() {
        HearthHooks.BLOCK_USE.add(HearthOfferings::onUse);
        HearthHooks.BLOCK_PLACED.add(HearthStages::onPlaced);
        HearthHooks.BLOCK_BROKEN.add(HearthStages::onBroken);
        HearthHooks.HEARTH_TICK.add(HearthStages::onSecond);
        HearthHooks.REQUEST.add(HearthStages::onRequest);
        // The sneak-hold is a block property, not a hook: its server-side count runs whatever hearth.enabled says.
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, HearthBreaking::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> HearthBreaking.forgetAll());
        // So is putting it out: the feeders lose its charge and offerings either way, so they are told
        // either way. LOWEST and not for canceled breaks, like the BLOCK_BROKEN hook: a claim mod has had its say.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, HearthStages::onBreak);
    }

    /** A break that went through: a Hearth is put out (whatever {@code hearth.enabled} says). */
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer player
                && event.getState().is(HearthBlocks.HEARTH.get())) {
            HearthBreaking.onBroken(level, event.getPos(), player);
        }
    }

    /** Once a second per Hearth: who is home, then the build. */
    static void onSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        HearthPresence.tick(level, pos, hearth);
        HearthStageCheck.tick(level, pos, hearth);
        HearthAutoBuild.tick(level, pos, hearth);
    }

    static void onPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity placer) {
        HearthStageCheck.touched(level, pos);
        if (placed.is(Blocks.CAMPFIRE) && placer instanceof ServerPlayer player) {
            hint(level, pos, player);
        }
    }

    /** A block in a Hearth's box is going: the build is re-read. The Hearth itself is {@link #onBreak}'s. */
    static void onBroken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!state.is(HearthBlocks.HEARTH.get())) {
            HearthStageCheck.touched(level, pos);
        }
    }

    /**
     * The first campfire a player sets on a full 3x3 of hearth stone: "Light it with flint and steel to
     * make a Hearth", once. Too close to another Hearth it says so instead, and keeps the hint for later.
     */
    static void hint(ServerLevel level, BlockPos pos, ServerPlayer player) {
        PlayerHearth data = HearthAttachments.of(player);
        if (data.flag(HearthOfferings.HINT_SHOWN)) {
            return;
        }
        HearthData hearthData = Hearths.data();
        if (HearthStructure.layer(StageBlocks.probe(level, pos, hearthData), 1) != null) {
            return;
        }
        BlockPos other = HearthOfferings.tooClose(level, pos);
        if (other != null) {
            StageText.actionBar(player, StageText.tooClose(HearthOfferings.distance(pos, other),
                    dev.amman.proficiency.config.ProficiencyConfig.hearthMinSpacing()));
            return;
        }
        data.setFlag(HearthOfferings.HINT_SHOWN, true);
        Item offering = StageBlocks.offeringItem(hearthData, 1);
        StageText.actionBar(player, StageText.hint(StageBlocks.offeringName(hearthData, 1), offering == Items.FLINT_AND_STEEL));
    }

    /** {@code STATE}: a fresh state for the panel, for a player in range of that Hearth (or an op). */
    static void onRequest(ServerPlayer player, HearthRequestPayload request) {
        int what = request.what();
        if (what != HearthRequestPayload.STATE && what != HearthRequestPayload.BUILD_START
                && what != HearthRequestPayload.BUILD_STOP && what != HearthRequestPayload.BUILD_PLAN
                && what != HearthRequestPayload.BUILD_CHOOSE) {
            return;
        }
        BlockPos at = BlockPos.of(request.pos());
        boolean allowed = at.equals(HearthRange.homeOf(player)) || player.hasPermissions(2);
        if (!allowed) {
            return;
        }
        if (what == HearthRequestPayload.BUILD_START) {
            HearthAutoBuild.start(player, at, request.arg());
            return;
        }
        if (what == HearthRequestPayload.BUILD_STOP) {
            HearthAutoBuild.stop(player, at);
            return;
        }
        if (what == HearthRequestPayload.BUILD_PLAN) {
            HearthAutoBuild.plan(player, at, request.arg());
            return;
        }
        if (what == HearthRequestPayload.BUILD_CHOOSE) {
            HearthAutoBuild.choose(player, at, (char) request.arg(), request.text() == null ? "" : request.text());
            return;
        }
        HearthBlockEntity hearth = HearthRange.entity(player.serverLevel(), at);
        if (hearth != null) {
            HearthNet.sendState(player, HearthNet.stateOf(hearth, HearthStatePayload.UPDATE));
        }
    }
}
