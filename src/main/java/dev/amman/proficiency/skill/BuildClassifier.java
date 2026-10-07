package dev.amman.proficiency.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** Structural or decorative. Shared so the client's ability key agrees with the server's XP. */
public final class BuildClassifier {

    private static final Set<String> FURNITURE_NAMESPACES = Set.of(
            "handcrafted", "another_furniture", "supplementaries", "chipped",
            "adorabuild_structures", "amendments", "immersivelanterns", "hearths",
            "aurelj_paintings", "fastpaintings");

    private static final net.minecraft.resources.ResourceLocation GLASS_TAG_ID =
            net.minecraft.resources.ResourceLocation.tryParse("c:glass_blocks");

    private BuildClassifier() {
    }

    /** Modded glass: the loaders' shared glass-block tag. */
    private static final TagKey<Block> GLASS_BLOCKS = TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK, GLASS_TAG_ID);

    /**
     * Which skill a placed block pays. Level-free on purpose: the client's ability key calls this
     * with a default state and no world, so the shape test uses the static collision shape.
     * Order: planting (Farming); the decorative list; decorative shapes and glass (Decorating);
     * stairs, slabs and walls (Masonry here, and half of the XP goes to Decorating, see
     * {@link #isSplit}); any other full cube (Masonry); any other non-full block (Decorating).
     * Machines (Engineering) are checked by the placement handler before this.
     */
    public static Skill skillFor(BlockState state) {
        if (isPlanting(state)) {
            return Skill.FARMING;
        }
        if (isDecorative(state) || isDecorativeShape(state)) {
            return Skill.DECORATING;
        }
        if (isSplit(state)) {
            return Skill.MASONRY;
        }
        return isFullCube(state) ? Skill.MASONRY : Skill.DECORATING;
    }

    /**
     * Stairs, slabs and walls: half Masonry, half Decorating. Only Masonry rolls procs and
     * refunds and pays a first-time bonus for them, so one block can never refund twice.
     */
    public static boolean isSplit(BlockState state) {
        if (isPlanting(state) || isDecorative(state) || isDecorativeShape(state)) {
            return false;
        }
        return state.getBlock() instanceof StairBlock
                || state.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof WallBlock
                || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS);
    }

    /** Fences, gates, panes and bars, doors, trapdoors, buttons, plates, lanterns, chains, ladders, glass. */
    public static boolean isDecorativeShape(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock || block instanceof FenceGateBlock
                || block instanceof IronBarsBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock || block instanceof LanternBlock
                || block instanceof ChainBlock || block instanceof LadderBlock
                || block instanceof TransparentBlock || block instanceof StainedGlassBlock
                || block instanceof TintedGlassBlock
                || state.is(GLASS_BLOCKS)
                || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES);
    }

    /** A full cube by its static collision shape (no world needed, so the client can ask too). */
    public static boolean isFullCube(BlockState state) {
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A crop, stem, sapling or bush put in the ground from seeds: that is farming, not building.
     * Flowers stay decorative, so they are excluded even if a pack tags one as a crop.
     */
    public static boolean isPlanting(BlockState state) {
        if (state.is(BlockTags.FLOWERS)) {
            return false;
        }
        return state.getBlock() instanceof CropBlock
                || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof NetherWartBlock
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.getBlock() instanceof CocoaBlock
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS);
    }

    public static boolean isDecorative(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null && FURNITURE_NAMESPACES.contains(id.getNamespace())) {
            return true;
        }
        return state.is(BlockTags.CANDLES)
                || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.BANNERS)
                || state.is(BlockTags.FLOWER_POTS)
                || state.is(BlockTags.SIGNS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CAMPFIRES);
    }
}
