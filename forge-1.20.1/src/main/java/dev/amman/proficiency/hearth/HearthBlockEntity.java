package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Hearth's block entity on Forge 1.20.1 (common's {@code HearthBlockEntity}): one
 * {@link HearthState} (charge per pool, stage, highest offering, last visit, ledger, holds, Comfort
 * and Character cache, siege fields, Feast and bell), saved with {@link HearthState#CODEC} under
 * {@code "hearth"}, plus a free tag per lane under {@code "lanes"} ({@link #laneData}). The NBT is the
 * same shape as on 1.21.1.
 *
 * <p>Ticks once a second on the server (never per tick of work): it makes sure the per-dimension
 * index lists it, hands over to the lanes through {@link HearthHooks#HEARTH_TICK}, then copies the
 * state's siege due time into the world data's mirror ({@link HearthWorldData#siegeDue()}). Call
 * {@link #setChanged()} after changing the state so the chunk saves it.
 *
 * <p>1.20.1: {@code saveAdditional(tag)} and {@code load(tag)} take no registry lookup.
 */
public class HearthBlockEntity extends BlockEntity {

    private static final String STATE_KEY = "hearth";
    private static final String LANES_KEY = "lanes";

    private HearthState state = new HearthState();
    private CompoundTag lanes = new CompoundTag();
    private int ticks;
    @Nullable
    private String key;

    public HearthBlockEntity(BlockPos pos, BlockState blockState) {
        super(HearthBlocks.HEARTH_ENTITY.get(), pos, blockState);
    }

    public HearthState state() {
        return state;
    }

    /** Replaces the whole state (a test, or a lane rebuilding it); marks the block entity changed. */
    public void setState(HearthState next) {
        state = next == null ? new HearthState() : next;
        setChanged();
    }

    /**
     * The free tag of one lane ({@code "sieges"}, {@code "comfort"}, ...), created on first use and
     * saved with the block entity. Call {@link #setChanged()} after writing to it.
     */
    public CompoundTag laneData(String lane) {
        if (!lanes.contains(lane, Tag.TAG_COMPOUND)) {
            lanes.put(lane, new CompoundTag());
        }
        return lanes.getCompound(lane);
    }

    /** The lane's tag if it already exists, else null: reads without creating (or saving) an empty tag. */
    public CompoundTag peekLane(String lane) {
        return lanes.contains(lane, Tag.TAG_COMPOUND) ? lanes.getCompound(lane) : null;
    }

    /** The position as core packs it ({@link HearthPos}): the same long as {@code getBlockPos().asLong()}. */
    public long packedPos() {
        return worldPosition.asLong();
    }

    /** "dimension|x,y,z": the key world data and ledgers use for this Hearth. */
    public String key() {
        Level level = getLevel();
        if (level == null) {
            return key(null, worldPosition);
        }
        if (key == null) {
            key = key(level, worldPosition);
        }
        return key;
    }

    /** "dimension|x,y,z" for any Hearth position, loaded or not (what {@link #key()} returns for it). */
    public static String key(@Nullable Level level, BlockPos pos) {
        String dimension = level == null ? "" : level.dimension().location().toString();
        return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, HearthBlockEntity hearth) {
        if (++hearth.ticks < 20) {
            return;
        }
        hearth.ticks = 0;
        if (level instanceof ServerLevel server) {
            // Self-healing: a Hearth that came from a structure, an old save or /setblock is indexed too.
            HearthIndexData.get(server).add(pos);
            HearthHooks.onHearthSecond(server, pos, hearth);
            // The world data's copy of the siege due time follows the state, the one source of truth
            // (after the lanes' tick, so a change they made this second is copied at once).
            HearthWorldData.get(server.getServer()).mirrorSiegeDue(hearth.key(), hearth.state.siegeDue());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        HearthState.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .resultOrPartial(error -> Proficiency.LOG.error("Hearth at {}: cannot save: {}", worldPosition, error))
                .ifPresent(saved -> tag.put(STATE_KEY, saved));
        tag.put(LANES_KEY, lanes.copy());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Tag saved = tag.get(STATE_KEY);
        state = saved == null ? new HearthState()
                : HearthState.CODEC.parse(NbtOps.INSTANCE, saved)
                        .resultOrPartial(error -> Proficiency.LOG.error("Hearth at {}: cannot load: {}", worldPosition, error))
                        .orElseGet(HearthState::new);
        lanes = tag.getCompound(LANES_KEY).copy();
    }
}
