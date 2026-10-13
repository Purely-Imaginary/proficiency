package dev.amman.proficiency.gametest;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthBreaking;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.HearthOfferings;
import dev.amman.proficiency.hearth.stages.HearthStageCheck;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import dev.amman.proficiency.hearth.stages.StageText;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The stages lane in a real world, on both loaders (docs/HEARTH-SPEC.md, "Building and upgrading"):
 * lighting, the spacing rule (abuse check 1), sealing each stage, a broken layer and its rebuild
 * with no second offering, a refused offering, the sneak-hold break and the in-range flag. The
 * build rules themselves are unit tested in core ({@code HearthStructureTest}).
 *
 * <p>Each test has its own batch: a Hearth's range is 24 blocks and its spacing 64, far larger than
 * the {@code empty} template, so two tests side by side would see each other's Hearth. The build
 * stands on the template's floor (y 1) around the campfire at {@link #FIRE} and reaches a block past
 * the template on each side; every test clears what it built.
 */
public final class HearthStagesGameTests {

    private static final String T = "proficiency:empty";
    /** The campfire; its platform replaces the template's floor. */
    private static final BlockPos FIRE = new BlockPos(2, 2, 2);

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HearthStagesGameTests() {
    }

    // ---- The world ------------------------------------------------------------------------------

    private static BlockPos at(BlockPos fire, int dx, int dy, int dz) {
        return fire.offset(dx, dy, dz);
    }

    /** Builds stage {@code stage} of the default design around the campfire at {@code fire} (relative). */
    private static void build(GameTestHelper helper, BlockPos fire, int stage) {
        HearthDesignBuilds.place(helper, fire, stage);
    }

    /** Air over the whole 9x9x7 box around {@code fire}, and no drops left lying there. */
    private static void clear(GameTestHelper helper, BlockPos fire) {
        HearthDesignBuilds.clear(helper, fire);
    }

    private static ServerPlayer player(GameTestHelper helper, List<ServerPlayer> cast, double x, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
        cast.add(player);
        return player;
    }

    private static void leave(List<ServerPlayer> cast) {
        for (ServerPlayer player : cast) {
            player.server.getPlayerList().remove(player);
        }
        cast.clear();
    }

