package dev.amman.proficiency.hearth;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code proficiency:hearth}: the lit campfire at the heart of a base. Made in the world by the
 * Stages lane (a campfire on a platform, lit with an offering), never crafted, so it has no item.
 *
 * <p>Blockstate (the art contract, docs/HEARTH-CONTRACT.md): {@code stage} 1..6, the ceiling the
 * build has reached, and {@code smouldering}, a doused Hearth; since the integration task also
 * {@code glow} (the highest effective tier of any pool: the light, {@link HearthRules#chargeLight}) and
 * {@code feed} (the pool fed most this hour: the colour of the fire's motes), which the model ignores. The block keeps the per-dimension index ({@link HearthIndexData})
 * and the world data's siege mirror current when it appears and goes; everything else it does belongs to the lanes, through
 * {@link HearthHooks}. The Stages lane's parts (docs/HEARTH-CONTRACT.md): breaking takes a 3 s
 * sneak-hold ({@code stages.HearthBreaking}), it drops a plain campfire (its loot table), explosions
 * do not take it, and a click the server answers (an offering, an empty hand) predicts nothing on
 * the client.
 */
public class HearthBlock extends BaseEntityBlock {

    public static final MapCodec<HearthBlock> CODEC = simpleCodec(HearthBlock::new);

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 1, 6);
    public static final BooleanProperty SMOULDERING = BooleanProperty.create("smouldering");
    /**
     * The highest effective tier of any pool, 0..6 (integration, 2026-10-09): the light follows it
     * ({@link HearthRules#chargeLight}, "glows with charge"). Kept in step by {@link HearthLook} once a
     * second. Not in the blockstate file's variant keys, so the model does not change with it.
     */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 6);
    /**
     * The pool fed most in the rolling hour, as its wire index + 1, 0 for none ({@link HearthRules#feedingMost}):
     * the client tints the fire's motes in that pool's colour. Kept in step by {@link HearthLook}.
     */
    public static final IntegerProperty FEED = IntegerProperty.create("feed", 0, HearthPool.COUNT);

    /** A campfire's height: the fire pit, not a full block. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);

    public HearthBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 1).setValue(SMOULDERING, false)
                .setValue(GLOW, 0).setValue(FEED, 0));
    }

    /**
     * Campfire-like: wood sounds, lit by its stage. The break speed is the sneak-hold
     * ({@link #getDestroyProgress}); no explosion takes it (losing a home's charge to a stray creeper
     * would be the accidental break the hold exists to prevent).
     */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PODZOL)
                .strength(2.0f)
                .explosionResistance(1200.0f)
                .sound(SoundType.WOOD)
                .lightLevel(state -> HearthRules.chargeLight(state.getValue(GLOW), state.getValue(SMOULDERING)))
                .noOcclusion();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, SMOULDERING, GLOW, FEED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * The 3 s sneak-hold: no progress without sneaking, whatever the tool. The server counts the
     * sneaked ticks itself, so a client that sneaks only at the end does not finish it.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return dev.amman.proficiency.hearth.stages.HearthBreaking.destroyProgress(player, pos);
    }

    /** The start of a break: the server starts counting the hold; a swing without sneaking gets the hint. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer server) {
            dev.amman.proficiency.hearth.stages.HearthBreaking.onAttack(server, pos);
        }
    }

    /**
     * The server answers offerings and the panel through {@code HearthHooks.BLOCK_USE} before this
     * runs; on the client a click it will answer is swallowed here, so the client predicts no block
     * placed against the Hearth and no fire beside it. Vanilla calls this with the empty stack too, so
     * an empty main hand (the panel) is swallowed here as well.
     *
     * <p>No {@code useWithoutItem}: vanilla's PASS. Vanilla calls it after
     * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} for any main-hand click (MultiPlayerGameMode.performUseItemOn),
     * so a SUCCESS there swallowed every click with an item the server does not answer: bread was not
     * eaten, a bow not drawn, an off-hand torch not placed (review of the Forge foundation, 2026-10-09).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide && hand == InteractionHand.MAIN_HAND
                && dev.amman.proficiency.hearth.stages.HearthOfferings.answers(stack)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** It has no item of its own: pick-block gives the campfire it was made from. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(Items.CAMPFIRE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HearthBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        // The client ticker is the client lane's particles by stage (hearth.client.HearthBlockFx).
        return level.isClientSide
                ? createTickerHelper(type, HearthBlocks.HEARTH_ENTITY.get(),
                        dev.amman.proficiency.hearth.client.HearthBlockFx::clientTick)
                : createTickerHelper(type, HearthBlocks.HEARTH_ENTITY.get(), HearthBlockEntity::serverTick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            HearthIndexData.get(server).add(pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            HearthIndexData.forget(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
