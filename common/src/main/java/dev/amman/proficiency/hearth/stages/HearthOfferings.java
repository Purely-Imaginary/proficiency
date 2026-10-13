package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthStructure.Check;
import dev.amman.proficiency.hearth.stages.HearthStructure.Problem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Lighting and offerings (docs/HEARTH-SPEC.md, "Building and upgrading"). Both are a right click
 * through {@code HearthHooks.BLOCK_USE}:
 *
 * <ul>
 * <li><b>Lighting</b>: stage I's offering (flint and steel) on a plain campfire that stands on a full
 * 3x3 of hearth stone turns it into the Hearth, stage I, if no other Hearth burns within
 * {@code hearth.minSpacing} blocks horizontally in this dimension (a refusal names the distance).</li>
 * <li><b>Offerings</b>: the next stage's item, right-clicked on the Hearth, is taken only when every
 * layer up to that stage stands and the Hearth has the Character that stage needs; otherwise the
 * first problem is named and the item stays. A stage offered once is never owed again.</li>
 * <li>An empty hand opens the panel ({@link HearthStatePayload#PANEL}); the panel asks for the ledger
 * itself as it opens ({@code REQUEST LEDGER}, answered by the charge lane), so it is not sent here
 * too. Sneaking, other items, and flint and steel on a smouldering Hearth (the Sieges lane relights
 * it) pass.</li>
 * </ul>
 *
 * <p>Claims: the hook runs after every other mod's right-click handler (LOWEST on NeoForge, a late
 * {@code UseBlockCallback} phase on Fabric), so a claim mod that refuses the click (FTB Chunks for a
 * non-member) has done so before anything here runs. On top of that, nothing is lit or taken where
 * the player may not build or interact ({@code mayBuild}, {@code mayInteract}: adventure mode, spawn
 * protection, the world border).
 */
public final class HearthOfferings {

    /** Player flags (PlayerHearth): the campfire hint was shown; this player has lit a Hearth before. */
    static final String HINT_SHOWN = "stages.hint";
    static final String FIRST_LIT = "stages.firstLit";

    /** Sends the panel's state ({@code PANEL}); a GameTest swaps it to see the panel open. */
    public static BiConsumer<ServerPlayer, HearthStatePayload> panelSender = HearthNet::sendState;

    private HearthOfferings() {
    }

    /** The {@code BLOCK_USE} listener. */
    static InteractionResult onUse(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
            InteractionHand hand, ItemStack held) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (state.is(Blocks.CAMPFIRE)) {
            if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive()) {
                return campfirePanel(player, level, pos);
            }
            return light(player, level, pos, state, hand, held);
        }
        if (!state.is(HearthBlocks.HEARTH.get()) || player.isSecondaryUseActive() || hand != InteractionHand.MAIN_HAND
                || !(level.getBlockEntity(pos) instanceof HearthBlockEntity hearth)) {
            return InteractionResult.PASS;
        }
        HearthData data = Hearths.data();
        int offered = StageBlocks.offeringStage(data, held);
        if (offered == 0) {
            if (!held.isEmpty()) {
                return InteractionResult.PASS;
            }
            panelSender.accept(player, HearthNet.stateOf(hearth, HearthStatePayload.PANEL));
            return InteractionResult.SUCCESS;
        }
        if (offered == 1 && hearth.state().smouldering()) {
            // Relighting a smouldering Hearth is the Sieges lane's.
            return InteractionResult.PASS;
        }
        return offer(player, level, pos, hearth, hand, held, offered, data);
    }

    /**
     * An empty hand on a plain campfire: the Hearth panel at stage 0, so the build guide shows stage I before
     * anything is lit. A campfire too close to another Hearth says so in the chat as lighting would.
     */
    private static InteractionResult campfirePanel(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos other = tooClose(level, pos);
        if (other != null) {
            StageText.chat(player, StageText.tooClose(distance(pos, other), ProficiencyConfig.hearthMinSpacing()));
        }
        panelSender.accept(player, HearthNet.campfireState(level, pos));
        return InteractionResult.SUCCESS;
    }

    /** Where this player may change blocks: survival or creative, outside spawn protection and the border. */
    static boolean mayChange(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.mayBuild() && level.mayInteract(player, pos);
    }

    /**
     * The server's six offerings as this client last heard them (the state payload's {@code offerings},
     * sent on entering a Hearth's range), or null before any came. A dedicated server's datapack is not
     * loaded on its clients, so {@link Hearths#data()} there holds the shipped defaults.
     */
    @Nullable
    private static volatile StageBlocks.Offerings serverOfferings;

    /** The client heard the server's offerings (an empty list from an older payload changes nothing). */
    public static void serverOfferings(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        StageBlocks.Offerings known = serverOfferings;
        if (known == null || !known.ids().equals(ids)) {
            serverOfferings = new StageBlocks.Offerings(ids);
        }
    }

    /** Leaving a server: its offerings do not carry over to the next one. */
    public static void forgetServerOfferings() {
        serverOfferings = null;
    }

    /**
     * Whether a click with {@code stack} on a Hearth is one the server answers (an offering, or flint
     * and steel): the client swallows those so it predicts no block placement or fire there. It reads
     * the offerings the server sent ({@link #serverOfferings(List)}), else the local data.
     */
    public static boolean answers(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.FLINT_AND_STEEL)) {
            return true;
        }
        StageBlocks.Offerings known = serverOfferings;
        return (known != null ? known : StageBlocks.offerings(Hearths.data())).stageOf(stack) > 0;
    }

    // ---- Lighting -------------------------------------------------------------------------------

    private static InteractionResult light(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
            InteractionHand hand, ItemStack held) {
        HearthData data = Hearths.data();
        if (!StageBlocks.isOffering(data, 1, held) || !mayChange(player, level, pos)) {
            return InteractionResult.PASS;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return InteractionResult.PASS;
        }
        HearthStructure.Probe probe = StageBlocks.probe(level, pos, data);
        Problem platform = HearthStructure.layer(probe, 1);
        if (platform != null) {
            // On hearth stone it was meant as a Hearth: say what is missing. Elsewhere it is just a campfire.
            if (probe.is('S', 0, -1, 0) || probe.is('k', 0, -1, 0)) {
                StageText.actionBar(player, StageText.notAHearthYet(platform));
            }
            return InteractionResult.PASS;
        }
        BlockPos other = tooClose(level, pos);
        if (other != null) {
            StageText.chat(player, StageText.tooClose(distance(pos, other), ProficiencyConfig.hearthMinSpacing()));
            return InteractionResult.SUCCESS;
        }

        long now = level.getGameTime();
        level.setBlock(pos, HearthBlocks.HEARTH.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof HearthBlockEntity hearth)) {
            return InteractionResult.SUCCESS;
        }
        HearthState hs = hearth.state();
        Check check = HearthStructure.check(probe);
        hs.onLit(now, HearthWorldData.get(level.getServer()).playTicks());
        seal(level, pos, hearth, player, 1, check, data);
        take(player, hand, held);

        StageFx.lit(level, pos);
        PlayerHearth mine = HearthAttachments.of(player);
        boolean first = !mine.flag(FIRST_LIT);
        mine.setFlag(FIRST_LIT, true);
        StageFx.banner(player, 1, first, "");
        tellAround(level, pos, player, StageText.lit(player), 1);
        HearthEvents.STAGE_SEALED.fire(new HearthEvents.StageSealed(level, pos, player, 1));
        // The next layer may already stand (built before lighting): say what to offer.
        HearthStageCheck.sync(level, pos, hearth, check);
        return InteractionResult.SUCCESS;
    }

    /** The nearest other Hearth closer than the spacing, or null (spacing 0 turns the rule off). */
    static BlockPos tooClose(ServerLevel level, BlockPos pos) {
        int spacing = ProficiencyConfig.hearthMinSpacing();
        return spacing <= 0 ? null : HearthIndexData.get(level).tooClose(level, pos, spacing);
    }

    /** Horizontal distance in whole blocks, rounded down, so "63 blocks away" never reads as the limit. */
    static int distance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return (int) Math.floor(Math.sqrt(dx * dx + dz * dz));
    }

    // ---- Offerings ------------------------------------------------------------------------------

    private static InteractionResult offer(ServerPlayer player, ServerLevel level, BlockPos pos,
            HearthBlockEntity hearth, InteractionHand hand, ItemStack held, int offered, HearthData data) {
        HearthState state = hearth.state();
        int highest = state.highestOffering();
        if (offered <= highest) {
            if (offered == 1) {
                StageText.actionBar(player, StageText.alreadyLit());
                return InteractionResult.SUCCESS;
            }
            HearthStructure.Probe probe = StageBlocks.probe(level, pos, data);
            Problem broken = HearthStructure.firstProblem(probe, offered);
            StageText.actionBar(player, broken != null ? StageText.rebuild(broken) : StageText.alreadyOffered());
            if (broken == null && state.stage() < offered) {
                // It stands again and the timer has not noticed yet: re-read it with the same probe.
                HearthStageCheck.recheckNow(level, pos, hearth, probe);
            }
            return InteractionResult.SUCCESS;
        }
        if (offered > highest + 1) {
            StageText.chat(player, StageText.wantsFirst(StageBlocks.offeringName(data, highest + 1), highest + 1));
            return InteractionResult.SUCCESS;
        }
        if (!mayChange(player, level, pos)) {
            return InteractionResult.PASS;
        }
        Check check = HearthStructure.check(StageBlocks.probe(level, pos, data));
        Problem problem = check.built() >= offered ? null : check.problem();
        int need = data.character().forStage(offered);
        boolean characterShort = state.character() < need;
        if (problem != null || characterShort) {
            if (problem != null) {
                StageText.chat(player, StageText.notYet(problem));
            }
            if (characterShort) {
                StageText.chat(player, StageText.characterRefusal(state.character(), need, offered));
            }
            return InteractionResult.SUCCESS;
        }

        take(player, hand, held);
        seal(level, pos, hearth, player, offered, check, data);

        StageFx.sealed(level, pos, offered);
        tellAround(level, pos, player, StageText.sealed(player, offered), offered);
        HearthEvents.STAGE_SEALED.fire(new HearthEvents.StageSealed(level, pos, player, offered));
        HearthPresence.update(level, pos, hearth);
        HearthStageCheck.sync(level, pos, hearth, check);
        return InteractionResult.SUCCESS;
    }

    /**
     * A stage was paid ({@code offered}, 1 for the lighting): the offering is remembered, the build
     * just read becomes the stage and counts as the last check, the ceiling is shown and counted as
     * told (the player's own moment says it, so the second does not also say "stands again"), and the
     * player is remembered as a sealer. The one place lighting and offerings write the lane.
     */
    private static void seal(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, ServerPlayer player, int offered,
            Check check, HearthData data) {
        HearthState state = hearth.state();
        state.setHighestOffering(offered);
        state.setStage(HearthStructure.stage(check.built(), offered));
        HearthStageCheck.markChecked(HearthStageCheck.lane(hearth), level.getGameTime());
        HearthStageCheck.settle(level, pos, hearth, state.ceiling(data));
        addSealer(hearth, player);
        hearth.setChanged();
    }

    /** Durability for a tool (flint and steel), one item for the rest; nothing in creative. */
    static void take(ServerPlayer player, InteractionHand hand, ItemStack held) {
        if (held.isDamageableItem()) {
            held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        } else {
            held.consume(1, player);
        }
    }

    /** The chat line to everyone in range and the one who did it, and the stage banner to each. */
    private static void tellAround(ServerLevel level, BlockPos pos, ServerPlayer by, net.minecraft.network.chat.Component line,
            int stage) {
        List<ServerPlayer> told = new ArrayList<>(HearthPresence.inRange(level, pos));
        if (!told.contains(by)) {
            told.add(by);
        }
        String name = by.getGameProfile().getName();
        for (ServerPlayer player : told) {
            StageText.chat(player, line);
            if (player != by) {
                StageFx.banner(player, stage, false, name);
            } else if (stage > 1) {
                StageFx.banner(player, stage, false, "");
            }
        }
    }

    // ---- Who built it ---------------------------------------------------------------------------

    /** Remembers who lit the Hearth or sealed a stage: they hear it when it is put out. */
    static void addSealer(HearthBlockEntity hearth, ServerPlayer player) {
        CompoundTag lane = HearthStageCheck.lane(hearth);
        ListTag list = lane.getList(HearthStageCheck.SEALERS, Tag.TAG_INT_ARRAY);
        UUID id = player.getUUID();
        for (Tag tag : list) {
            if (id.equals(NbtUtils.loadUUID(tag))) {
                return;
            }
        }
        list.add(NbtUtils.createUUID(id));
        lane.put(HearthStageCheck.SEALERS, list);
    }

    /** Everyone who fed it charge (the ledger) or lit it or sealed a stage. */
    static Set<UUID> feeders(HearthBlockEntity hearth) {
        Set<UUID> out = new LinkedHashSet<>(hearth.state().ledger().players().keySet());
        ListTag list = HearthStageCheck.lane(hearth).getList(HearthStageCheck.SEALERS, Tag.TAG_INT_ARRAY);
        for (Tag tag : list) {
            try {
                out.add(NbtUtils.loadUUID(tag));
            } catch (IllegalArgumentException e) {
                // A damaged entry names nobody.
            }
        }
        return out;
    }
}
