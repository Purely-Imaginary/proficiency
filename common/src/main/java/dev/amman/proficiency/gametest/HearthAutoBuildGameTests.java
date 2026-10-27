package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthAutoBuild;
import dev.amman.proficiency.hearth.stages.HearthChunks;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import dev.amman.proficiency.hearth.stages.StageGhost;
import dev.amman.proficiency.hearth.stages.StageText;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Hearth's self-build in a real world: it builds the next stage block by block from a chest in range, digs
 * natural ground out of the way into that chest, waits when a material runs out and goes on once it is restocked,
 * pays no XP, and refuses to start while a player's block stands in a stage cell. The order rules are unit tested
 * in core ({@code AutoBuildTest}). Each test has its own batch, like the other Hearth tests.
 */
public final class HearthAutoBuildGameTests {

    private static final String T = "proficiency:empty";
    private static final BlockPos FIRE = new BlockPos(2, 2, 2);
    /** Outside the 9x9 box, inside the Hearth's range. */
    private static final BlockPos CHEST = new BlockPos(8, 2, 2);
    private static final String LANE = HearthDesignBuilds.LANE;

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HearthAutoBuildGameTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, List<ServerPlayer> cast, double x, double z) {
        ServerPlayer player = MockPlayers.make(helper);
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

    private static void clearStage(GameTestHelper helper) {
        Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
            if (other.position().distanceTo(origin) < 96) {
                other.setPos(other.getX(), other.getY() + 400, other.getZ());
            }
        }
    }

    private static HearthBlockEntity light(GameTestHelper helper, ServerPlayer player) {
        int before = ProficiencyConfig.hearthMinSpacing();
        ProficiencyConfig.HEARTH_MIN_SPACING.set(10);
        try {
            helper.setBlock(FIRE, Blocks.CAMPFIRE);
            BlockPos abs = helper.absolutePos(FIRE);
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            player.setItemInHand(InteractionHand.MAIN_HAND, flint);
            player.gameMode.useItemOn(player, player.serverLevel(), flint, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            if (!(helper.getLevel().getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
                throw new GameTestAssertException("the campfire should have become a Hearth");
            }
            return hearth;
        } finally {
            ProficiencyConfig.HEARTH_MIN_SPACING.set(before);
        }
    }

    private static int laneIndex() {
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        for (int i = 0; i < lanes.size(); i++) {
            if (lanes.get(i).id().equals(LANE)) {
                return i;
            }
        }
        throw new GameTestAssertException("no lane " + LANE);
    }

    private static List<StageGhost.Cell> diff(GameTestHelper helper, int stage) {
        return StageGhost.diff(HearthDesigns.shipped().lane(LANE).stage(stage),
                StageBlocks.probe(helper.getLevel(), helper.absolutePos(FIRE), Hearths.data()));
    }

    /** Item to count: the lane's own block for every cell the stage still needs. */
    private static Map<Item, Integer> needs(GameTestHelper helper, int stage) {
        HearthDesigns.Lane lane = HearthDesigns.shipped().lane(LANE);
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (StageGhost.Cell cell : diff(helper, stage)) {
            if (cell.kind() != StageGhost.Kind.MISSING) {
                continue;
            }
            Item item = HearthDesigns.isFluid(cell.role()) ? (cell.role() == 'v' ? Items.WATER_BUCKET : Items.LAVA_BUCKET)
                    : StageBlocks.laneState(lane, cell.role()).getBlock().asItem();
            out.merge(item, 1, Integer::sum);
        }
        return out;
    }

    private static void fill(ChestBlockEntity chest, Map<Item, Integer> items) {
        int slot = 0;
        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            int left = entry.getValue();
            while (left > 0) {
                int n = Math.min(left, new ItemStack(entry.getKey()).getMaxStackSize());
                while (!chest.getItem(slot).isEmpty()) {
                    slot++;
                }
                chest.setItem(slot, new ItemStack(entry.getKey(), n));
                left -= n;
            }
        }
    }

    private static int count(ChestBlockEntity chest, Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                n += chest.getItem(i).getCount();
            }
        }
        return n;
    }

    /**
     * Every skill's level and XP but the two that pay for time, not for an act: Homebuilder's share pot pays a part
     * of the lighting's XP up to 5 s later, and Nightwalker pays for standing in the dark when the test world is at
     * night. Neither has a block source, so no other skill moving means the build paid nothing.
     */
    private static String allXp(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        StringBuilder out = new StringBuilder();
        for (Skill skill : Skill.values()) {
            if (skill != Skill.HOMEBUILDER && skill != Skill.NIGHTWALKER) {
                out.append(skill).append('=').append(skills.level(skill)).append('/').append(skills.xp(skill)).append(' ');
            }
        }
        return out.toString();
    }

    /** Captures the lane's lines while a test runs. */
    private static final class Said implements StageText.Sink, AutoCloseable {
        final List<String> keys = new ArrayList<>();

        Said() {
            StageText.sink = this;
        }

        @Override
        public void send(ServerPlayer player, Component message, boolean actionBar) {
            if (message.getContents() instanceof TranslatableContents t) {
                keys.add(t.getKey());
            }
        }

        boolean has(String key) {
            return keys.contains("proficiency.hearth.build." + key);
        }

        @Override
        public void close() {
            StageText.sink = StageText.PLAYER;
        }
    }

    @GameTest(template = T, batch = "hearth_autobuild", timeoutTicks = 3000)
    public static void theHearthBuildsTheNextStageFromAChestAndPaysNoXp(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        HearthAutoBuild.secondsOverride = 1;
        List<ServerPlayer> cast = new ArrayList<>();
        Said said = new Said();
        ServerPlayer amman = player(helper, cast, 2.5, 7.5);
        HearthDesignBuilds.place(helper, FIRE, 1);
        HearthBlockEntity hearth = light(helper, amman);
        BlockPos abs = helper.absolutePos(FIRE);

        // Natural ground in a cell the stage wants: dug out, its drop goes into the chest.
        StageGhost.Cell dug = diff(helper, 2).get(0);
        Map<Item, Integer> needs = needs(helper, 2);
        helper.setBlock(FIRE.offset(dug.dx(), dug.dy(), dug.dz()), Blocks.DIRT);
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CHEST));
        // Everything but one material: the build waits for it, then goes on once the chest has it.
        Item held = needs.keySet().iterator().next();
        int heldCount = needs.remove(held);
        fill(chest, needs);
        int total = diff(helper, 2).size();
        String xpBefore = allXp(amman);

        HearthAutoBuild.start(amman, abs, laneIndex());
        helper.assertTrue(said.has("started"), "the start is told: " + said.keys);
        boolean[] restocked = {false};
        helper.succeedWhen(() -> {
            HearthStatePayload.Build status = HearthAutoBuild.status(hearth);
            if (!restocked[0]) {
                if (status.reason() == 1) {
                    helper.assertTrue(!status.missing().isEmpty(), "it names what it waits for");
                    helper.assertTrue(said.has("wait.material"), "the wait is told");
                    helper.assertTrue(HearthChunks.isHeld(helper.getLevel(), abs), "a running build holds the chunk");
                    helper.assertTrue(status.held(), "the panel is told the build goes on while away");
                    fill(chest, Map.of(held, heldCount));
                    restocked[0] = true;
                }
                throw new GameTestAssertException("waiting for the held material to be missed");
            }
            helper.assertTrue(diff(helper, 2).isEmpty(), "stage II stands: " + diff(helper, 2).size() + " of " + total + " left");
            helper.assertTrue(!status.on() && said.has("done"), "the build ends and says so");
            helper.assertTrue(!HearthChunks.isHeld(helper.getLevel(), abs), "a finished build lets the chunk go");
            helper.assertTrue(hearth.state().stage() == 1, "the offering stays the player's: still stage I");
            helper.assertTrue(count(chest, Items.DIRT) >= 1, "the dug dirt went into the chest");
            for (Item item : needs.keySet()) {
                if (item != Items.WATER_BUCKET && item != Items.LAVA_BUCKET) {
                    helper.assertTrue(count(chest, item) == 0, "every " + item + " was used");
                }
            }
            helper.assertTrue(allXp(amman).equals(xpBefore), "the self-build pays no XP: " + xpBefore + " -> " + allXp(amman));
            said.close();
            HearthAutoBuild.secondsOverride = 0;
            HearthDesignBuilds.clear(helper, FIRE);
            helper.setBlock(CHEST, Blocks.AIR);
            leave(cast);
        });
    }

    @GameTest(template = T, batch = "hearth_autobuild_foreign")
    public static void aPlayersBlockInAStageCellRefusesTheStart(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 2.5, 7.5);
            HearthDesignBuilds.place(helper, FIRE, 1);
            HearthBlockEntity hearth = light(helper, amman);
            StageGhost.Cell cell = diff(helper, 2).get(0);
            Block planted = cell.role() == 'P' ? Blocks.GLASS : Blocks.OAK_PLANKS;
            helper.setBlock(FIRE.offset(cell.dx(), cell.dy(), cell.dz()), planted);
            HearthAutoBuild.start(amman, helper.absolutePos(FIRE), laneIndex());
            helper.assertTrue(said.has("foreign"), "the player's block is named: " + said.keys);
            helper.assertTrue(!HearthAutoBuild.status(hearth).on(), "nothing starts");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(FIRE.offset(cell.dx(), cell.dy(), cell.dz())))
                    .is(planted), "the player's block stays");
        } finally {
            HearthDesignBuilds.clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_autobuild_choice", timeoutTicks = 3000)
    public static void aChosenBlockIsTheOnlyKindItTakes(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        HearthAutoBuild.secondsOverride = 1;
        List<ServerPlayer> cast = new ArrayList<>();
        Said said = new Said();
        ServerPlayer amman = player(helper, cast, 2.5, 7.5);
        HearthDesignBuilds.place(helper, FIRE, 1);
        HearthBlockEntity hearth = light(helper, amman);
        BlockPos abs = helper.absolutePos(FIRE);
        Map<Item, Integer> needs = needs(helper, 2);
        Item lanes = StageBlocks.laneState(HearthDesigns.shipped().lane(LANE), 'S').getBlock().asItem();
        int stone = needs.remove(lanes);
        // Far more granite than deepslate bricks: without the choice the build would take granite.
        needs.put(Items.GRANITE, 64);
        needs.put(Items.DEEPSLATE_BRICKS, stone);
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CHEST));
        fill(chest, needs);
        HearthAutoBuild.choose(amman, abs, 'S', "minecraft:deepslate_bricks");
        HearthAutoBuild.choose(amman, abs, 'S', "minecraft:oak_planks");
        HearthAutoBuild.start(amman, abs, laneIndex());
        helper.succeedWhen(() -> {
            helper.assertTrue(diff(helper, 2).isEmpty(), "stage II stands: " + diff(helper, 2).size() + " left");
            int bricks = 0;
            for (HearthDesigns.Cell cell : HearthDesigns.shipped().lane(LANE).stage(2).cells()) {
                if (cell.role() != 'S') {
                    continue;
                }
                net.minecraft.world.level.block.state.BlockState here = helper.getLevel().getBlockState(abs.offset(cell.dx(), cell.dy(), cell.dz()));
                helper.assertTrue(!here.is(Blocks.GRANITE), "no granite at " + cell);
                if (here.is(Blocks.DEEPSLATE_BRICKS)) {
                    bricks++;
                }
            }
            helper.assertTrue(bricks == stone, "every new stone cell is deepslate bricks: " + bricks + " of " + stone);
            helper.assertTrue(count(chest, Items.GRANITE) == 64, "the granite is untouched");
            helper.assertTrue(count(chest, Items.DEEPSLATE_BRICKS) == 0, "the chosen bricks were used");
            said.close();
            HearthAutoBuild.secondsOverride = 0;
            HearthDesignBuilds.clear(helper, FIRE);
            helper.setBlock(CHEST, Blocks.AIR);
            leave(cast);
        });
    }

    @GameTest(template = T, batch = "hearth_autobuild_ticket_stop")
    public static void aRunningBuildHoldsTheChunkAndAStopReleasesIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 2.5, 7.5);
            HearthDesignBuilds.place(helper, FIRE, 1);
            HearthBlockEntity hearth = light(helper, amman);
            BlockPos abs = helper.absolutePos(FIRE);
            helper.assertTrue(!HearthChunks.isHeld(helper.getLevel(), abs), "no ticket before a build");
            HearthAutoBuild.start(amman, abs, laneIndex());
            helper.assertTrue(HearthChunks.isHeld(helper.getLevel(), abs), "the start takes the chunk");
            HearthAutoBuild.stop(amman, abs);
            helper.assertTrue(!HearthChunks.isHeld(helper.getLevel(), abs), "the stop lets it go");
            helper.assertTrue(!HearthAutoBuild.status(hearth).held(), "and the panel stops saying so");
            boolean before = ProficiencyConfig.hearthBuildKeepsChunk();
            ProficiencyConfig.HEARTH_BUILD_KEEPS_CHUNK.set(false);
            try {
                HearthAutoBuild.start(amman, abs, laneIndex());
                helper.assertTrue(!HearthChunks.isHeld(helper.getLevel(), abs), "off in the config: no ticket");
                HearthAutoBuild.stop(amman, abs);
            } finally {
                ProficiencyConfig.HEARTH_BUILD_KEEPS_CHUNK.set(before);
            }
        } finally {
            HearthDesignBuilds.clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_autobuild_ticket_wait", timeoutTicks = 3000)
    public static void aLongWaitReleasesTheChunkAndAPlacedBlockTakesItAgain(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        HearthAutoBuild.secondsOverride = 1;
        int limit = ProficiencyConfig.hearthBuildChunkWaitMinutes();
        ProficiencyConfig.HEARTH_BUILD_CHUNK_WAIT.set(0);
        List<ServerPlayer> cast = new ArrayList<>();
        Said said = new Said();
        ServerPlayer amman = player(helper, cast, 2.5, 7.5);
        HearthDesignBuilds.place(helper, FIRE, 1);
        light(helper, amman);
        BlockPos abs = helper.absolutePos(FIRE);
        HearthAutoBuild.start(amman, abs, laneIndex());
        boolean[] stocked = {false};
        helper.succeedWhen(() -> {
            if (!stocked[0]) {
                // No chest: the build waits for material; past the (zero) limit it lets the chunk go.
                helper.assertTrue(said.has("wait.material"), "the build waits first: " + said.keys);
                helper.assertTrue(!HearthChunks.isHeld(helper.getLevel(), abs), "past the wait limit the chunk is released");
                helper.setBlock(CHEST, Blocks.CHEST);
                fill((ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CHEST)), needs(helper, 2));
                stocked[0] = true;
                throw new GameTestAssertException("restocked, waiting for a block to be placed");
            }
            helper.assertTrue(HearthChunks.isHeld(helper.getLevel(), abs), "a placed block takes the chunk again");
            ProficiencyConfig.HEARTH_BUILD_CHUNK_WAIT.set(limit);
            HearthChunks.release(helper.getLevel(), abs);
            said.close();
            HearthAutoBuild.secondsOverride = 0;
            HearthAutoBuild.stop(amman, abs);
            HearthDesignBuilds.clear(helper, FIRE);
            helper.setBlock(CHEST, Blocks.AIR);
            leave(cast);
        });
    }

    @GameTest(template = T, batch = "hearth_autobuild_ticket_broken")
    public static void aBrokenHearthLetsTheChunkGo(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        clearStage(helper);
        List<ServerPlayer> cast = new ArrayList<>();
        try (Said said = new Said()) {
            ServerPlayer amman = player(helper, cast, 2.5, 7.5);
            HearthDesignBuilds.place(helper, FIRE, 1);
            light(helper, amman);
            BlockPos abs = helper.absolutePos(FIRE);
            HearthAutoBuild.start(amman, abs, laneIndex());
            helper.assertTrue(HearthChunks.isHeld(helper.getLevel(), abs), "held while running");
            dev.amman.proficiency.hearth.HearthIndexData.forget(helper.getLevel(), abs);
            helper.assertTrue(!HearthChunks.isHeld(helper.getLevel(), abs), "the Hearth is gone: the ticket is too");
        } finally {
            HearthDesignBuilds.clear(helper, FIRE);
            leave(cast);
        }
        helper.succeed();
    }
}
