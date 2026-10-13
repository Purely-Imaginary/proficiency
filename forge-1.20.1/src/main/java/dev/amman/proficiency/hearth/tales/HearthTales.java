package dev.amman.proficiency.hearth.tales;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.homebuilder.HomebuilderApi;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.ToDoubleFunction;

/**
 * The tales lane on Forge 1.20.1 (common's {@code HearthTales}; docs/HEARTH-FORGE-CONTRACT.md, lane
 * forge-extras): Tales, the Hearth Chronicle, the Feast and the context bell. Called once from
 * {@code Hearth.init}.
 *
 * <ul>
 * <li>{@link TalesBank}: Movement and Expedition XP earned away from home is banked (the
 * {@code GRANT} hook), paid into the Tales pool when the player comes home to a Hearth they fed (the
 * {@code PLAYER_SECOND} hook), lost on a death (the {@code DEATH} hook).</li>
 * <li>{@link HearthChronicle}: a book on a lectern in range; a new page is filed to
 * {@code <world>/proficiency/chronicle.jsonl} and pays the flat bonus; {@code /proficiency hearth
 * tale} pays the rating ({@link ChronicleCommand}).</li>
 * <li>{@link HearthFeast}: two or more players eating in range warm each other.</li>
 * <li>{@link HearthBell}: the stage-V bell, one call by the moment (alarm, dinner, come home).</li>
 * </ul>
 */
public final class HearthTales {

    private HearthTales() {
    }

    /**
     * The extra share of banked Tales a player's talents add: Homebuilder's Storyteller, +10% per rank
     * ({@link HomebuilderApi#storyteller}, 0 while the skill is off). A seam so a test can pin it.
     */
    public static volatile ToDoubleFunction<ServerPlayer> storyteller = HomebuilderApi::storyteller;

    public static void init() {
        HearthHooks.GRANT.add(TalesBank::onGrant);
        HearthHooks.PLAYER_SECOND.add(TalesBank::onSecond);
        HearthHooks.DEATH.add(TalesBank::onDeath);
        HearthHooks.ITEM_EATEN.add(HearthFeast::onEaten);
        HearthHooks.BLOCK_USE.add(HearthTales::onBlockUse);
        HearthHooks.HEARTH_TICK.add(HearthChronicle::onHearthSecond);
        MinecraftForge.EVENT_BUS.register(HearthTalesEvents.class);
    }

    /** A right click: a bell is noted (it counts once vanilla rings it), a lectern is registered with its Hearth. Never consumes the click. */
    private static InteractionResult onBlockUse(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
            InteractionHand hand, ItemStack held) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (state.is(Blocks.BELL)) {
            HearthBell.noteClick(player, level, pos);
        } else if (state.is(Blocks.LECTERN)) {
            HearthChronicle.onLecternUse(player, level, pos, state);
        }
        return InteractionResult.PASS;
    }

    /** Sends a tales payload when the client can take it (the one channel's check, in {@code HearthNet}). */
    static void send(ServerPlayer player, HearthTalesPayload payload) {
        HearthNet.sendTales(player, payload);
    }

    static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** The Hearth block entity at a packed position of this level, when loaded. */
    static HearthBlockEntity entityAt(ServerLevel level, long packed) {
        return dev.amman.proficiency.hearth.HearthRange.entity(level, BlockPos.of(packed));
    }
}
