package dev.amman.proficiency.hearth.sieges;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code proficiency:siege_trophy}: a wall-mounted trophy for a defended siege, its look by the
 * Hearth's ceiling (docs/HEARTH-SPEC.md, "The siege itself"). Blockstate (the art contract):
 * {@code tier} 3..6 and horizontal {@code facing}, the way the trophy faces, away from its wall.
 * It counts for the {@code trophy} Comfort kind through the comfort tags.
 *
 * <p>Like a wall banner it only hangs on the side of a sturdy block (the clicked face first, then
 * the nearest wall the player looks toward) and drops when that wall goes. The tier comes from the
 * item's {@code block_state} component ({@link SiegeTrophyItem}); a placement without one is III.
 */
public class SiegeTrophyBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SiegeTrophyBlock> CODEC = simpleCodec(SiegeTrophyBlock::new);

    public static final IntegerProperty TIER = IntegerProperty.create("tier", 3, 6);

    private static final VoxelShape NORTH = Block.box(2.0, 2.0, 14.0, 14.0, 14.0, 16.0);
    private static final VoxelShape SOUTH = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 2.0);
    private static final VoxelShape WEST = Block.box(14.0, 2.0, 2.0, 16.0, 14.0, 14.0);
    private static final VoxelShape EAST = Block.box(0.0, 2.0, 2.0, 2.0, 14.0, 14.0);

    public SiegeTrophyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIER, 3));
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f)
                .sound(SoundType.WOOD)
                .noOcclusion();
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }

    /** The clicked wall first, then the nearest wall the player looks toward; null when there is no wall. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction look : context.getNearestLookingDirections()) {
            if (look.getAxis().isHorizontal()) {
                BlockState placed = state.setValue(FACING, look.getOpposite());
                if (placed.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return placed;
                }
            }
        }
        return null;
    }

    /** It needs the sturdy face of the block behind it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Pick-block gives the trophy with this tier. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return SiegeTrophyItem.of(state.getValue(TIER), 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }
}