    /** Leftover mock players of earlier tests go far up, out of every range here. */
    private static void clearStage(GameTestHelper helper) {
        Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
            if (other.position().distanceTo(origin) < 96) {
                other.setPos(other.getX(), other.getY() + 400, other.getZ());
            }
        }
    }

    /** A right click with {@code stack} on the block, through the loader's own use path. */
    private static InteractionResult use(ServerPlayer player, BlockPos abs, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    /** Lights the campfire at {@code fire} (relative) with flint and steel; the Hearth, or a failure. */
    private static HearthBlockEntity light(GameTestHelper helper, ServerPlayer player, BlockPos fire) {
        helper.setBlock(fire, Blocks.CAMPFIRE);
        BlockPos abs = helper.absolutePos(fire);
        use(player, abs, new ItemStack(Items.FLINT_AND_STEEL));
        if (!(helper.getLevel().getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("the campfire at " + fire
                    + " should have become a Hearth, found " + helper.getLevel().getBlockState(abs));
        }
        return hearth;
    }

    /** Runs {@code body} with {@code hearth.minSpacing} set, so another test's Hearth cannot refuse it. */
    private static <R> R withSpacing(int spacing, java.util.function.Supplier<R> body) {
        int before = ProficiencyConfig.hearthMinSpacing();
        ProficiencyConfig.HEARTH_MIN_SPACING.set(spacing);
        try {
            return body.get();
        } finally {
            ProficiencyConfig.HEARTH_MIN_SPACING.set(before);
        }
    }

    // ---- Reading what was said --------------------------------------------------------------------

    private record Line(ServerPlayer player, String key, Object[] args, boolean actionBar) {
    }

    /** Captures the lane's chat and action-bar lines while a test runs. */
    private static final class Said implements StageText.Sink, AutoCloseable {
        final List<Line> lines = new ArrayList<>();

        Said() {
            StageText.sink = this;
        }

        @Override
        public void send(ServerPlayer player, Component message, boolean actionBar) {
            if (message.getContents() instanceof TranslatableContents t) {
                lines.add(new Line(player, t.getKey(), t.getArgs(), actionBar));
            }
        }

        Line find(ServerPlayer player, String key) {
            for (Line line : lines) {
                if (line.player() == player && line.key().equals("proficiency.hearth.stages." + key)) {
                    return line;
                }
            }
            return null;
        }

        boolean has(ServerPlayer player, String key) {
            return find(player, key) != null;
        }

        @Override
        public void close() {
            StageText.sink = StageText.PLAYER;
        }
    }

    private static String keyOf(Object arg) {
        return arg instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(arg);
    }

    private static Object[] argsOf(Object arg) {
        return arg instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
    }

    /** The first cell of the default lane's stage {@code stage} with this role that the stage before leaves blank. */
    private static HearthDesigns.Cell firstNew(char role, int stage) {
        HearthDesigns.Lane lane = HearthDesigns.shipped().lane(HearthDesignBuilds.LANE);
        for (HearthDesigns.Cell cell : lane.stage(stage).cells()) {
            if (cell.role() == role && lane.stage(stage - 1).cellAt(cell.dx(), cell.dy(), cell.dz()) == null) {
                return cell;
            }
        }
        throw new IllegalStateException("no new " + role + " in stage " + stage);
    }

    /** "north 1;east 1;down 1;": the parts of a where-component, in the order the line gives them. */
    private static String whereOf(Object arg) {
        StringBuilder out = new StringBuilder();
        if (arg instanceof Component c) {
            for (Component part : c.getSiblings()) {
                if (part.getContents() instanceof TranslatableContents t) {
                    out.append(t.getKey().substring(t.getKey().lastIndexOf('.') + 1)).append(' ').append(t.getArgs()[0]).append(';');
                }
            }
        }
        return out.toString();
    }

    private static String whereExpected(int dx, int dy, int dz) {
        StringBuilder out = new StringBuilder();
        if (dz != 0) {
            out.append(dz < 0 ? "north " : "south ").append(Math.abs(dz)).append(';');
        }
        if (dx != 0) {
            out.append(dx < 0 ? "west " : "east ").append(Math.abs(dx)).append(';');
        }
        if (dy != 0) {
            out.append(dy < 0 ? "down " : "up ").append(Math.abs(dy)).append(';');
        }
        return out.toString();
    }

    /** A problem line: the cell's role, where it is from the fire in player terms, and the count still wrong. */
    private static void assertCell(GameTestHelper helper, Object problem, char role, int dx, int dy, int dz, int remaining) {
        String text = keyOf(problem) + " " + List.of(argsOf(problem));
        Object[] args = argsOf(problem);
        helper.assertTrue(keyOf(problem).equals("proficiency.hearth.stages.problem.cell") && args.length == 3
                && keyOf(args[0]).equals("proficiency.hearth.role." + role)
                && whereOf(args[1]).equals(whereExpected(dx, dy, dz))
                && keyOf(args[2]).contains("to_go.") && String.valueOf(argsOf(args[2])[0]).equals(String.valueOf(remaining)),
                role + " wanted " + whereExpected(dx, dy, dz) + " (" + remaining + " to go), got " + text
                        + " where " + whereOf(args.length > 1 ? args[1] : null));
    }

    private static String number(Object arg) {
        return String.valueOf(arg);
    }

    // ---- Tests ----------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_stages_light")
    public static void aCampfireOnHearthStoneLightsIntoAHearth(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        List<HearthEvents.StageSealed> sealed = new ArrayList<>();
        java.util.function.Consumer<HearthEvents.StageSealed> listener = sealed::add;
        HearthEvents.STAGE_SEALED.add(listener);
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 4.5, 2.5);
            build(helper, FIRE, 1);
            // One stone short: still a campfire, and the action bar says what is missing.
            helper.setBlock(at(FIRE, 1, -1, -1), Blocks.DIRT);
            helper.setBlock(FIRE, Blocks.CAMPFIRE);
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            withSpacing(10, () -> use(amman, abs, flint));
            helper.assertTrue(level.getBlockState(abs).is(Blocks.CAMPFIRE), "a broken platform should leave the campfire");
            Line short1 = said.find(amman, "not_yet");
            helper.assertTrue(short1 != null && short1.actionBar(), "the action bar should name the gap");
            assertCell(helper, short1.args()[0], 'S', 1, -1, -1, 1);

            helper.setBlock(at(FIRE, 1, -1, -1), Blocks.STONE_BRICKS);
            long before = level.getGameTime();
            InteractionResult result = withSpacing(10, () -> use(amman, abs, flint));
            helper.assertTrue(result.consumesAction(), "lighting should consume the click: " + result);
            BlockState lit = level.getBlockState(abs);
            helper.assertTrue(lit.is(HearthBlocks.HEARTH.get()) && lit.getValue(HearthBlock.STAGE) == 1
                    && !lit.getValue(HearthBlock.SMOULDERING), "the campfire should be a Hearth, stage I: " + lit);
            HearthBlockEntity hearth = (HearthBlockEntity) level.getBlockEntity(abs);
            HearthState state = hearth.state();
            helper.assertTrue(state.stage() == 1 && state.highestOffering() == 1, "stage I, flint and steel offered");
            helper.assertTrue(state.litAt() >= before, "lit now: " + state.litAt());
            helper.assertTrue(HearthIndexData.get(level).contains(abs), "the world index lists it");
            helper.assertTrue(flint.getDamageValue() == 1 && !flint.isEmpty(), "flint and steel loses durability only");
            helper.assertTrue(sealed.size() == 1 && sealed.get(0).stage() == 1 && sealed.get(0).player() == amman
                    && sealed.get(0).hearth().equals(abs), "stage I sealed once, by the lighter: " + sealed);
            helper.assertTrue(said.has(amman, "lit"), "the lighter is told");
            helper.assertTrue(HearthAttachments.of(amman).flag("stages.firstLit"), "the first Hearth is remembered");

            // Flint and steel again: already lit, no fire beside it, no second seal.
            use(amman, abs, flint);
            helper.assertTrue(said.has(amman, "already_lit") && sealed.size() == 1, "already lit");
            helper.assertTrue(level.getBlockState(abs.above()).isAir(), "no fire was set");
            // An empty hand opens the panel (the click is taken); the panel asks for its ledger itself.
            List<HearthStatePayload> panels = new ArrayList<>();
            HearthOfferings.panelSender = (who, sent) -> {
                if (who == amman && sent.reason() == HearthStatePayload.PANEL && sent.pos() == abs.asLong()) {
                    panels.add(sent);
                }
            };
            helper.assertTrue(use(amman, abs, ItemStack.EMPTY).consumesAction(), "an empty hand opens the panel");
            helper.assertTrue(panels.size() == 1, "the panel's state is sent: " + panels.size());
        } finally {
            HearthOfferings.panelSender = dev.amman.proficiency.hearth.net.HearthNet::sendState;
            HearthEvents.STAGE_SEALED.remove(listener);
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_stages_spacing")
    public static void aSecondHearthTooCloseRefusesNamingTheDistance(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos near = FIRE.offset(12, 0, 0);
        BlockPos far = FIRE.offset(0, 0, 23);
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 4.5, 2.5);
            build(helper, FIRE, 1);
            withSpacing(20, () -> light(helper, amman, FIRE));
            // Abuse check 1: 12 blocks away with a spacing of 20 refuses, naming the distance.
            build(helper, near, 1);
            helper.setBlock(near, Blocks.CAMPFIRE);
            BlockPos nearAbs = helper.absolutePos(near);
            InteractionResult refused = withSpacing(20, () -> use(amman, nearAbs, new ItemStack(Items.FLINT_AND_STEEL)));
            helper.assertTrue(level.getBlockState(nearAbs).is(Blocks.CAMPFIRE), "the second campfire stays a campfire");
            helper.assertTrue(refused.consumesAction(), "the refusal takes the click (no fire beside it)");
            Line line = said.find(amman, "too_close");
            helper.assertTrue(line != null && "12".equals(number(line.args()[0])) && "20".equals(number(line.args()[1])),
                    "the refusal names 12 of 20 blocks: " + (line == null ? "nothing said" : List.of(line.args())));
            helper.assertFalse(HearthIndexData.get(level).contains(nearAbs), "nothing indexed for the refusal");

            // Past the spacing it lights.
            build(helper, far, 1);
            HearthBlockEntity second = withSpacing(20, () -> light(helper, amman, far));
            helper.assertTrue(second.state().stage() == 1, "23 blocks away lights");
        } finally {
            clear(helper, near);
            clear(helper, far);
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_stages_seal")
    public static void everyStageSealsWithItsOffering(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        List<Integer> sealed = new ArrayList<>();
        java.util.function.Consumer<HearthEvents.StageSealed> listener = event -> {
            if (event.hearth().equals(abs)) {
                sealed.add(event.stage());
            }
        };
        HearthEvents.STAGE_SEALED.add(listener);
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 2.5, 4.5);
            build(helper, FIRE, 1);
            HearthBlockEntity hearth = withSpacing(10, () -> light(helper, amman, FIRE));
            HearthState state = hearth.state();
            net.minecraft.world.item.Item[] offerings = {Items.FLINT_AND_STEEL, Items.COPPER_BLOCK, Items.IRON_BLOCK,
                    Items.GOLD_BLOCK, Items.DIAMOND_BLOCK, Items.HEART_OF_THE_SEA};

            // A later stage's offering first: refused, named, kept.
            build(helper, FIRE, 2);
            ItemStack early = new ItemStack(Items.DIAMOND_BLOCK, 2);
            use(amman, abs, early);
            helper.assertTrue(early.getCount() == 2 && state.highestOffering() == 1, "out of order is refused");
            helper.assertTrue(said.has(amman, "offer.first"), "it names the offering the Hearth wants first");

            for (int stage = 2; stage <= 6; stage++) {
                build(helper, FIRE, stage);
                ItemStack offering = new ItemStack(offerings[stage - 1], 3);
                if (stage == 4) {
                    // The Character the Hall needs (14) is missing: refused, named, kept.
                    state.setCharacter(11);
                    use(amman, abs, offering);
                    Line why = said.find(amman, "offer.character");
                    helper.assertTrue(offering.getCount() == 3 && state.highestOffering() == 3 && why != null,
                            "Character 11/14 refuses the Hall");
                    Object character = why.args()[0];
                    helper.assertTrue("11".equals(number(argsOf(character)[0])) && "14".equals(number(argsOf(character)[1])),
                            "it names Character 11/14: " + List.of(argsOf(character)));
                }
                // The comfort lane owns Character; give the Hearth enough for every stage.
                state.setCharacter(50);
                InteractionResult result = use(amman, abs, offering);
                helper.assertTrue(result.consumesAction(), "stage " + stage + ": the offering takes the click");
                helper.assertTrue(offering.getCount() == 2, "stage " + stage + ": one offering taken, " + offering.getCount() + " left");
                helper.assertTrue(state.highestOffering() == stage && state.stage() == stage,
                        "stage " + stage + ": sealed, built " + state.stage() + " offered " + state.highestOffering());
                helper.assertTrue(state.ceiling(Hearths.data()) == stage, "stage " + stage + ": the ceiling follows");
                BlockState shown = level.getBlockState(abs);
                helper.assertTrue(shown.getValue(HearthBlock.STAGE) == stage, "stage " + stage + ": the blockstate shows it: " + shown);
                helper.assertTrue(sealed.get(sealed.size() - 1) == stage, "stage " + stage + ": HearthEvents.STAGE_SEALED");
                // A second one of the same is not taken.
                use(amman, abs, offering);
                helper.assertTrue(offering.getCount() == 2, "stage " + stage + ": a second offering is not taken");
            }
            helper.assertTrue(sealed.equals(List.of(1, 2, 3, 4, 5, 6)), "every stage sealed once, in order: " + sealed);
            helper.assertTrue(said.find(amman, "sealed") != null, "the sealing is told");
        } finally {
            HearthEvents.STAGE_SEALED.remove(listener);
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_stages_rebuild", timeoutTicks = 120)
    public static void aBrokenLayerHoldsTheChargeAndARebuildDoesNotRepay(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        Said said = new Said();
        ServerPlayer amman = player(helper, cast, 2.5, 4.5);
        HearthBlockEntity hearth;
        HearthState state;
        try {
            build(helper, FIRE, 4);
            hearth = withSpacing(10, () -> light(helper, amman, FIRE));
            state = hearth.state();
            state.setCharacter(50);
            use(amman, abs, new ItemStack(Items.COPPER_BLOCK));
            use(amman, abs, new ItemStack(Items.IRON_BLOCK));
            use(amman, abs, new ItemStack(Items.GOLD_BLOCK));
            helper.assertTrue(state.stage() == 4 && state.highestOffering() == 4, "setup: a Hall, stage IV");
            state.setCharge(HearthPool.HEARTY, 3000);
            state.setCharge(HearthPool.WORKSHOP, 650);

            // Break a log new in stage III (a post of the frame) through the real break path: the box is marked dirty.
            HearthDesigns.Cell logCell = firstNew('W', 3);
            BlockPos log = helper.absolutePos(at(FIRE, logCell.dx(), logCell.dy(), logCell.dz()));
            amman.gameMode.destroyBlock(log);
            helper.assertTrue(level.getBlockState(log).isAir(), "setup: the log is gone");
            helper.assertTrue(hearth.laneData("stages").getBoolean("dirty"), "a block broken in the box marks the Hearth dirty");
            // The check runs at most every 30 s: pretend the last one was 30 s ago and let the second come.
            hearth.laneData("stages").putLong("lastCheck", level.getGameTime() - HearthStageCheck.RECHECK_TICKS);
        } catch (RuntimeException e) {
            said.close();
            clear(helper, FIRE);
            leave(cast);
            throw e;
        }
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(state.stage() == 2, "the pillar fell: the built stage is II, got " + state.stage());
                helper.assertTrue(state.highestOffering() == 4, "the offerings are remembered");
                helper.assertTrue(state.ceiling(Hearths.data()) == 2 && level.getBlockState(abs).getValue(HearthBlock.STAGE) == 2,
                        "the ceiling and the blockstate drop to II");
                helper.assertTrue(state.charge(HearthPool.HEARTY) == 3000 && state.charge(HearthPool.WORKSHOP) == 650,
                        "charge is held, never cut");
                helper.assertTrue(state.effectiveTier(HearthPool.HEARTY, Hearths.data()) == 2, "the pool shows capped at II");
                Line fell = said.find(amman, "fell");
                helper.assertTrue(fell != null, "the fall is told to who is home");
                Object why = fell.args()[2];
                HearthDesigns.Cell cut = firstNew('W', 3);
                assertCell(helper, why, 'W', cut.dx(), cut.dy(), cut.dz(), 1);

                // Re-offering what was paid is not taken, and names what to rebuild.
                ItemStack iron = new ItemStack(Items.IRON_BLOCK);
                use(amman, abs, iron);
                helper.assertTrue(iron.getCount() == 1 && said.has(amman, "offer.rebuild"), "no second iron block");

                // Rebuild: the Hall stands again with no offering. (Character is the comfort lane's; it
                // may have scanned these unmarked test blocks meanwhile.)
                state.setCharacter(50);
                HearthDesignBuilds.set(helper, at(FIRE, cut.dx(), cut.dy(), cut.dz()), Blocks.OAK_LOG.defaultBlockState());
                HearthStageCheck.recheckNow(level, abs, hearth);
                helper.assertTrue(state.stage() == 4 && state.ceiling(Hearths.data()) == 4, "rebuilt: IV again, got " + state.stage());
                helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4, "the blockstate is back at IV at once");
                // One line per 30 s per Hearth: the fall was told a second ago, so the rise waits for the gap.
                helper.assertFalse(said.has(amman, "rose"), "a rise right after the fall line is not told yet");
                CompoundTag lane = hearth.laneData("stages");
                lane.putLong("toldAt", lane.getLong("toldAt") - HearthStageCheck.TELL_GAP_TICKS);
                state.setCharacter(50);
                HearthStageCheck.second(level, abs, hearth);
                helper.assertTrue(said.has(amman, "rose"), "the rebuild is told once the gap is over");
                ItemStack gold = new ItemStack(Items.GOLD_BLOCK);
                use(amman, abs, gold);
                helper.assertTrue(gold.getCount() == 1 && said.has(amman, "offer.already"), "the gold block is not owed again");
                helper.assertTrue(state.charge(HearthPool.HEARTY) == 3000, "charge untouched by the rebuild");
                helper.succeed();
            } finally {
                said.close();
                clear(helper, FIRE);
                leave(cast);
            }
        });
    }

    @GameTest(template = T, batch = "hearth_stages_refuse")
    public static void anOfferingIsRefusedUntilItsLayerIsComplete(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        BlockPos abs = helper.absolutePos(FIRE);
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 2.5, 4.5);
            build(helper, FIRE, 2);
            HearthDesigns.Cell ring = firstNew('S', 2);
            helper.setBlock(at(FIRE, ring.dx(), ring.dy(), ring.dz()), Blocks.DIRT);
            HearthBlockEntity hearth = withSpacing(10, () -> light(helper, amman, FIRE));
            HearthState state = hearth.state();
            state.setCharacter(50);
            ItemStack copper = new ItemStack(Items.COPPER_BLOCK, 4);
            use(amman, abs, copper);
            helper.assertTrue(copper.getCount() == 4 && state.highestOffering() == 1, "the copper block stays in hand");
            Line line = said.find(amman, "offer.not_yet");
            helper.assertTrue(line != null, "the refusal names the problem");
            Object problem = line.args()[0];
            assertCell(helper, problem, 'S', ring.dx(), ring.dy(), ring.dz(), 1);

            helper.setBlock(at(FIRE, ring.dx(), ring.dy(), ring.dz()), Blocks.STONE_BRICKS);
            use(amman, abs, copper);
            helper.assertTrue(copper.getCount() == 3 && state.highestOffering() == 2, "complete: the copper block is taken");

            // A post a log short.
            build(helper, FIRE, 3);
            HearthDesigns.Cell post = firstNew('W', 3);
            helper.setBlock(at(FIRE, post.dx(), post.dy(), post.dz()), Blocks.AIR);
            ItemStack iron = new ItemStack(Items.IRON_BLOCK);
            said.lines.clear();
            use(amman, abs, iron);
            Line pillar = said.find(amman, "offer.not_yet");
            helper.assertTrue(iron.getCount() == 1 && pillar != null, "the iron block stays in hand");
            Object why = pillar.args()[0];
            assertCell(helper, why, 'W', post.dx(), post.dy(), post.dz(), 1);
        } finally {
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_stages_break", timeoutTicks = 200)
    public static void puttingTheHearthOutTakesASneakHold(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        Said said = new Said();
        ServerPlayer amman = player(helper, cast, 4.5, 2.5);
        ServerPlayer second = player(helper, cast, 2.5, 4.5);
        ServerPlayer stranger = player(helper, cast, 0.5, 0.5);
        int top = level.getMaxBuildHeight();
        Runnable cleanup = () -> {
            amman.setShiftKeyDown(false);
            said.close();
            clear(helper, FIRE);
            leave(cast);
        };
        try {
            build(helper, FIRE, 1);
            HearthBlockEntity hearth = withSpacing(10, () -> light(helper, amman, FIRE));
            // Rowan fed it (the charge lane writes the ledger); the stranger never did.
            hearth.state().ledger().add(second.getUUID(), "Rowan", HearthPool.HEARTY, "mining", 40, 1);
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(25, () -> {
            try {
                String dimension = level.dimension().location().toString();
                PlayerHearth flag = HearthAttachments.of(amman);
                helper.assertTrue(flag.inRange(dimension, level.getGameTime()) && flag.inRangePos() == abs.asLong(),
                        "setup: the Hearth flags who is home");
                amman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                BlockState hearthState = level.getBlockState(abs);

                // Not sneaking: no progress at all, whatever the pickaxe, and a hint.
                amman.setShiftKeyDown(false);
                helper.assertTrue(hearthState.getDestroyProgress(amman, level, abs) == 0f, "no progress without sneaking");
                amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 1);
                for (int i = 0; i < 100; i++) {
                    amman.gameMode.tick();
                }
                helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()), "a long swing without sneaking does nothing");
                helper.assertTrue(said.has(amman, "sneak_to_break"), "the swing tells how it is done");
                amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, top, 2);

                // The client's rule: 1/60 a tick while sneaking. Sneak and start; let go after 1.5 s.
                amman.setShiftKeyDown(true);
                float perTick = HearthBreaking.destroyProgress(amman);
                helper.assertTrue(Math.abs(perTick - 1f / HearthBreaking.HOLD_TICKS) < 1e-6, "1/60 a tick while sneaking: " + perTick);
                amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 3);
                helper.assertTrue(HearthBreaking.sneakTicks(amman, abs) == 0, "the server starts its own count at the swing");
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(HearthBreaking.HOLD_TICKS / 2, () -> {
                try {
                    int counted = HearthBreaking.sneakTicks(amman, abs);
                    helper.assertTrue(counted >= HearthBreaking.HOLD_TICKS / 2 - 3 && counted <= HearthBreaking.HOLD_TICKS / 2 + 1,
                            "the server counts the ticks really sneaked: " + counted);
                    amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, top, 4);
                    helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()), "half a hold does not put it out");
                    // The full hold, through the normal break (claim mods see it).
                    amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 5);
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                helper.runAfterDelay(HearthBreaking.HOLD_TICKS + 2, () -> {
                    try {
                        amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 6);
                        helper.assertTrue(level.getBlockState(abs).isAir(), "the full hold puts it out: " + level.getBlockState(abs));
                        helper.assertFalse(HearthIndexData.get(level).contains(abs), "the index forgets it");
                        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0),
                                item -> item.getItem().is(Items.CAMPFIRE));
                        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "it drops one plain campfire: " + drops);
                        Line told = said.find(second, "put_out");
                        helper.assertTrue(told != null && told.args()[0] instanceof Component name
                                && name.getString().equals(amman.getDisplayName().getString()), "the feeder is told who put it out");
                        helper.assertFalse(said.has(stranger, "put_out"), "who never fed it is not told");
                        helper.assertTrue(said.has(amman, "put_out.self"), "the breaker is told what it cost");
                        String dimension = level.dimension().location().toString();
                        helper.assertFalse(HearthAttachments.of(amman).inRange(dimension, level.getGameTime()),
                                "nobody is home at a Hearth that is gone");
                        for (ItemEntity drop : drops) {
                            drop.discard();
                        }
                        helper.succeed();
                    } finally {
                        cleanup.run();
                    }
                });
            });
        });
    }

    /**
     * Review finding: the server read the sneak key only when the break arrived, so a modified client
     * could swing for 3 s and sneak for the last tick alone. The server now counts the sneak itself.
     */
    @GameTest(template = T, batch = "hearth_stages_break_cheat", timeoutTicks = 220)
    public static void sneakingOnlyAtTheEndDoesNotPutItOut(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        ServerPlayer amman = player(helper, cast, 4.5, 2.5);
        int top = level.getMaxBuildHeight();
        Runnable cleanup = () -> {
            amman.setShiftKeyDown(false);
            clear(helper, FIRE);
            leave(cast);
        };
        int ticked;
        try {
            build(helper, FIRE, 1);
            withSpacing(10, () -> light(helper, amman, FIRE));
            amman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            amman.setShiftKeyDown(false);
            ticked = amman.tickCount;
            amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 1);
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(HearthBreaking.HOLD_TICKS + 5, () -> {
            try {
                helper.assertTrue(amman.tickCount >= ticked + HearthBreaking.HOLD_TICKS,
                        "setup: the level ticks the player's break: " + (amman.tickCount - ticked) + " ticks");
                helper.assertTrue(HearthBreaking.sneakTicks(amman, abs) == 0, "a swing without sneaking counts nothing");
                // The modified client presses sneak for the last tick only, then says it is done.
                amman.setShiftKeyDown(true);
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(1, () -> {
                try {
                    amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 2);
                    helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()),
                            "3 s of swinging with one tick of sneak does not put it out");
                    amman.setShiftKeyDown(false);
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                helper.runAfterDelay(20, () -> {
                    try {
                        helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()),
                                "vanilla's late finish gets nowhere without sneaking");
                        int counted = HearthBreaking.sneakTicks(amman, abs);
                        helper.assertTrue(counted >= 0 && counted <= 2, "one or two ticks of sneak counted: " + counted);
                        helper.succeed();
                    } finally {
                        cleanup.run();
                    }
                });
            });
        });
    }

    /**
     * Found while porting to Forge (2026-10-09): after a refused finish, vanilla keeps finishing the break
     * late (hasDelayedDestroy), multiplied by the ticks since that older swing, so a second swing with
     * sneak put the Hearth out after a few ticks. The server's break speed now reads 0 below 70% sneaked.
     */
    @GameTest(template = T, batch = "hearth_stages_break_late", timeoutTicks = 260)
    public static void aRefusedFinishDoesNotShortenTheNextHold(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        ServerPlayer amman = player(helper, cast, 4.5, 2.5);
        int top = level.getMaxBuildHeight();
        Runnable cleanup = () -> {
            amman.setShiftKeyDown(false);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0),
                    item -> item.getItem().is(Items.CAMPFIRE))) {
                drop.discard();
            }
            clear(helper, FIRE);
            leave(cast);
        };
        try {
            build(helper, FIRE, 1);
            withSpacing(10, () -> light(helper, amman, FIRE));
            amman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            amman.setShiftKeyDown(false);
            amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 1);
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(HearthBreaking.HOLD_TICKS + 5, () -> {
            try {
                // A finish the server refuses (nothing sneaked): vanilla keeps the break pending.
                amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 2);
                helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()), "an unsneaked swing is refused");
                // A new swing, sneaking from the start.
                amman.setShiftKeyDown(true);
                amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 3);
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(HearthBreaking.HOLD_TICKS / 2, () -> {
                try {
                    helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()),
                            "half a hold after a refused finish does not put it out");
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                helper.runAfterDelay(HearthBreaking.HOLD_TICKS / 2 + 2, () -> {
                    try {
                        amman.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 4);
                        helper.assertTrue(level.getBlockState(abs).isAir(), "the full hold still puts it out: " + level.getBlockState(abs));
                        helper.succeed();
                    } finally {
                        cleanup.run();
                    }
                });
            });
        });
    }

    /**
     * Review finding: Character hovering at a threshold announced every change (chat, sound,
     * particles) and flickered the blockstate. A Character drop now shows after 5 s, and one line per
     * 30 s per Hearth folds what happened inside the gap.
     */
    @GameTest(template = T, batch = "hearth_stages_hover")
    public static void aCharacterHoveringAtAThresholdNeitherFlickersNorFloods(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 2.5, 4.5);
            build(helper, FIRE, 4);
            HearthBlockEntity hearth = withSpacing(10, () -> light(helper, amman, FIRE));
            HearthState state = hearth.state();
            state.setCharacter(50);
            use(amman, abs, new ItemStack(Items.COPPER_BLOCK));
            use(amman, abs, new ItemStack(Items.IRON_BLOCK));
            use(amman, abs, new ItemStack(Items.GOLD_BLOCK));
            helper.assertTrue(state.highestOffering() == 4 && level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4,
                    "setup: a Hall, shown at IV");
            int need = Hearths.data().character().forStage(4);
            CompoundTag lane = hearth.laneData("stages");
            said.lines.clear();

            // Character flickers at the Hall's threshold once a second: the Hearth shows IV throughout, silent.
            for (int i = 0; i < 12; i++) {
                state.setCharacter(i % 2 == 0 ? need - 1 : need);
                HearthStageCheck.second(level, abs, hearth);
                helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4, "second " + i + ": still IV");
            }
            helper.assertTrue(said.lines.isEmpty(), "hovering says nothing: " + said.lines.size() + " lines");

            // A drop that holds 5 s shows: the blockstate falls to III and one line says why.
            state.setCharacter(need - 1);
            HearthStageCheck.second(level, abs, hearth);
            lane.putLong("lowSince", lane.getLong("lowSince") - (HearthStageCheck.FALL_DELAY_TICKS - 1));
            HearthStageCheck.second(level, abs, hearth);
            helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4, "4.95 s low: still IV");
            lane.putLong("lowSince", lane.getLong("lowSince") - 1);
            HearthStageCheck.second(level, abs, hearth);
            helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 3, "5 s low: shown at III");
            Line fell = said.find(amman, "fell");
            helper.assertTrue(fell != null && keyOf(fell.args()[2]).endsWith("stages.character"),
                    "the fall is told with its Character: " + (fell == null ? "nothing" : keyOf(fell.args()[2])));

            // It comes back at once, but the line waits for the 30 s gap.
            state.setCharacter(need);
            HearthStageCheck.second(level, abs, hearth);
            helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4, "a rise shows at once");
            helper.assertFalse(said.has(amman, "rose"), "inside the gap the rise is not told");
            // Down again for 5 s inside the gap: shown, not told.
            state.setCharacter(need - 1);
            HearthStageCheck.second(level, abs, hearth);
            lane.putLong("lowSince", lane.getLong("lowSince") - HearthStageCheck.FALL_DELAY_TICKS);
            HearthStageCheck.second(level, abs, hearth);
            helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 3, "down again: III");
            helper.assertTrue(count(said, amman, "fell") == 1, "one fall line inside the gap: " + count(said, amman, "fell"));
            // The gap ends where the last line left it (III): nothing to say.
            lane.putLong("toldAt", lane.getLong("toldAt") - HearthStageCheck.TELL_GAP_TICKS);
            HearthStageCheck.second(level, abs, hearth);
            helper.assertTrue(count(said, amman, "fell") == 1 && !said.has(amman, "rose"), "back where the last line left it: silent");
            // A rise after the gap is told at once.
            state.setCharacter(50);
            HearthStageCheck.second(level, abs, hearth);
            helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4 && said.has(amman, "rose"),
                    "after the gap the rise is told");
            helper.assertTrue(said.lines.size() == 2, "two lines in all: " + said.lines.size());
        } finally {
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    private static int count(Said said, ServerPlayer player, String key) {
        int n = 0;
        for (Line line : said.lines) {
            if (line.player() == player && line.key().equals("proficiency.hearth.stages." + key)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Review finding: lighting and offerings ran before claim mods saw the click. The loader's own
     * test class ({@code NeoForgeHearthStagesGameTests}, {@code FabricHearthStagesGameTests}) puts a
     * claim mod on the loader's real bus at its usual place (NeoForge NORMAL, a default-phase Fabric
     * callback, both registered after this mod), refusing clicks on the positions in {@code claimed};
     * this is the shared body. Inside the claim nothing is lit, no offering is taken and no panel
     * opens; outside it the same clicks work.
     */
    public static void aClaimRefusesTheClickBeforeTheHearth(GameTestHelper helper, java.util.Set<BlockPos> claimed) {
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        List<HearthEvents.StageSealed> sealed = new ArrayList<>();
        java.util.function.Consumer<HearthEvents.StageSealed> listener = event -> {
            if (event.hearth().equals(abs)) {
                sealed.add(event);
            }
        };
        List<HearthStatePayload> panels = new ArrayList<>();
        HearthEvents.STAGE_SEALED.add(listener);
        try (Said said = new Said()) {
            HearthOfferings.panelSender = (who, sent) -> panels.add(sent);
            ServerPlayer visitor = player(helper, cast, 4.5, 2.5);
            build(helper, FIRE, 2);
            helper.setBlock(FIRE, Blocks.CAMPFIRE);
            claimed.add(abs);

            // A non-member in someone's claim: the claim refuses the click first.
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            InteractionResult refused = withSpacing(10, () -> use(visitor, abs, flint));
            helper.assertFalse(refused.consumesAction(), "the claim's answer stands: " + refused);
            helper.assertTrue(level.getBlockState(abs).is(Blocks.CAMPFIRE), "claimed: the campfire stays a campfire");
            helper.assertTrue(flint.getDamageValue() == 0 && sealed.isEmpty(), "claimed: no durability, nothing sealed");
            helper.assertTrue(said.lines.isEmpty(), "claimed: the Hearth says nothing");

            // Outside the claim the same click lights it.
            claimed.remove(abs);
            HearthBlockEntity hearth = withSpacing(10, () -> light(helper, visitor, FIRE));
            HearthState state = hearth.state();
            state.setCharacter(50);

            // Claimed again: the offering stays in hand and the panel does not open.
            claimed.add(abs);
            ItemStack copper = new ItemStack(Items.COPPER_BLOCK, 2);
            use(visitor, abs, copper);
            helper.assertTrue(copper.getCount() == 2 && state.highestOffering() == 1, "claimed: the copper block is not taken");
            use(visitor, abs, ItemStack.EMPTY);
            helper.assertTrue(panels.isEmpty(), "claimed: the panel does not open");

            claimed.remove(abs);
            use(visitor, abs, copper);
            helper.assertTrue(copper.getCount() == 1 && state.highestOffering() == 2, "unclaimed: the copper block seals stage II");
            helper.assertTrue(sealed.size() == 2, "stages I and II sealed, only outside the claim: " + sealed.size());
        } finally {
            claimed.remove(abs);
            HearthOfferings.panelSender = dev.amman.proficiency.hearth.net.HearthNet::sendState;
            HearthEvents.STAGE_SEALED.remove(listener);
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    // ---- Fix round 2 (2026-10-09): the second review's findings ------------------------------------

    private static final java.util.Set<BlockPos> CANCELED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean cancelRegistered;

    /**
     * A listener on the shared bus, before the Hearth (NORMAL), that cancels clicks on {@link #CANCELED}
     * and leaves the result at PASS, the way a handler that only means "not this click" does.
     */
    private static synchronized void cancelBeforeTheHearth() {
        if (cancelRegistered) {
            return;
        }
        cancelRegistered = true;
        NeoForge.EVENT_BUS.addListener(dev.amman.proficiency.platform.bus.EventPriority.NORMAL, false,
                dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent.RightClickBlock.class, event -> {
                    if (!event.getLevel().isClientSide() && CANCELED.contains(event.getPos())) {
                        event.setCanceled(true);
                    }
                });
    }

    /**
     * Review finding: on Fabric the late phase built a new click event, so a cancel posted before the
     * Hearth's LOWEST listener (left at PASS) was lost and the Hearth lit or took the offering anyway;
     * NeoForge skips a canceled click. Both loaders now refuse it the same way.
     */
    @GameTest(template = T, batch = "hearth_stages_cancel")
    public static void aCancelBeforeTheHearthStopsItOnEveryLoader(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        cancelBeforeTheHearth();
        aClaimRefusesTheClickBeforeTheHearth(helper, CANCELED);
    }

    /**
     * Review finding: a Hearth placed by a command or a test (no lighting, so no first check) was read
     * in its first second, found standing on nothing and fell to stage 0. It is now adopted: its stage
     * counts as paid and its build is first read at the usual times, after which the spec's rule holds.
     */
    @GameTest(template = T, batch = "hearth_stages_adopt", timeoutTicks = 120)
    public static void aHearthSetByHandIsAdoptedNotEmptied(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        Said said = new Said();
        Runnable cleanup = () -> {
            said.close();
            clear(helper, FIRE);
            leave(cast);
        };
        ServerPlayer amman;
        HearthBlockEntity hearth;
        try {
            amman = player(helper, cast, 4.5, 2.5);
            // What /setblock with the block entity's data does: a Hall, with no build and no offering record.
            helper.setBlock(FIRE, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, 4));
            hearth = (HearthBlockEntity) level.getBlockEntity(abs);
            hearth.state().setStage(4);
            hearth.state().setCharacter(50);
            helper.assertTrue(hearth.state().highestOffering() == 0, "setup: no offering recorded");
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        ServerPlayer home = amman;
        HearthBlockEntity placed = hearth;
        // Two Hearth seconds and more, on the real clock.
        helper.runAfterDelay(45, () -> {
            try {
                HearthState state = placed.state();
                helper.assertTrue(state.stage() == 4, "adopted: the stage it was given stands: " + state.stage());
                helper.assertTrue(state.highestOffering() == 4, "adopted: its stage counts as paid: " + state.highestOffering());
                helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 4, "adopted: shown at IV");
                helper.assertFalse(said.has(home, "fell") || said.has(home, "fell.footing"), "adopted: nothing falls");
                CompoundTag lane = placed.laneData("stages");
                helper.assertTrue(lane.contains("lastCheck"), "adopted: it counts as checked");

                // The 2-minute check reads what stands (nothing): the spec's rule from then on, told once.
                lane.putLong("lastCheck", level.getGameTime() - HearthStageCheck.PERIODIC_TICKS);
                HearthStageCheck.second(level, abs, placed);
                helper.assertTrue(state.stage() == 0 && state.highestOffering() == 4,
                        "read: nothing stands, the offerings stay: " + state.stage() + "/" + state.highestOffering());
                helper.assertTrue(level.getBlockState(abs).getValue(HearthBlock.STAGE) == 1, "read: shown at its lowest");
                helper.assertTrue(said.has(home, "fell.footing"), "read: the fall is told, naming the footing");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Review finding: putting a Hearth out ran through the BLOCK_BROKEN hook, which the hearth.enabled
     * switch silences, so with the switch off the feeders lost their charge without a word and the
     * home flag lingered. Putting it out now tells them whatever the switch says.
     */
    @GameTest(template = T, batch = "hearth_stages_break_off", timeoutTicks = 120)
    public static void puttingAHearthOutWithTheSwitchOffStillTellsItsFeeders(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        Said said = new Said();
        boolean was = ProficiencyConfig.hearthEnabled();
        Runnable cleanup = () -> {
            ProficiencyConfig.HEARTH_ENABLED.set(was);
            said.close();
            clear(helper, FIRE);
            leave(cast);
        };
        ServerPlayer amman;
        ServerPlayer second;
        try {
            amman = player(helper, cast, 4.5, 2.5);
            second = player(helper, cast, 2.5, 4.5);
            build(helper, FIRE, 1);
            HearthBlockEntity hearth = withSpacing(10, () -> light(helper, amman, FIRE));
            hearth.state().ledger().add(second.getUUID(), "Rowan", HearthPool.HEARTY, "mining", 40, 1);
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        ServerPlayer breaker = amman;
        ServerPlayer feeder = second;
        helper.runAfterDelay(25, () -> {
            try {
                String dimension = level.dimension().location().toString();
                helper.assertTrue(HearthAttachments.of(breaker).inRange(dimension, level.getGameTime()),
                        "setup: the Hearth flags who is home");
                ProficiencyConfig.HEARTH_ENABLED.set(false);
                said.lines.clear();
                // The break itself, past the hold (its own test covers the hold).
                helper.assertTrue(breaker.gameMode.destroyBlock(abs), "the break goes through");
                helper.assertFalse(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()), "the Hearth is gone");
                helper.assertTrue(said.has(feeder, "put_out"), "switched off: the feeder is still told");
                helper.assertTrue(said.has(breaker, "put_out.self"), "switched off: the breaker is told what it cost");
                helper.assertFalse(HearthAttachments.of(breaker).inRange(dimension, level.getGameTime()),
                        "switched off: nobody stays flagged home at a Hearth that is gone");
                helper.assertTrue(HearthBreaking.sneakTicks(breaker, abs) == -1, "no hold is left behind");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /** A copy of {@code data} whose stage {@code stage} asks for {@code offering}: a pack's change. */
    private static dev.amman.proficiency.hearth.HearthData withOffering(dev.amman.proficiency.hearth.HearthData data,
            int stage, String offering) {
        List<dev.amman.proficiency.hearth.HearthData.Stage> stages = new ArrayList<>(data.stages());
        dev.amman.proficiency.hearth.HearthData.Stage old = stages.get(stage - 1);
        stages.set(stage - 1, new dev.amman.proficiency.hearth.HearthData.Stage(old.build(), offering));
        return new dev.amman.proficiency.hearth.HearthData(data.pools(), data.thresholds(), data.mirrorShare(),
                data.combatShare(), data.hourlyCap(), data.comfortForTier(), data.decay(), data.warmth(),
                data.combatSeconds(), data.tales(), data.provisioned(), data.character(), data.chronicle(), data.feast(),
                data.bell(), List.copyOf(stages), data.comfort(), data.siege());
    }

    /**
     * Review finding: the client decided which clicks on the Hearth to swallow from its own data, which
     * on a dedicated server is the shipped defaults, so a pack's offering was predicted as a placed
     * block. The state payload now carries the six offerings, and the client reads those.
     */
    @GameTest(template = T, batch = "hearth_stages_pack")
    public static void aPacksOfferingsReachTheClientsClick(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        dev.amman.proficiency.hearth.HearthData before = Hearths.data();
        ItemStack emerald = new ItemStack(Items.EMERALD_BLOCK);
        ItemStack copper = new ItemStack(Items.COPPER_BLOCK);
        try {
            // The server's pack: stage II asks for an emerald block.
            Hearths.install(withOffering(before, 2, "minecraft:emerald_block"));
            helper.assertTrue(dev.amman.proficiency.hearth.stages.StageBlocks.offeringStage(Hearths.data(), emerald) == 2,
                    "the server reads the pack's offering (parsed again after the install)");
            helper.assertTrue(dev.amman.proficiency.hearth.stages.StageBlocks.offeringStage(Hearths.data(), copper) == 0,
                    "the default copper block is no offering under that pack");
            List<String> sent = HearthStatePayload.Extra.of(new HearthState(), Hearths.data(), 64).offerings();
            helper.assertTrue(sent.size() == 6 && sent.get(1).equals("minecraft:emerald_block"),
                    "the state payload carries all six offerings: " + sent);

            // A dedicated server's client: its own data is the shipped defaults.
            Hearths.install(before);
            helper.assertTrue(HearthOfferings.answers(copper) && !HearthOfferings.answers(emerald),
                    "setup: from its own data the client would swallow the wrong item");
            HearthOfferings.serverOfferings(sent);
            helper.assertTrue(HearthOfferings.answers(emerald), "with the server's offerings: the emerald block is swallowed");
            helper.assertFalse(HearthOfferings.answers(copper), "with the server's offerings: a copper block is placed as usual");
            helper.assertTrue(HearthOfferings.answers(ItemStack.EMPTY) && HearthOfferings.answers(new ItemStack(Items.FLINT_AND_STEEL)),
                    "the empty hand (panel) and flint and steel are always the server's");
            helper.assertFalse(HearthOfferings.answers(new ItemStack(Items.DIRT)), "anything else is placed as usual");
            HearthOfferings.serverOfferings(List.of());
            helper.assertTrue(HearthOfferings.answers(emerald), "a payload with no offerings changes nothing");
            HearthOfferings.forgetServerOfferings();
            helper.assertTrue(HearthOfferings.answers(copper) && !HearthOfferings.answers(emerald),
                    "leaving the server: back to the local data");
        } finally {
            Hearths.install(before);
            HearthOfferings.forgetServerOfferings();
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_stages_range", timeoutTicks = 260)
    public static void theInRangeFlagIsSetAndCleared(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        String dimension = level.dimension().location().toString();
        ServerPlayer amman = player(helper, cast, 6.5, 2.5);
        Runnable cleanup = () -> {
            clear(helper, FIRE);
            leave(cast);
        };
        try {
            build(helper, FIRE, 1);
            withSpacing(10, () -> light(helper, amman, FIRE));
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        PlayerHearth data = HearthAttachments.of(amman);
        Vec3 home = amman.position();
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(data.inRange(dimension, level.getGameTime()) && data.inRangePos() == abs.asLong(),
                        "inside the cylinder: flagged with this Hearth");
                helper.assertTrue(data.inRangeUntil() <= level.getGameTime() + PlayerHearth.IN_RANGE_TICKS,
                        "the flag is good for 2 s at most");
                // Out of the cylinder (above its top): the flag runs out within 2 s.
                amman.setPos(home.x, home.y + 30, home.z);
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(45, () -> {
                try {
                    helper.assertFalse(data.inRange(dimension, level.getGameTime()), "away: the flag ran out");
                    amman.setPos(home);
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                helper.runAfterDelay(25, () -> {
                    try {
                        helper.assertTrue(data.inRange(dimension, level.getGameTime()), "back: flagged again");
                        // Broken through the normal break: nobody is home there, at once.
                        amman.gameMode.destroyBlock(abs);
                        helper.assertTrue(level.getBlockState(abs).isAir(), "setup: the Hearth is gone");
                        helper.assertFalse(data.inRange(dimension, level.getGameTime()), "broken: the flag is cleared at once");
                        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0))) {
                            drop.discard();
                        }
                        // Lit again, then removed with no break event: the flag runs out by itself.
                        withSpacing(10, () -> light(helper, amman, FIRE));
                    } catch (RuntimeException e) {
                        cleanup.run();
                        throw e;
                    }
                    helper.runAfterDelay(25, () -> {
                        try {
                            helper.assertTrue(data.inRange(dimension, level.getGameTime()), "relit: flagged");
                            helper.setBlock(FIRE, Blocks.AIR);
                        } catch (RuntimeException e) {
                            cleanup.run();
                            throw e;
                        }
                        helper.runAfterDelay(45, () -> {
                            try {
                                helper.assertFalse(data.inRange(dimension, level.getGameTime()),
                                        "a Hearth gone without a break event leaves no one home after 2 s");
                                helper.succeed();
                            } finally {
                                cleanup.run();
                            }
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = T, batch = "hearth_stages_hint")
    public static void aCampfireOnHearthStoneHintsOnce(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 4.5, 2.5);
            build(helper, FIRE, 1);
            helper.setBlock(FIRE, Blocks.CAMPFIRE);
            BlockState campfire = level.getBlockState(abs);
            withSpacing(10, () -> NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(level, abs,
                    Blocks.AIR.defaultBlockState(), campfire, Blocks.POLISHED_ANDESITE.defaultBlockState(), amman)));
            Line hint = said.find(amman, "hint");
            helper.assertTrue(hint != null && hint.actionBar(), "the first campfire on hearth stone hints");
            said.lines.clear();
            withSpacing(10, () -> NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(level, abs,
                    Blocks.AIR.defaultBlockState(), campfire, Blocks.POLISHED_ANDESITE.defaultBlockState(), amman)));
            helper.assertFalse(said.has(amman, "hint"), "only once");
        } finally {
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_stages_off")
    public static void withTheHearthSwitchedOffNothingLightsOrTicks(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        boolean was = ProficiencyConfig.hearthEnabled();
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 4.5, 2.5);
            build(helper, FIRE, 1);
            helper.setBlock(FIRE, Blocks.CAMPFIRE);
            ProficiencyConfig.HEARTH_ENABLED.set(false);
            use(amman, abs, new ItemStack(Items.FLINT_AND_STEEL));
            helper.assertTrue(level.getBlockState(abs).is(Blocks.CAMPFIRE), "switched off: the campfire stays a campfire");
            // An existing Hearth sits as it is: its second flags nobody.
            helper.setBlock(FIRE, HearthBlocks.HEARTH.get().defaultBlockState());
            HearthBlockEntity hearth = (HearthBlockEntity) level.getBlockEntity(abs);
            HearthHooks.onHearthSecond(level, abs, hearth);
            helper.assertFalse(HearthAttachments.of(amman).inRange(level.dimension().location().toString(), level.getGameTime()),
                    "switched off: no one is flagged home");
            use(amman, abs, new ItemStack(Items.COPPER_BLOCK));
            helper.assertTrue(hearth.state().highestOffering() == 0, "switched off: no offering is taken");
            helper.assertTrue(said.lines.isEmpty(), "switched off: the lane says nothing: " + said.lines.size());
        } finally {
            ProficiencyConfig.HEARTH_ENABLED.set(was);
            clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    // ---- The designs against the real tags ---------------------------------------------------------

    /** Every role's default block is in its block tag, and the tags take the variants the spec lists. */
    @GameTest(template = T, batch = "hearth_stages_designs")
    public static void everyRoleTagTakesItsDefaultBlockAndItsVariants(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        for (char role : HearthDesigns.ROLES.toCharArray()) {
            if (HearthDesigns.isFluid(role)) {
                // No block tag: the fluid state decides.
                helper.assertTrue(StageBlocks.roleTag(role) == null, "no tag for fluid role " + role);
                helper.assertTrue(StageBlocks.isFluidSource(role, StageBlocks.defaultState(role)), "the default block of " + role + " is a source");
                helper.assertFalse(StageBlocks.isFluidSource(role == 'v' ? 'V' : 'v', StageBlocks.defaultState(role)), "role " + role + " is its own fluid");
                continue;
            }
            net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag = StageBlocks.roleTag(role);
            helper.assertTrue(tag != null, "a tag for role " + role);
            BlockState state = StageBlocks.defaultState(role);
            helper.assertTrue(state.is(tag), "the default block of " + role + " (" + state.getBlock() + ") is in " + tag.location());
        }
        Object[][] yes = {{'S', Blocks.COBBLESTONE}, {'S', Blocks.DEEPSLATE_TILES}, {'S', Blocks.SANDSTONE}, {'S', Blocks.NETHER_BRICKS},
                {'S', Blocks.QUARTZ_BRICKS}, {'S', Blocks.END_STONE_BRICKS}, {'S', Blocks.PRISMARINE}, {'S', Blocks.MUD_BRICKS},
                {'S', Blocks.TUFF}, {'S', Blocks.POLISHED_BLACKSTONE_BRICKS}, {'S', Blocks.BRICKS}, {'S', Blocks.SMOOTH_BASALT},
                {'s', Blocks.DEEPSLATE_TILE_WALL}, {'s', Blocks.STONE_SLAB}, {'s', Blocks.BRICK_STAIRS}, {'s', Blocks.MUD_BRICK_WALL},
                {'W', Blocks.CRIMSON_STEM}, {'W', Blocks.STRIPPED_BIRCH_WOOD}, {'P', Blocks.CHERRY_PLANKS},
                {'w', Blocks.OAK_FENCE}, {'w', Blocks.SPRUCE_STAIRS}, {'w', Blocks.OAK_FENCE_GATE}, {'w', Blocks.OAK_TRAPDOOR},
                {'N', Blocks.RED_WALL_BANNER}, {'N', Blocks.BLACK_BANNER}, {'c', Blocks.WAXED_OXIDIZED_CUT_COPPER},
                {'c', Blocks.EXPOSED_COPPER}, {'c', Blocks.WAXED_COPPER_BLOCK}, {'l', Blocks.SOUL_LANTERN},
                {'a', Blocks.DAMAGED_ANVIL}, {'u', Blocks.LAVA_CAULDRON},
                {'f', Blocks.FLOWER_POT}, {'f', Blocks.POTTED_OAK_SAPLING}, {'x', Blocks.BARREL}, {'x', Blocks.TRAPPED_CHEST},
                {'C', Blocks.RED_CANDLE}, {'C', Blocks.CYAN_CANDLE}, {'m', Blocks.MOSS_CARPET}, {'m', Blocks.RED_CARPET},
                {'G', Blocks.TINTED_GLASS}, {'G', Blocks.BLUE_STAINED_GLASS_PANE}, {'G', Blocks.GLASS_PANE},
                {'z', Blocks.WITHER_SKELETON_WALL_SKULL}, {'z', Blocks.PLAYER_HEAD}, {'z', Blocks.ZOMBIE_WALL_HEAD},
                {'I', Blocks.BLUE_ICE}, {'O', Blocks.CRYING_OBSIDIAN}, {'L', Blocks.BIRCH_LEAVES}};
        for (Object[] one : yes) {
            helper.assertTrue(((Block) one[1]).defaultBlockState().is(StageBlocks.roleTag((Character) one[0])),
                    one[1] + " should be role " + one[0]);
        }
        Object[][] no = {{'S', Blocks.DIRT}, {'S', Blocks.STONE_STAIRS}, {'s', Blocks.STONE}, {'W', Blocks.OAK_PLANKS},
                {'P', Blocks.OAK_LOG}, {'w', Blocks.OAK_PLANKS}, {'c', Blocks.IRON_BLOCK}, {'c', Blocks.CUT_COPPER_STAIRS},
                {'i', Blocks.GOLD_BLOCK}, {'g', Blocks.IRON_BLOCK}, {'b', Blocks.IRON_TRAPDOOR}, {'k', Blocks.STONE},
                {'x', Blocks.ENDER_CHEST}, {'I', Blocks.ICE}, {'O', Blocks.BEDROCK}, {'n', Blocks.POWDER_SNOW}, {'G', Blocks.WHITE_WOOL},
                {'m', Blocks.WHITE_WOOL}, {'L', Blocks.OAK_LOG}};
        for (Object[] one : no) {
            helper.assertFalse(((Block) one[1]).defaultBlockState().is(StageBlocks.roleTag((Character) one[0])),
                    one[1] + " should not be role " + one[0]);
        }
        helper.succeed();
    }

    /**
     * Every lane's palette: each entry's role is in the legend, its block exists and holds that role (the role's
     * tag, or a source block for the fluids), and so does the block each role the lane's cells use resolves to.
     */
    @GameTest(template = T, batch = "hearth_stages_designs")
    public static void everyLanePaletteBlockMatchesItsRole(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        int checked = 0;
        for (HearthDesigns.Lane lane : HearthDesigns.shipped().lanes()) {
            java.util.Set<Character> roles = new java.util.TreeSet<>(lane.palette().keySet());
            for (int stage = 1; stage <= HearthDesigns.STAGES; stage++) {
                for (HearthDesigns.Cell cell : lane.stage(stage).cells()) {
                    if (cell.role() != '.' && cell.role() != 'F') {
                        roles.add(cell.role());
                    }
                }
            }
            for (char role : roles) {
                helper.assertTrue(HearthDesigns.ROLES.indexOf(role) >= 0, lane.id() + ": palette role " + role + " is in the legend");
                String id = lane.block(role);
                net.minecraft.resources.ResourceLocation location = id == null ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
                helper.assertTrue(location != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(location),
                        lane.id() + ": role " + role + " block " + id + " exists");
                BlockState state = StageBlocks.laneState(lane, role);
                if (HearthDesigns.isFluid(role)) {
                    helper.assertTrue(StageBlocks.isFluidSource(role, state), lane.id() + ": " + id + " is a source for role " + role);
                } else {
                    net.minecraft.tags.TagKey<Block> tag = StageBlocks.roleTag(role);
                    helper.assertTrue(tag != null && state.is(tag), lane.id() + ": " + id + " is not in the tag of role " + role);
                }
                checked++;
            }
        }
        helper.assertTrue(checked > 0, "some palette entries were checked");
        helper.succeed();
    }

    /** Each lane's stage k, built from default blocks, is read as exactly k through the real probe. */
    @GameTest(template = T, batch = "hearth_stages_designs", timeoutTicks = 200)
    public static void everyDesignBuiltWithDefaultBlocksStandsAsItsStage(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        try {
            for (HearthDesigns.Lane lane : HearthDesigns.shipped().lanes()) {
                for (int stage = 1; stage <= 6; stage++) {
                    clear(helper, FIRE);
                    HearthDesignBuilds.place(helper, FIRE, lane.id(), stage);
                    var check = dev.amman.proficiency.hearth.stages.HearthStructure.check(
                            StageBlocks.probe(level, abs, Hearths.data()));
                    helper.assertTrue(check.built() == stage, lane.id() + " stage " + stage + " reads as " + check.built()
                            + ", next problem " + check.problem());
                    if (stage < 6) {
                        helper.assertTrue(check.problem() != null && check.problem().stage() == stage + 1,
                                lane.id() + " " + stage + ": the next stage is the problem");
                    }
                }
                // Blocked air fails: a block in the smoke column.
                clear(helper, FIRE);
                HearthDesignBuilds.place(helper, FIRE, lane.id(), 2);
                HearthDesignBuilds.set(helper, at(FIRE, 0, 1, 0), Blocks.STONE.defaultBlockState());
                helper.assertTrue(dev.amman.proficiency.hearth.stages.HearthStructure.check(
                        StageBlocks.probe(level, abs, Hearths.data())).built() == 0,
                        lane.id() + ": a block in the smoke column fails the stage");
            }
        } finally {
            clear(helper, FIRE);
        }
        helper.succeed();
    }

    /**
     * Water and lava source cells (roles v and V): a stage that wants them fails while the cell is empty, holds
     * flowing water, waterlogged stone or the other fluid, and stands once the source is placed.
     */
    @GameTest(template = T, batch = "hearth_stages_designs", timeoutTicks = 200)
    public static void aStageWithWaterFailsWithoutItAndStandsOnceItIsPlaced(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(FIRE);
        int tested = 0;
        try {
            for (HearthDesigns.Lane lane : HearthDesigns.shipped().lanes()) {
                for (int stage = 1; stage <= 6; stage++) {
                    HearthDesigns.Stage design = lane.stage(stage);
                    List<HearthDesigns.Cell> fluids = design.cells().stream()
                            .filter(c -> HearthDesigns.isFluid(c.role())).toList();
                    if (fluids.isEmpty()) {
                        continue;
                    }
                    tested++;
                    String name = lane.id() + " " + stage;
                    clear(helper, FIRE);
                    HearthDesignBuilds.place(helper, FIRE, lane.id(), stage);
                    var probe = StageBlocks.probe(level, abs, Hearths.data());
                    helper.assertTrue(dev.amman.proficiency.hearth.stages.StageGhost.wrong(design, probe) == 0,
                            name + ": built with its sources it stands");
                    HearthDesigns.Cell cell = fluids.get(0);
                    boolean water = cell.role() == 'v';
                    BlockPos rel = at(FIRE, cell.dx(), cell.dy(), cell.dz());
                    BlockState source = (water ? Blocks.WATER : Blocks.LAVA).defaultBlockState();
                    BlockState[] wrong = {
                            Blocks.AIR.defaultBlockState(),
                            (water ? Blocks.WATER : Blocks.LAVA).defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1),
                            (water ? Blocks.LAVA : Blocks.WATER).defaultBlockState(),
                            Blocks.OAK_SLAB.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true),
                            Blocks.STONE.defaultBlockState()};
                    for (BlockState bad : wrong) {
                        HearthDesignBuilds.set(helper, rel, bad);
                        helper.assertTrue(dev.amman.proficiency.hearth.stages.StageGhost.wrong(design, probe) >= 1,
                                name + ": " + bad + " at the " + (water ? "water" : "lava") + " cell fails the stage");
                    }
                    HearthDesignBuilds.set(helper, rel, source);
                    helper.assertTrue(dev.amman.proficiency.hearth.stages.StageGhost.wrong(design, probe) == 0,
                            name + ": the source placed, the stage stands again");
                }
            }
        } finally {
            clear(helper, FIRE);
        }
        helper.assertTrue(tested > 0, "the shipped designs have water or lava cells");
        helper.succeed();
    }
}
