package dev.amman.proficiency.hearth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * {@code proficiency:hearth} on Forge 1.20.1: the lit campfire at the heart of a base, made in the
 * world by the Stages lane, never crafted, so it has no item. The port of common's {@code HearthBlock}.
 *
 * <p>Blockstate (the art contract, docs/HEARTH-CONTRACT.md): {@code stage} 1..6, the ceiling the build
 * has reached, and {@code smouldering}, a doused Hearth; since 1.21.1's integration task also
 * {@code glow} (the highest effective tier of any pool: the light, {@link HearthRules#chargeLight}) and
 * {@code feed} (the pool fed most this hour: the colour of the fire's motes), which the model ignores,
 * both kept by {@link HearthLook}. The block keeps the per-dimension index ({@link HearthIndexData}) and
 * the world data's siege mirror current when it appears and goes; everything else it does belongs to
 * the lanes, through {@link HearthHooks}. The Stages lane's parts call into its classes: the 3 s
 * sneak-hold break ({@code stages.HearthBreaking}), it drops a plain campfire (its loot table), and a
 * click the server answers (an offering, an empty hand) predicts nothing on the client.
 *
 * <p>Nothing but the sneak-hold takes it: explosions meet a resistance of 1200, and the
 * {@code minecraft:wither_immune} and {@code dragon_immune} block tags list it, so neither the
 * Wither's charge and blue skulls nor the dragon's flight break it.
 *
 * <p>1.20.1 differences: one {@link #use} instead of {@code useItemOn}/{@code useWithoutItem}, no
 * block codec, and the public overrides of the old API.
 */
public class HearthBlock extends BaseEntityBlock {

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 1, 6);
    public static final BooleanProperty SMOULDERING = BooleanProperty.create("smouldering");
    /**
     * The highest effective tier of any pool, 0..6: the light follows it ({@link HearthRules#chargeLight},
     * "glows with charge"). Kept in step by {@link HearthLook} once a second. Not in the blockstate
     * file's variant keys, so the model does not change with it.
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
     * Campfire-like: wood sounds, lit by its charge. The break speed is the sneak-hold
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, SMOULDERING, GLOW, FEED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * The 3 s sneak-hold: no progress without sneaking, whatever the tool. The server counts the
     * sneaked ticks itself, so a client that sneaks only at the end does not finish it.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return dev.amman.proficiency.hearth.stages.HearthBreaking.destroyProgress(player, pos);
    }

    /** The start of a break: the server starts counting the hold; a swing without sneaking gets the hint. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer server) {
            dev.amman.proficiency.hearth.stages.HearthBreaking.onAttack(server, pos);
        }
    }

    /**
     * The server answers offerings and the panel through {@code HearthHooks.BLOCK_USE} (Forge's
     * {@code RightClickBlock} at LOWEST) before this runs, and passes here. On the client a click the
     * server will answer ({@link #predicts}) is swallowed, so the client predicts no block placed
     * against the Hearth and no fire beside it. Every other click passes: vanilla goes on to the held
     * item (bread is eaten, a bow drawn, a block placed against the Hearth) and then to the off hand.
     */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide && predicts(hand, player.getItemInHand(hand))) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Whether the client swallows a click with {@code held} in {@code hand}: a main-hand click the server
     * answers ({@code stages.HearthOfferings.answers}: an empty hand, flint and steel, an offering). The
     * same rule as 1.21.1's {@code useItemOn}.
     */
    public static boolean predicts(InteractionHand hand, ItemStack held) {
        return hand == InteractionHand.MAIN_HAND && dev.amman.proficiency.hearth.stages.HearthOfferings.answers(held);
    }

    /** It has no item of its own: pick-block gives the campfire it was made from. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
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
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            HearthIndexData.get(server).add(pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            HearthIndexData.forget(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
