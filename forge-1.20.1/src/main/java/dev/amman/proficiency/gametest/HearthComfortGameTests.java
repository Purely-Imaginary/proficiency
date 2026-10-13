package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthDataLoader;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.comfort.ComfortRequests;
import dev.amman.proficiency.hearth.comfort.ComfortScanner;
import dev.amman.proficiency.hearth.comfort.ComfortTags;
import dev.amman.proficiency.hearth.comfort.HearthComfort;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import dev.amman.proficiency.skill.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The comfort lane in a real world, on both loaders (docs/HEARTH-SPEC.md, "Comfort" and abuse checks
 * 4 and 15): the Comfort and Character scan counts placed blocks only, indoors only for Comfort, never
 * the contents of a chest, with {@code placedBlocksPayXp} on or off; the kind tags hold what the spec
 * lists; the Hearth rescans itself after a placement, a break and an explosion.
 *
 * <p>The template's floor is the layer at y = 1 (y = 0 is the structure block's own level), so
 * furniture stands at y = 2, the roof is a layer of stone slabs at y = 5, and a room is three blocks
 * high. Every test has its own batch and a {@link Fixture} that puts everything back (blocks, marks,
 * entities, the Hearth, the data, the option, the forced chunks): finished and failed tests alike leave
 * their structure standing, and a neighbour's furniture or Hearth is "in range" of this tiny template.
 * The furniture stands on the cells of the 5x5 floor that are no part of the Hearth's own structure (a Hearth
 * that stands as no design has only its fire as one: the middle); one test builds a design on purpose.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HearthComfortGameTests {

    private static final String T = "empty";
    /** Furniture stands on the floor, one layer above the structure block's level. */
    private static final int Y = 2;
    /** The roof: a layer of stone slabs, three blocks above the floor. */
    private static final int ROOF = 5;
    /** The Hearth, in the middle of the 5x5 template. */
    private static final BlockPos HEARTH = new BlockPos(2, Y, 2);

    private HearthComfortGameTests() {
    }

    // ---- The fixture --------------------------------------------------------------------------------------------

    /** One test's world: test data, forced chunks, a player, a Hearth, and a way to put it all back. */
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel level;
        final ServerPlayer player;
        final BlockPos hearthRel;
        private final HearthData dataBefore;
        private final boolean payXpBefore;
        private boolean payXpChanged;
        private final List<long[]> forced = new ArrayList<>();
        private final List<BlockPos> placed = new ArrayList<>();
        private final List<Entity> entities = new ArrayList<>();
        private boolean closed;
        HearthBlockEntity hearth;

        Fixture(GameTestHelper helper, String comfort, BlockPos hearthRel) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.hearthRel = hearthRel;
            this.player = MockPlayers.make(helper);
            this.player.setGameMode(GameType.SURVIVAL);
            this.payXpBefore = ProficiencyConfig.placedBlocksPayXp();
            this.dataBefore = Hearths.data();
            Hearths.install(HearthDataLoader.parse("test", dataJson(comfort)).data());
            BlockPos abs = helper.absolutePos(hearthRel);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int cx = (abs.getX() >> 4) + dx;
                    int cz = (abs.getZ() >> 4) + dz;
                    level.setChunkForced(cx, cz, true);
                    level.getChunk(cx, cz);
                    forced.add(new long[] {cx, cz});
                }
            }
            clearLeftovers(abs);
        }

        /**
         * A guard, not the cleanup: a test that places marked blocks takes them away again when it
         * ends (the charge lane's hold test does, through its {@code Home}). Should one still stand in
         * this Hearth's cylinder (GameTest cells sit a few blocks apart, and a finished test's blocks
         * stay), it would count for Comfort and Character here: it is named in the log, so the test
         * that left it can be fixed, and only its marks go (only marked blocks still standing inside
         * the cylinder, exactly what the scan would count). This test's batch holds only this test,
         * so nothing that runs now is touched.
         */
        private void clearLeftovers(BlockPos hearth) {
            dev.amman.proficiency.hearth.HearthGeometry.Range range = dev.amman.proficiency.hearth.HearthRange.range();
            int r = (int) Math.ceil(range.radius());
            List<BlockPos> marked = new ArrayList<>();
            for (int cx = (hearth.getX() - r) >> 4; cx <= (hearth.getX() + r) >> 4; cx++) {
                for (int cz = (hearth.getZ() - r) >> 4; cz <= (hearth.getZ() + r) >> 4; cz++) {
                    PlacedBlocks.forEachPlaced(level, cx, cz, hearth.getY() - range.below(),
                            hearth.getY() + range.above(), (x, y, z, hash, comfortMark) -> {
                                BlockPos at = new BlockPos(x, y, z);
                                if (dev.amman.proficiency.hearth.HearthRange.inHouse(hearth, at)
                                        && PlacedBlocks.hash(level.getBlockState(at)) == hash) {
                                    marked.add(at);
                                }
                            });
                }
            }
            for (BlockPos pos : marked) {
                dev.amman.proficiency.Proficiency.LOG.warn("GameTest leftover: a marked {} still stands at {}, in range of "
                        + "the comfort test at {}; the test that placed it should take it away", level.getBlockState(pos),
                        pos, hearth);
                PlacedBlocks.clearComfort(level, pos);
                PlacedBlocks.consume(level, pos, level.getBlockState(pos));
            }
        }

        /** Sets the Hearth block (a lit campfire) and returns its block entity. */
        HearthBlockEntity light(int stage) {
            helper.setBlock(hearthRel, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, stage));
            if (level.getBlockEntity(helper.absolutePos(hearthRel)) instanceof HearthBlockEntity entity) {
                entity.state().setStage(stage);
                hearth = entity;
                return entity;
            }
            throw new IllegalStateException("a Hearth block should carry a Hearth block entity");
        }

        /** What a player's placement does: the block goes in, then the shared event every loader posts. */
        void place(BlockPos rel, BlockState state) {
            BlockPos abs = helper.absolutePos(rel);
            BlockState before = level.getBlockState(abs);
            helper.setBlock(rel, state);
            placed.add(rel);
            ForgeTestEvents.place(level, abs, Blocks.STONE.defaultBlockState(), player);
        }

        void place(BlockPos rel, Block block) {
            place(rel, block.defaultBlockState());
        }

        /** As {@link #place(BlockPos, BlockState)}, for another player. */
        void place(ServerPlayer who, BlockPos rel, Block block) {
            BlockPos abs = helper.absolutePos(rel);
            BlockState before = level.getBlockState(abs);
            helper.setBlock(rel, block);
            placed.add(rel);
            ForgeTestEvents.place(level, abs, Blocks.STONE.defaultBlockState(), who);
        }

        /** Something solid under a spot, set at once (not placed by the player): a bell needs it. */
        void support(BlockPos rel) {
            BlockPos below = rel.below();
            helper.setBlock(below, Blocks.STONE);
            placed.add(below);
        }

        /** A roof of stone slabs over the floor, set at once: a roof is no furniture and no event. */
        void roof(int x0, int z0, int x1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos rel = new BlockPos(x, ROOF, z);
                    helper.setBlock(rel, Blocks.STONE_SLAB);
                    placed.add(rel);
                }
            }
        }

        <E extends Entity> E keep(E entity) {
            entities.add(entity);
            return entity;
        }

        void payXp(boolean on) {
            payXpChanged = true;
            ProficiencyConfig.PLACED_BLOCKS_PAY_XP.set(on);
        }

        /** A scan that must succeed: every chunk of the range is forced. */
        void scan() {
            helper.assertTrue(hearth != null && HearthComfort.scanNow(level, hearth),
                    "the scan should have run: its chunks are forced");
        }

        HearthState state() {
            return hearth.state();
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            entities.forEach(Entity::discard);
            for (BlockPos rel : placed) {
                PlacedBlocks.clearComfort(level, helper.absolutePos(rel));
                helper.setBlock(rel, Blocks.AIR);
            }
            helper.setBlock(hearthRel, Blocks.AIR);
            if (payXpChanged) {
                ProficiencyConfig.PLACED_BLOCKS_PAY_XP.set(payXpBefore);
            }
            Hearths.install(dataBefore);
            for (long[] c : forced) {
                level.setChunkForced((int) c[0], (int) c[1], false);
            }
        }
    }

    /** The test data: the comfort section as given (the cooldowns), everything else the defaults. */
    private static String dataJson(String comfort) {
        return comfort == null ? "{}" : "{\"comfort\":" + comfort + "}";
    }

    /** Runs {@code body} after a delay; whatever it throws, the fixture is put back first. */
    private static void after(GameTestHelper helper, Fixture fx, int ticks, Runnable body) {
        helper.runAfterDelay(ticks, () -> {
            try {
                body.run();
            } catch (RuntimeException | Error e) {
                fx.close();
                throw e;
            }
        });
    }

    private static boolean has(HearthState state, String kind) {
        return state.comfortKinds().contains(kind);
    }

    private static String shown(HearthState state) {
        return state.comfort() + " " + state.comfortKinds() + " character " + state.character();
    }

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, Y, z);
    }

    /**
     * Twelve kinds on a 5x5 floor, none on a corner (the Hearth's pillars) or in the middle (its fire):
     * eleven blocks and an item frame on a wall of stone. Returns the frame.
     */
    private static ItemFrame furnish(Fixture fx) {
        fx.place(at(1, 0), Blocks.RED_BED);
        fx.place(at(3, 0), Blocks.OAK_STAIRS);
        fx.place(at(1, 4), Blocks.WHITE_CARPET);
        fx.place(at(3, 4), Blocks.WHITE_BANNER);
        fx.place(at(0, 1), Blocks.CAMPFIRE);
        fx.place(at(0, 3), Blocks.LANTERN);
        fx.place(at(4, 1), Blocks.FLOWER_POT);
        fx.place(at(4, 3), Blocks.BOOKSHELF);
        fx.place(at(1, 2), Blocks.JUKEBOX);
        fx.place(at(3, 3), Blocks.BARREL);
        fx.place(at(2, 1), Blocks.SKELETON_SKULL);
        // Art is an entity: an item frame holding an item, hung on a stone block.
        fx.place(at(4, 2), Blocks.STONE);
        return fx.keep(frame(fx, at(3, 2), Direction.WEST, new ItemStack(Items.COMPASS)));
    }

    private static ItemFrame frame(Fixture fx, BlockPos rel, Direction facing, ItemStack held) {
        ItemFrame frame = new ItemFrame(fx.level, fx.helper.absolutePos(rel), facing);
        frame.setItem(held, false);
        fx.level.addFreshEntity(frame);
        return frame;
    }

    private static ArmorStand armourStand(Fixture fx, BlockPos rel, boolean armoured) {
        BlockPos abs = fx.helper.absolutePos(rel);
        ArmorStand stand = new ArmorStand(fx.level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        if (armoured) {
            stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        }
        fx.level.addFreshEntity(stand);
        return fx.keep(stand);
    }

    // ---- The tags ---------------------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_tags")
    public static void theKindTagsHoldTheSpecsVanillaMembers(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Map<String, List<Block>> expected = new HashMap<>();
        expected.put("bed", List.of(Blocks.RED_BED, Blocks.WHITE_BED));
        expected.put("seat", List.of(Blocks.OAK_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.CRIMSON_STAIRS));
        expected.put("carpet", List.of(Blocks.WHITE_CARPET, Blocks.RED_CARPET, Blocks.MOSS_CARPET));
        expected.put("banner", List.of(Blocks.WHITE_BANNER, Blocks.RED_WALL_BANNER));
        expected.put("fire", List.of(Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE));
        expected.put("light", List.of(Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.CANDLE, Blocks.RED_CANDLE,
                Blocks.SEA_LANTERN, Blocks.GLOWSTONE));
        expected.put("plant", List.of(Blocks.FLOWER_POT, Blocks.POTTED_OAK_SAPLING, Blocks.POTTED_FERN));
        expected.put("books", List.of(Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF, Blocks.LECTERN));
        expected.put("music", List.of(Blocks.JUKEBOX, Blocks.NOTE_BLOCK));
        expected.put("storage", List.of(Blocks.BARREL, Blocks.CHEST, Blocks.DECORATED_POT));
        expected.put("trophy", List.of(Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL, Blocks.WITHER_SKELETON_SKULL,
                Blocks.ZOMBIE_HEAD, Blocks.CREEPER_HEAD, Blocks.PIGLIN_HEAD, Blocks.DRAGON_HEAD, Blocks.PLAYER_HEAD,
                Blocks.PLAYER_WALL_HEAD, HearthBlocks.SIEGE_TROPHY.get()));
        for (String kind : HearthData.DEFAULT_COMFORT_KINDS) {
            for (Block block : expected.getOrDefault(kind, List.of())) {
                helper.assertTrue(block.defaultBlockState().is(ComfortTags.block(kind)), block + " should be a " + kind);
            }
        }
        // Not furniture: a furnace is no fire, a torch no light, a crafting table no storage, plain stone nothing.
        helper.assertFalse(Blocks.FURNACE.defaultBlockState().is(ComfortTags.block("fire")), "a lit furnace does not count");
        helper.assertFalse(Blocks.TORCH.defaultBlockState().is(ComfortTags.block("light")), "a torch is not furniture");
        helper.assertFalse(Blocks.CRAFTING_TABLE.defaultBlockState().is(ComfortTags.block("storage")), "no table kind");
        for (String kind : HearthData.DEFAULT_COMFORT_KINDS) {
            helper.assertFalse(HearthBlocks.HEARTH.get().defaultBlockState().is(ComfortTags.block(kind)),
                    "the Hearth's own block is no " + kind);
            helper.assertFalse(Blocks.STONE.defaultBlockState().is(ComfortTags.block(kind)), "stone is no " + kind);
        }
        // Entities: art and trophy are the two entity kinds.
        helper.assertTrue(EntityType.PAINTING.is(ComfortTags.entity("art")), "paintings are art");
        helper.assertTrue(EntityType.ITEM_FRAME.is(ComfortTags.entity("art")), "item frames are art");
        helper.assertTrue(EntityType.GLOW_ITEM_FRAME.is(ComfortTags.entity("art")), "glow item frames are art");
        helper.assertTrue(EntityType.ARMOR_STAND.is(ComfortTags.entity("trophy")), "armour stands are trophies");
        helper.assertFalse(EntityType.ZOMBIE.is(ComfortTags.entity("art")), "a zombie is no art");
        // Character: filler and the Hearth never count.
        for (Block filler : List.of(Blocks.DIRT, Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.NETHERRACK,
                Blocks.GRAVEL, Blocks.SAND, HearthBlocks.HEARTH.get())) {
            helper.assertTrue(filler.defaultBlockState().is(ComfortTags.CHARACTER_IGNORE), filler + " never counts for Character");
        }
        helper.assertFalse(Blocks.OAK_PLANKS.defaultBlockState().is(ComfortTags.CHARACTER_IGNORE), "planks count");
        // The twelve kinds are the data's kinds.
        helper.assertTrue(Hearths.data().comfort().kinds().equals(HearthData.DEFAULT_COMFORT_KINDS), "the twelve kinds");
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_entries")
    public static void aCharacterEntryIsTheItemThatPlacesTheBlock(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        helper.assertTrue("minecraft:torch".equals(ComfortScanner.characterEntry(Blocks.TORCH.defaultBlockState()))
                && "minecraft:torch".equals(ComfortScanner.characterEntry(Blocks.WALL_TORCH.defaultBlockState())),
                "a torch and its wall twin are one entry");
        helper.assertTrue("minecraft:lantern".equals(ComfortScanner.characterEntry(Blocks.LANTERN.defaultBlockState()))
                && "minecraft:lantern".equals(ComfortScanner.characterEntry(Blocks.LANTERN.defaultBlockState()
                        .setValue(LanternBlock.HANGING, true))), "so are a lantern and the one that hangs");
        helper.assertTrue("minecraft:oak_planks".equals(ComfortScanner.characterEntry(Blocks.OAK_PLANKS.defaultBlockState())),
                "planks are planks");
        helper.assertTrue("minecraft:red_banner".equals(ComfortScanner.characterEntry(Blocks.RED_WALL_BANNER.defaultBlockState())),
                "a wall banner is the banner item");
        for (Block none : List.of(Blocks.DIRT, Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.NETHERRACK,
                Blocks.GRAVEL, Blocks.SAND, HearthBlocks.HEARTH.get(), Blocks.WATER, Blocks.FIRE, Blocks.AIR,
                Blocks.NETHER_PORTAL)) {
            helper.assertTrue(ComfortScanner.characterEntry(none.defaultBlockState()) == null, none + " counts for nothing");
        }
        ComfortScanner.Worth chair = ComfortScanner.worthOf(Blocks.OAK_STAIRS.defaultBlockState());
        helper.assertTrue(chair.kinds().equals(List.of("seat")) && "minecraft:oak_stairs".equals(chair.entry()), "worth of a chair");
        helper.assertTrue(ComfortScanner.worthOf(Blocks.STONE.defaultBlockState()).kinds().isEmpty(), "stone is no kind");
        helper.succeed();
    }

    // ---- The mark ----------------------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_mark")
    public static void everyPlayerPlacementIsMarkedAndABreakClearsIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            ServerLevel level = fx.level;
            BlockPos rel = at(1, 1);
            BlockPos abs = helper.absolutePos(rel);
            fx.place(rel, Blocks.OAK_PLANKS);
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, abs), "a player's placement is marked");
            // The same block put there by something that is no player is not.
            BlockPos otherRel = at(2, 1);
            BlockPos other = helper.absolutePos(otherRel);
            helper.setBlock(otherRel, Blocks.OAK_PLANKS);
            fx.placed.add(otherRel);
            ForgeTestEvents.place(level, other, Blocks.STONE.defaultBlockState(), null);
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, other), "an enderman's block is not placed by a player");
            // A door marks both halves.
            BlockState door = Blocks.OAK_DOOR.defaultBlockState();
            BlockPos lowerRel = at(3, 3);
            BlockPos upperRel = lowerRel.above();
            helper.setBlock(upperRel, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            fx.place(lowerRel, door);
            fx.placed.add(upperRel);
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, helper.absolutePos(lowerRel))
                    && PlacedBlocks.rawComfortMarked(level, helper.absolutePos(upperRel)), "both door halves");
            // Breaking it takes the mark away.
            fx.player.gameMode.destroyBlock(abs);
            helper.assertTrue(level.getBlockState(abs).isAir(), "setup: the break went through");
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, abs), "a break clears the mark");
            // Breaking one half of a door takes the mark of the other half too: the game removes it with no event.
            fx.player.gameMode.destroyBlock(helper.absolutePos(lowerRel));
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, helper.absolutePos(lowerRel)), "the broken half");
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, helper.absolutePos(upperRel)),
                    "and the half the game took with it");
        } finally {
            fx.close();
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_fallers")
    public static void theMarkRidesOnAFallingBlock(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos to = helper.absolutePos(at(1, 1));
        BlockState sand = Blocks.SAND.defaultBlockState();
        boolean payXpBefore = ProficiencyConfig.placedBlocksPayXp();
        try {
            // Both marks (the default): both ride along.
            ProficiencyConfig.PLACED_BLOCKS_PAY_XP.set(false);
            PlacedBlocks.markComfort(level, from, sand);
            PlacedBlocks.markPlacement(level, from, sand);
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, from) && PlacedBlocks.rawMarked(level, from), "setup");
            int both = PlacedBlocks.pickUp(level, from, sand);
            helper.assertTrue(both == (PlacedBlocks.MARK_GATHERING | PlacedBlocks.MARK_COMFORT),
                    "a block with both marks that starts to fall takes both: " + both);
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, from) || PlacedBlocks.rawMarked(level, from),
                    "the origin loses its marks");
            PlacedBlocks.land(level, to, sand, both);
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, to) && PlacedBlocks.rawMarked(level, to),
                    "the landed block is marked again, both ways");
            PlacedBlocks.clearComfort(level, to);
            PlacedBlocks.consume(level, to, sand);

            // placedBlocksPayXp on: only the comfort mark exists, and only it comes back. A landed block
            // that gained a gathering mark would stop paying XP the day the option is switched off.
            ProficiencyConfig.PLACED_BLOCKS_PAY_XP.set(true);
            PlacedBlocks.markPlacement(level, from, sand);
            PlacedBlocks.markComfort(level, from, sand);
            helper.assertFalse(PlacedBlocks.rawMarked(level, from), "setup: the option writes no gathering mark");
            int comfortOnly = PlacedBlocks.pickUp(level, from, sand);
            helper.assertTrue(comfortOnly == PlacedBlocks.MARK_COMFORT, "only the comfort mark was there: " + comfortOnly);
            PlacedBlocks.land(level, to, sand, comfortOnly);
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, to), "the comfort mark landed");
            helper.assertFalse(PlacedBlocks.rawMarked(level, to), "and no gathering mark was invented for it");
            PlacedBlocks.clearComfort(level, to);

            // A block from before the comfort mark: its gathering mark alone rides.
            ProficiencyConfig.PLACED_BLOCKS_PAY_XP.set(false);
            PlacedBlocks.markPlacement(level, from, sand);
            int gatheringOnly = PlacedBlocks.pickUp(level, from, sand);
            helper.assertTrue(gatheringOnly == PlacedBlocks.MARK_GATHERING, "only the gathering mark: " + gatheringOnly);
            PlacedBlocks.land(level, to, sand, gatheringOnly);
            helper.assertTrue(PlacedBlocks.rawMarked(level, to) && !PlacedBlocks.rawComfortMarked(level, to),
                    "it lands as it left");
            PlacedBlocks.consume(level, to, sand);
            helper.assertTrue(PlacedBlocks.pickUp(level, from, sand) == 0, "an unmarked block carries nothing");
        } finally {
            ProficiencyConfig.PLACED_BLOCKS_PAY_XP.set(payXpBefore);
            for (BlockPos pos : List.of(from, to)) {
                PlacedBlocks.clearComfort(level, pos);
                PlacedBlocks.consume(level, pos, sand);
                PlacedBlocks.clearRefunded(level, pos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_sweep")
    public static void aChunkLoadDropsTheComfortMarksOfBlocksThatAreGone(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos goneRel = at(1, 0);
        BlockPos swappedRel = at(3, 0);
        BlockPos keptRel = at(1, 4);
        BlockPos gone = helper.absolutePos(goneRel);
        BlockPos swapped = helper.absolutePos(swappedRel);
        BlockPos kept = helper.absolutePos(keptRel);
        try {
            // Marked, and then the block went with no player event: burnt, blown up, popped off, taken by an enderman.
            PlacedBlocks.markComfort(level, gone, Blocks.WHITE_CARPET.defaultBlockState());
            PlacedBlocks.markComfort(level, swapped, Blocks.BOOKSHELF.defaultBlockState());
            helper.setBlock(swappedRel, Blocks.STONE);
            helper.setBlock(keptRel, Blocks.BARREL);
            PlacedBlocks.markComfort(level, kept, Blocks.BARREL.defaultBlockState());
            // A block mid-push is a moving piston for a few ticks; its mark is not stale.
            BlockPos movingRel = at(3, 3);
            BlockPos moving = helper.absolutePos(movingRel);
            helper.setBlock(movingRel, Blocks.MOVING_PISTON);
            PlacedBlocks.markComfort(level, moving, Blocks.STONE.defaultBlockState());
            // The loaders post the shared event when a chunk finishes loading.
            for (LevelChunk chunk : new LinkedHashSet<>(List.of(level.getChunkAt(gone), level.getChunkAt(swapped),
                    level.getChunkAt(kept), level.getChunkAt(moving)))) {
                ForgeTestEvents.chunkLoaded(chunk);
            }
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, gone), "the mark of a block that is gone is dropped");
            helper.assertFalse(PlacedBlocks.rawComfortMarked(level, swapped), "so is the one of a block replaced by another");
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, kept), "a block that still stands keeps it");
            helper.assertTrue(PlacedBlocks.rawComfortMarked(level, moving), "a block in a piston's grip too");
            helper.assertTrue(PlacedBlocks.sweepComfort(level, level.getChunkAt(kept)) == 0, "a second sweep finds nothing");
        } finally {
            for (BlockPos rel : List.of(goneRel, swappedRel, keptRel, at(3, 3))) {
                PlacedBlocks.clearComfort(level, helper.absolutePos(rel));
                helper.setBlock(rel, Blocks.AIR);
            }
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_payxp", timeoutTicks = 120)
    public static void placedBlocksPayXpOnStillCountsAndWritesNoGatheringMark(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.payXp(true);
            helper.assertTrue(ProficiencyConfig.placedBlocksPayXp(), "setup: the option is on");
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            after(helper, fx, 8, () -> {
                BlockPos chair = at(1, 1);
                BlockPos abs = helper.absolutePos(chair);
                fx.place(chair, Blocks.OAK_STAIRS);
                helper.assertTrue(PlacedBlocks.rawComfortMarked(fx.level, abs), "the comfort mark is written");
                helper.assertFalse(PlacedBlocks.rawMarked(fx.level, abs), "the gathering mark is not, as before");
                fx.scan();
                helper.assertTrue(has(fx.state(), "seat"), "a chair still counts: " + shown(fx.state()));
                helper.assertTrue(fx.state().character() == 1, "and so does Character: " + shown(fx.state()));
                fx.close();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    // ---- The lawn and the house (abuse check 4) ------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_lawn")
    public static void aLawnWithAllTwelveKindsUnderTheSkyScoresZero(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.light(1);
            furnish(fx);
            armourStand(fx, at(2, 3), true);
            fx.scan();
            helper.assertTrue(fx.state().comfort() == 0, "a lawn scores 0 whatever stands on it: " + shown(fx.state()));
            helper.assertTrue(fx.state().character() >= 10, "Character counts inside and out: " + shown(fx.state()));
            for (HearthPool pool : HearthPool.VALUES) {
                helper.assertTrue(HearthRules.comfortCap(pool, fx.state().comfort(), Hearths.data())
                        <= (pool == HearthPool.REGEN || pool == HearthPool.HOMECOMING ? 0 : 4), "a lawn lifts no cap");
            }
        } finally {
            fx.close();
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_house", timeoutTicks = 200)
    public static void aRoofedHouseWithAllTwelveKindsScoresTwelve(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        Map<String, UUID> kindPlacers = new HashMap<>();
        Map<String, UUID> idPlacers = new HashMap<>();
        List<String> seenIds = new ArrayList<>();
        Consumer<HearthEvents.ComfortKindSeen> onKind = e -> kindPlacers.put(e.kind(), e.placer());
        Consumer<HearthEvents.CharacterIdSeen> onId = e -> {
            idPlacers.put(e.blockId(), e.placer());
            seenIds.add(e.blockId());
        };
        HearthEvents.COMFORT_KIND_SEEN.add(onKind);
        HearthEvents.CHARACTER_ID_SEEN.add(onId);
        Runnable done = () -> {
            HearthEvents.COMFORT_KIND_SEEN.remove(onKind);
            HearthEvents.CHARACTER_ID_SEEN.remove(onId);
            fx.close();
        };
        try {
            fx.light(1);
            furnish(fx);
            // The furniture goes in first, under the open sky: nothing counts yet.
            fx.scan();
            helper.assertTrue(fx.state().comfort() == 0, "no roof, no Comfort: " + shown(fx.state()));
            fx.roof(0, 0, 4, 4);
            // The player stands at home, so the "kind gained" moment (action bar, chime, sparks) runs for them too.
            BlockPos home = helper.absolutePos(HEARTH);
            fx.player.setPos(home.getX() + 0.5, home.getY(), home.getZ() + 1.5);
            helper.runAfterDelay(10, () -> {
                try {
                    fx.scan();
                    HearthState state = fx.state();
                    helper.assertTrue(state.comfort() == 12, "a roofed house with all twelve scores 12: " + shown(state));
                    helper.assertTrue(HearthComfort.missingKinds(state).isEmpty(), "nothing is missing");
                    // Twelve block ids: eleven pieces of furniture and the stone wall; the slab roof was set, not placed.
                    helper.assertTrue(state.character() == 12, "Character is the distinct placed ids: " + shown(state) + " " + seenIds);
                    for (HearthPool pool : HearthPool.VALUES) {
                        helper.assertTrue(HearthRules.comfortCap(pool, state.comfort(), Hearths.data()) == HearthData.TIERS,
                                "twelve kinds lift every cap");
                    }
                    helper.assertTrue(kindPlacers.size() == 12, "every kind was announced once: " + kindPlacers);
                    UUID amman = fx.player.getUUID();
                    for (String kind : HearthData.DEFAULT_COMFORT_KINDS) {
                        if (kind.equals("art")) {
                            helper.assertTrue(kindPlacers.get(kind) == null, "nothing tracks who hung a frame");
                        } else {
                            helper.assertTrue(amman.equals(kindPlacers.get(kind)),
                                    kind + " was placed by the player, even though it only counted once the roof stood");
                        }
                    }
                    helper.assertTrue(seenIds.contains("minecraft:oak_stairs") && amman.equals(idPlacers.get("minecraft:oak_stairs")),
                            "a Character entry names its placer: " + idPlacers);
                    helper.assertFalse(seenIds.contains("minecraft:stone_slab"), "the roof was set, not placed by a player");

                    // A second scan announces nothing new.
                    int announced = kindPlacers.size() + seenIds.size();
                    fx.scan();
                    helper.assertTrue(kindPlacers.size() + seenIds.size() == announced, "milestones fire once");

                    // The panel's payload carries the cache.
                    HearthStatePayload payload = HearthNet.stateOf(fx.hearth, HearthStatePayload.UPDATE);
                    helper.assertTrue(payload.comfort() == 12 && payload.comfortKinds().size() == 12
                            && payload.character() == 12, "state payload: " + payload.comfort() + " " + payload.character());

                    // Breaking the barrel drops storage; the missing kind is named.
                    fx.player.gameMode.destroyBlock(helper.absolutePos(at(3, 3)));
                    fx.scan();
                    helper.assertTrue(state.comfort() == 11 && !has(state, "storage"), "storage is gone: " + shown(state));
                    helper.assertTrue(HearthComfort.missingKinds(state).equals(List.of("storage")), "the panel names it");
                } catch (RuntimeException | Error e) {
                    done.run();
                    throw e;
                }
                done.run();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            done.run();
            throw e;
        }
    }

    // ---- What does not count --------------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_natural", timeoutTicks = 120)
    public static void blocksNobodyPlacedAndChestContentsNeverCount(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            // A house full of furniture that came with the world: set, never placed by a player.
            for (BlockPos rel : List.of(at(1, 0), at(3, 0), at(1, 4))) {
                fx.placed.add(rel);
            }
            helper.setBlock(at(1, 0), Blocks.RED_BED);
            helper.setBlock(at(3, 0), Blocks.OAK_STAIRS);
            helper.setBlock(at(1, 4), Blocks.BOOKSHELF);
            // A chest a player placed, stuffed with the other kinds as items.
            fx.place(at(3, 4), Blocks.CHEST);
            if (fx.level.getBlockEntity(helper.absolutePos(at(3, 4))) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.RED_BED, 3));
                chest.setItem(1, new ItemStack(Items.OAK_STAIRS, 3));
                chest.setItem(2, new ItemStack(Items.JUKEBOX, 3));
                chest.setItem(3, new ItemStack(Items.LANTERN, 3));
            }
            after(helper, fx, 8, () -> {
                fx.scan();
                HearthState state = fx.state();
                helper.assertTrue(state.comfortKinds().equals(Set.of("storage")),
                        "only the chest itself counts, not what is in it, and nothing that nobody placed: " + shown(state));
                helper.assertTrue(state.character() == 1, "one placed id: " + shown(state));
                fx.close();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    @GameTest(template = T, batch = "hearth_comfort_stale", timeoutTicks = 120)
    public static void aMarkWhoseBlockIsGoneOrReplacedIsDroppedNotCounted(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            BlockPos gone = at(1, 0);
            BlockPos swapped = at(3, 0);
            BlockPos kept = at(1, 4);
            fx.place(gone, Blocks.JUKEBOX);
            fx.place(swapped, Blocks.BOOKSHELF);
            fx.place(kept, Blocks.BARREL);
            // An explosion, fire or a piston removes blocks without any player event; the mark is left behind.
            helper.setBlock(gone, Blocks.AIR);
            helper.setBlock(swapped, Blocks.STONE);
            after(helper, fx, 8, () -> {
                fx.scan();
                HearthState state = fx.state();
                helper.assertTrue(state.comfortKinds().equals(Set.of("storage")),
                        "only the block that is still the one placed counts: " + shown(state));
                helper.assertFalse(PlacedBlocks.rawComfortMarked(fx.level, helper.absolutePos(gone)), "the stale mark is dropped");
                helper.assertFalse(PlacedBlocks.rawComfortMarked(fx.level, helper.absolutePos(swapped)), "so is the replaced one");
                helper.assertTrue(PlacedBlocks.rawComfortMarked(fx.level, helper.absolutePos(kept)), "the live one stays");
                fx.close();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    @GameTest(template = T, batch = "hearth_comfort_legacy", timeoutTicks = 120)
    public static void blocksPlacedBeforeTheComfortMarkStillCountThroughTheGatheringMark(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.payXp(false);
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            BlockPos rel = at(1, 1);
            BlockPos abs = helper.absolutePos(rel);
            helper.setBlock(rel, Blocks.OAK_STAIRS);
            fx.placed.add(rel);
            // 1.4.x: only the gathering mark existed.
            PlacedBlocks.markPlacement(fx.level, abs, Blocks.OAK_STAIRS.defaultBlockState());
            helper.assertTrue(PlacedBlocks.rawMarked(fx.level, abs) && !PlacedBlocks.rawComfortMarked(fx.level, abs), "setup");
            after(helper, fx, 8, () -> {
                fx.scan();
                helper.assertTrue(has(fx.state(), "seat") && fx.state().character() == 1,
                        "an old placed block counts: " + shown(fx.state()));
                helper.assertFalse(PlacedBlocks.rawComfortMarked(fx.level, abs), "and the scan does not invent a mark");
                fx.close();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    @GameTest(template = T, batch = "hearth_comfort_entities", timeoutTicks = 120)
    public static void anItemFrameNeedsAnItemAndAnArmourStandNeedsArmour(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            fx.place(at(4, 1), Blocks.STONE);
            ItemFrame empty = fx.keep(frame(fx, at(3, 1), Direction.WEST, ItemStack.EMPTY));
            ArmorStand naked = armourStand(fx, at(1, 3), false);
            after(helper, fx, 8, () -> {
                fx.scan();
                helper.assertTrue(!has(fx.state(), "art") && !has(fx.state(), "trophy"),
                        "an empty frame and a bare armour stand are not furniture: " + shown(fx.state()));
                empty.setItem(new ItemStack(Items.COMPASS), false);
                naked.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                fx.scan();
                helper.assertTrue(has(fx.state(), "art") && has(fx.state(), "trophy"),
                        "a frame with an item and an armour stand in armour count: " + shown(fx.state()));
                // A painting is art too (it needs no item).
                empty.setItem(ItemStack.EMPTY, false);
                fx.place(at(4, 3), Blocks.STONE);
                Painting painting = fx.keep(new Painting(fx.level, helper.absolutePos(at(3, 3)), Direction.WEST,
                        fx.level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT)
                                .getHolderOrThrow(PaintingVariants.KEBAB)));
                fx.level.addFreshEntity(painting);
                fx.scan();
                helper.assertTrue(has(fx.state(), "art"), "a painting is art: " + shown(fx.state()));
                fx.close();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    // ---- Range ----------------------------------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_range")
    public static void onlyTheCylinderIsCounted(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        // Floating 8 above the furniture level, so that "8 below" is still inside the world (the test floor is near its bottom).
        BlockPos hearthRel = new BlockPos(2, 10, 2);
        Fixture fx = new Fixture(helper, null, hearthRel);
        try {
            fx.light(1);
            // Edge blocks: 24 across, 16 up and 8 down are in; one more in any direction is out.
            fx.place(hearthRel.offset(24, 0, 0), Blocks.OAK_PLANKS);
            fx.place(hearthRel.offset(25, 0, 0), Blocks.SPRUCE_PLANKS);
            fx.place(hearthRel.offset(0, 16, 0), Blocks.BIRCH_PLANKS);
            fx.place(hearthRel.offset(0, 17, 0), Blocks.JUNGLE_PLANKS);
            fx.place(hearthRel.offset(0, -8, 0), Blocks.ACACIA_PLANKS);
            fx.place(hearthRel.offset(0, -9, 0), Blocks.DARK_OAK_PLANKS);
            // The corner of the square is outside the circle: 18,18 is 25.5 away, 16,17 is 23.3.
            fx.place(hearthRel.offset(18, 0, 18), Blocks.MANGROVE_PLANKS);
            fx.scan();
            helper.assertTrue(fx.state().character() == 3,
                    "oak, birch and acacia are in the cylinder, nothing else is: " + shown(fx.state()));
            fx.place(hearthRel.offset(16, 0, 17), Blocks.CHERRY_PLANKS);
            fx.scan();
            helper.assertTrue(fx.state().character() == 4, "a block inside the circle's curve counts: " + shown(fx.state()));
        } finally {
            fx.close();
        }
        helper.succeed();
    }

    // ---- Character (abuse check 15) -------------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_character", timeoutTicks = 150)
    public static void aDirtHutStaysAtCeilingTwoAndRemovingBlocksDropsItAgain(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            HearthState state = fx.light(4).state();
            state.setHighestOffering(4);
            state.setCharge(HearthPool.HEARTY, 1500);
            // Two hundred blocks of filler (none of it falls): cobblestone, dirt, coarse dirt, grass, netherrack, cobbled deepslate.
            Block[] filler = {Blocks.COBBLESTONE, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.NETHERRACK,
                    Blocks.COBBLED_DEEPSLATE};
            for (int i = 0; i < 200; i++) {
                fx.place(new BlockPos(i % 20 - 8, 6 + i / 100, 8 + (i / 20) % 5), filler[i % filler.length]);
            }
            fx.scan();
            helper.assertTrue(state.character() == 0, "two hundred blocks of filler count 0: " + shown(state));
            helper.assertTrue(state.stage() == 4 && state.ceiling(Hearths.data()) == 2,
                    "a Hall in a dirt hut stays at ceiling II: " + state.ceiling(Hearths.data()));
            // Eight different building blocks lift it to III.
            Block[] building = {Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BRICKS, Blocks.STONE_BRICKS,
                    Blocks.GLASS, Blocks.OAK_LOG, Blocks.SMOOTH_STONE, Blocks.QUARTZ_BLOCK};
            List<BlockPos> good = new ArrayList<>();
            for (int i = 0; i < building.length; i++) {
                BlockPos rel = new BlockPos(i, 9, 14);
                fx.place(rel, building[i]);
                good.add(rel);
            }
            // The same plank twice is still one entry.
            fx.place(new BlockPos(0, 10, 14), Blocks.OAK_PLANKS);
            fx.scan();
            helper.assertTrue(state.character() == 8, "eight distinct ids: " + shown(state));
            helper.assertTrue(state.ceiling(Hearths.data()) == 3, "Character 8 allows III: " + state.ceiling(Hearths.data()));
            double charge = state.charge(HearthPool.HEARTY);
            // Take blocks away: the ceiling drops and the charge is held, never cut.
            fx.player.gameMode.destroyBlock(helper.absolutePos(good.get(1)));
            fx.player.gameMode.destroyBlock(helper.absolutePos(good.get(2)));
            fx.scan();
            helper.assertTrue(state.character() == 6, "two ids fewer: " + shown(state));
            helper.assertTrue(state.ceiling(Hearths.data()) == 2, "the ceiling drops again");
            helper.assertTrue(state.charge(HearthPool.HEARTY) == charge, "charge is held, not cut");
            helper.assertTrue(state.stage() == 4, "the build is untouched");
        } finally {
            fx.close();
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_footprint")
    public static void theHearthsOwnStructureCountsForNeitherComfortNorCharacter(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            // The campfire a player placed becomes the Hearth: its mark is stale, and its place is the structure's anyway.
            fx.place(HEARTH, Blocks.CAMPFIRE);
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            // The smoke column: the stage-I design keeps it clear above the fire (the roof slab, and the
            // template's own barrier ceiling on some loaders, are in the way).
            for (int dy = 1; dy <= 5; dy++) {
                helper.setBlock(HEARTH.offset(0, dy, 0), Blocks.AIR);
            }
            // The structure, as a player builds it: stage I of the default design (stone floor, four posts, four
            // lanterns), every block placed by the player, and a bell in the check box.
            for (HearthDesigns.Cell cell : HearthDesigns.shipped().lane(HearthDesignBuilds.LANE).stage(1).cells()) {
                if (cell.role() != '.' && cell.role() != 'F') {
                    fx.place(HEARTH.offset(cell.dx(), cell.dy(), cell.dz()), StageBlocks.defaultState(cell.role()));
                }
            }
            fx.support(HEARTH.offset(3, 0, 0));
            fx.place(HEARTH.offset(3, 0, 0), Blocks.BELL);
            helper.runAfterDelay(10, () -> {
                try {
                    fx.scan();
                    helper.assertTrue(fx.state().character() == 0 && fx.state().comfort() == 0,
                            "the Hearth's own structure counts for nothing: " + shown(fx.state()) + wrongCells(fx));
                    // The same blocks off the structure's positions are furniture and building blocks.
                    fx.place(HEARTH.offset(2, 0, 1), Blocks.OAK_STAIRS);
                    fx.place(HEARTH.offset(-2, 0, 1), Blocks.LANTERN);
                    fx.place(HEARTH.offset(4, 0, 0), Blocks.OAK_LOG);
                    fx.support(HEARTH.offset(5, 0, 0));
                    fx.place(HEARTH.offset(5, 0, 0), Blocks.BELL);
                    fx.scan();
                    // oak_stairs, lantern, oak_log, and the bell outside the box (the bell in the box is the Hearth's).
                    helper.assertTrue(fx.state().character() == 4, "furniture beside the fire counts: " + shown(fx.state()));
                    helper.assertTrue(has(fx.state(), "seat") && has(fx.state(), "light"),
                            "a chair and a lantern beside the fire are furniture: " + shown(fx.state()));
                } catch (RuntimeException | Error e) {
                    fx.close();
                    throw e;
                }
                fx.close();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    /** For a failure message: the cells of the stage-I design the probe does not read as built. */
    private static String wrongCells(Fixture fx) {
        StringBuilder out = new StringBuilder(" | built ");
        var probe = StageBlocks.probe(fx.level, fx.helper.absolutePos(HEARTH), Hearths.data());
        out.append(dev.amman.proficiency.hearth.stages.HearthStructure.check(probe).built()).append(", wrong:");
        for (HearthDesigns.Cell cell : HearthDesigns.shipped().lane(HearthDesignBuilds.LANE).stage(1).cells()) {
            if (cell.role() != 'F' && !probe.is(cell.role(), cell.dx(), cell.dy(), cell.dz())) {
                out.append(' ').append(cell.role()).append('@').append(cell.dx()).append(',').append(cell.dy()).append(',')
                        .append(cell.dz()).append('=').append(fx.level.getBlockState(
                                fx.helper.absolutePos(HEARTH.offset(cell.dx(), cell.dy(), cell.dz()))).getBlock());
            }
        }
        return out.toString();
    }

    // ---- The clock: the Hearth rescans itself ---------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_comfort_timer", timeoutTicks = 400)
    public static void theHearthRescansItselfAfterAPlacementAndABreak(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        // A one-tick cooldown: the Hearth's own second does the scanning here, no scanNow.
        Fixture fx = new Fixture(helper, "{\"scanCooldownSeconds\":0.05,\"rescanSeconds\":3600}", HEARTH);
        try {
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            helper.assertTrue(fx.state().lastScan() == HearthState.NEVER, "a fresh Hearth has not scanned");
            after(helper, fx, 45, () -> {
                helper.assertTrue(fx.state().lastScan() != HearthState.NEVER, "the first scan happens by itself");
                helper.assertTrue(fx.state().comfort() == 0, "an empty room: " + shown(fx.state()));
                fx.place(at(1, 1), Blocks.OAK_STAIRS);
                after(helper, fx, 45, () -> {
                    helper.assertTrue(has(fx.state(), "seat"), "a placement in range is picked up on the next second: " + shown(fx.state()));
                    fx.player.gameMode.destroyBlock(helper.absolutePos(at(1, 1)));
                    after(helper, fx, 45, () -> {
                        helper.assertFalse(has(fx.state(), "seat"), "so is a break: " + shown(fx.state()));
                        // A frame hung on a wall is picked up too; filled a moment later it counts without any event.
                        fx.place(at(4, 1), Blocks.STONE);
                        ItemFrame hung = fx.keep(frame(fx, at(3, 1), Direction.WEST, ItemStack.EMPTY));
                        after(helper, fx, 45, () -> {
                            helper.assertFalse(has(fx.state(), "art"), "an empty frame is no art yet: " + shown(fx.state()));
                            hung.setItem(new ItemStack(Items.COMPASS), false);
                            after(helper, fx, 45, () -> {
                                helper.assertTrue(has(fx.state(), "art"),
                                        "the Hearth looks again at an empty frame and sees it filled: " + shown(fx.state()));
                                fx.close();
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    @GameTest(template = T, batch = "hearth_comfort_explosion", timeoutTicks = 200)
    public static void anExplosionInRangeMakesTheHearthRescan(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, "{\"scanCooldownSeconds\":0.05,\"rescanSeconds\":3600}", HEARTH);
        try {
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            fx.place(at(1, 0), Blocks.WHITE_CARPET);
            after(helper, fx, 45, () -> {
                helper.assertTrue(has(fx.state(), "carpet"), "setup: the carpet counts: " + shown(fx.state()));
                BlockPos centre = helper.absolutePos(at(1, 0));
                fx.level.explode(null, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 1.0f,
                        Level.ExplosionInteraction.TNT);
                helper.assertTrue(fx.level.getBlockState(centre).isAir(), "setup: the blast took the carpet");
                after(helper, fx, 45, () -> {
                    helper.assertFalse(has(fx.state(), "carpet"), "an explosion in range is rescanned: " + shown(fx.state()));
                    helper.assertFalse(PlacedBlocks.rawComfortMarked(fx.level, centre), "and the mark of the blown-up carpet goes");
                    fx.close();
                    helper.succeed();
                });
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    @GameTest(template = T, batch = "hearth_comfort_leave", timeoutTicks = 300)
    public static void takingDownAFrameOrAStandMakesTheHearthRescan(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        // The 5-minute rescan is off: only the removal itself can make the Hearth look again.
        Fixture fx = new Fixture(helper, "{\"scanCooldownSeconds\":0.05,\"rescanSeconds\":3600}", HEARTH);
        try {
            fx.light(1);
            fx.roof(0, 0, 4, 4);
            fx.place(at(4, 1), Blocks.STONE);
            ItemFrame frame = fx.keep(frame(fx, at(3, 1), Direction.WEST, new ItemStack(Items.COMPASS)));
            ArmorStand stand = armourStand(fx, at(1, 3), true);
            after(helper, fx, 45, () -> {
                helper.assertTrue(has(fx.state(), "art") && has(fx.state(), "trophy"),
                        "setup: the frame and the stand count: " + shown(fx.state()));
                frame.discard();
                stand.kill();
                after(helper, fx, 45, () -> {
                    helper.assertFalse(has(fx.state(), "art"), "a frame taken down stops counting at once: " + shown(fx.state()));
                    helper.assertFalse(has(fx.state(), "trophy"), "so does a stand: " + shown(fx.state()));
                    fx.close();
                    helper.succeed();
                });
            });
        } catch (RuntimeException e) {
            fx.close();
            throw e;
        }
    }

    @GameTest(template = T, batch = "hearth_comfort_gone")
    public static void aHearthThatIsGoneForgetsItsFlagsAndPlacerNotes(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        try {
            fx.light(1);
            fx.place(at(1, 1), Blocks.OAK_STAIRS);
            String key = fx.hearth.key();
            ComfortRequests.Request request = ComfortRequests.get(key);
            helper.assertTrue(request != null && request.notes() >= 1 && ComfortRequests.dirty(key),
                    "setup: the placement left a flag and a note");
            helper.setBlock(HEARTH, Blocks.AIR);
            helper.assertTrue(ComfortRequests.get(key) == null, "the Hearth is gone: so is what the lane remembered of it");
        } finally {
            fx.close();
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_comfort_placers", timeoutTicks = 150)
    public static void aPlacerIsCreditedOnlyForABlockThatStillCounts(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture fx = new Fixture(helper, null, HEARTH);
        Map<String, UUID> placers = new HashMap<>();
        Consumer<HearthEvents.ComfortKindSeen> onKind = e -> placers.put(e.kind(), e.placer());
        HearthEvents.COMFORT_KIND_SEEN.add(onKind);
        Runnable done = () -> {
            HearthEvents.COMFORT_KIND_SEEN.remove(onKind);
            fx.close();
        };
        try {
            fx.light(1);
            // The roof covers the four western columns: the column at x = 4 is open sky.
            fx.roof(0, 0, 3, 4);
            ServerPlayer bob = MockPlayers.make(helper);
            bob.setGameMode(GameType.SURVIVAL);
            // Seat: the first placer breaks the chair herself; the next player's chair is the one that counts.
            fx.place(at(1, 1), Blocks.OAK_STAIRS);
            fx.player.gameMode.destroyBlock(helper.absolutePos(at(1, 1)));
            fx.place(bob, at(3, 1), Blocks.OAK_STAIRS);
            // Carpet: the first one is destroyed by something that is no player (no event), the second stands.
            fx.place(at(1, 3), Blocks.WHITE_CARPET);
            helper.setBlock(at(1, 3), Blocks.AIR);
            fx.place(bob, at(3, 3), Blocks.WHITE_CARPET);
            // Music: the first jukebox stands out under the sky and counts for nothing; the second one is indoors.
            fx.place(at(4, 2), Blocks.JUKEBOX);
            fx.place(bob, at(1, 2), Blocks.JUKEBOX);
            after(helper, fx, 10, () -> {
                fx.scan();
                helper.assertTrue(has(fx.state(), "seat") && has(fx.state(), "carpet") && has(fx.state(), "music"),
                        "setup: the three kinds count: " + shown(fx.state()));
                UUID bobId = bob.getUUID();
                for (String kind : List.of("seat", "carpet", "music")) {
                    helper.assertTrue(bobId.equals(placers.get(kind)),
                            kind + " is credited to the player whose block counts, not to the one whose block is gone or outdoors: "
                                    + placers.get(kind) + " (bob " + bobId + ", alice " + fx.player.getUUID() + ")");
                }
                done.run();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            done.run();
            throw e;
        }
    }
}
