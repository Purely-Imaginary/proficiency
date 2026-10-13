package dev.amman.proficiency.hearth;

import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * "Is this home?" in one place (docs/HEARTH-SPEC.md, "Range"). The range is a cylinder around the
 * campfire, from the server config ({@code hearth.range} radius, {@code hearth.rangeBelow},
 * {@code hearth.rangeAbove}); one meaning for gathering, boons, Comfort, the streak and sieges.
 *
 * <ul>
 * <li>{@link #homeOf}: the Hearth a player is in range of, from the flag the Hearth's tick writes
 * ({@link PlayerHearth#seenBy}); null within 2 s of leaving, a teleport or the Hearth going away.</li>
 * <li>{@link #isHome}: that, and the Hearth is not smouldering: what boons and Homebuilder mean.</li>
 * <li>{@link #hearthAt}: the Hearth whose range holds a block, from the per-dimension index (works
 * with nobody there: a furnace tick asks this).</li>
 * <li>{@link #inRange}: the plain cylinder test (positions only; the dimension is the caller's).</li>
 * </ul>
 */
public final class HearthRange {

    private HearthRange() {
    }

    private static volatile HearthGeometry.Range cached;

    /**
     * The configured cylinder. A field read: the server refreshes it from the config once a second
     * ({@link #refreshRange}), so asking it on a hot path costs nothing.
     */
    public static HearthGeometry.Range range() {
        HearthGeometry.Range range = cached;
        return range != null ? range : refreshRange();
    }

    /** Re-reads the three config values; a new cylinder only when one of them changed. */
    public static HearthGeometry.Range refreshRange() {
        double radius = ProficiencyConfig.hearthRange();
        int below = ProficiencyConfig.hearthRangeBelow();
        int above = ProficiencyConfig.hearthRangeAbove();
        HearthGeometry.Range range = cached;
        if (range == null || range.radius() != radius || range.below() != below || range.above() != above) {
            range = new HearthGeometry.Range(radius, below, above);
            cached = range;
        }
        return range;
    }

    /**
     * Whether a block is inside the cylinder of the Hearth at {@code hearthPos}. Positions carry no
     * dimension: that both are in the Hearth's level is the caller's part ({@link #homeOf} and
     * {@link #hearthAt} already answer per level).
     */
    public static boolean inHouse(BlockPos hearthPos, BlockPos pos) {
        return range().contains(pos.getX() - hearthPos.getX(), pos.getY() - hearthPos.getY(),
                pos.getZ() - hearthPos.getZ());
    }

    /**
     * Whether a block is in the Hearth's territory: its own Reach radius (docs/HEARTH-REACH-SPEC.md),
     * the configured heights. The house (Comfort scan, siege ring, bell) uses {@link #inHouse}.
     */
    public static boolean inRange(ServerLevel level, BlockPos hearthPos, BlockPos pos) {
        return cylinder(level, hearthPos).contains(pos.getX() - hearthPos.getX(), pos.getY() - hearthPos.getY(),
                pos.getZ() - hearthPos.getZ());
    }

    /** {@link #inRange(ServerLevel, BlockPos, BlockPos)} for a point (an entity's position). */
    public static boolean inRange(ServerLevel level, BlockPos hearthPos, Vec3 at) {
        return cylinder(level, hearthPos).contains(at.x - (hearthPos.getX() + 0.5), at.y - hearthPos.getY(),
                at.z - (hearthPos.getZ() + 0.5));
    }

    /** This Hearth's territory radius: its Reach, or the base range. */
    public static double radius(ServerLevel level, BlockPos hearthPos) {
        return HearthIndexData.get(level).radius(hearthPos);
    }

    /** The territory cylinder of one Hearth. */
    public static HearthGeometry.Range cylinder(ServerLevel level, BlockPos hearthPos) {
        HearthGeometry.Range base = range();
        double radius = radius(level, hearthPos);
        return radius == base.radius() ? base : new HearthGeometry.Range(radius, base.below(), base.above());
    }

    /** Whether a point (an entity's position) is inside the house cylinder (the base range) of the Hearth at {@code hearthPos}; same dimension, as above. */
    public static boolean inHouse(BlockPos hearthPos, Vec3 at) {
        return range().contains(at.x - (hearthPos.getX() + 0.5), at.y - hearthPos.getY(),
                at.z - (hearthPos.getZ() + 0.5));
    }

    /** The Hearth whose range holds {@code pos} in this level, from the index; null when none. */
    @Nullable
    public static BlockPos hearthAt(ServerLevel level, BlockPos pos) {
        return HearthIndexData.get(level).hearthAt(pos);
    }

    /**
     * As {@link #hearthAt(ServerLevel, BlockPos)}, packed ({@code BlockPos.asLong}), or
     * {@link HearthIndexStore#NONE}: allocates nothing, for per-tick callers (a furnace). The entry
     * may name a Hearth whose chunk is unloaded; {@link #entity} tells.
     */
    public static long hearthAtPacked(ServerLevel level, int x, int y, int z) {
        return HearthIndexData.get(level).hearthAt(x, y, z);
    }

    /**
     * The Hearth whose house (base radius, Reach ignored) holds {@code pos}, or null: for what is
     * part of the building (Comfort, the bell, lecterns), docs/HEARTH-REACH-SPEC.md.
     */
    @Nullable
    public static BlockPos houseAt(ServerLevel level, BlockPos pos) {
        long found = HearthIndexData.get(level).houseAt(pos.getX(), pos.getY(), pos.getZ());
        return found == HearthIndexStore.NONE ? null : BlockPos.of(found);
    }

    /** The loaded block entity of {@link #houseAt}, or null. */
    @Nullable
    public static HearthBlockEntity houseEntityAt(ServerLevel level, BlockPos pos) {
        BlockPos hearth = houseAt(level, pos);
        return hearth == null ? null : entity(level, hearth);
    }

    /**
     * Whether {@code player} is home at the Hearth at {@code hearthPos}: inside its territory and that
     * Hearth is the nearest one whose territory holds them (grown ranges may overlap).
     */
    public static boolean homeIs(ServerLevel level, BlockPos hearthPos, Vec3 at) {
        if (!inRange(level, hearthPos, at)) {
            return false;
        }
        long nearest = HearthIndexData.get(level).hearthAt((int) Math.floor(at.x), (int) Math.floor(at.y),
                (int) Math.floor(at.z));
        return nearest == HearthIndexStore.NONE || nearest == hearthPos.asLong();
    }

    /** The Hearth block entity whose range holds {@code pos}, when its chunk is loaded; else null. */
    @Nullable
    public static HearthBlockEntity hearthEntityAt(ServerLevel level, BlockPos pos) {
        BlockPos hearth = hearthAt(level, pos);
        return hearth == null ? null : entity(level, hearth);
    }

    /**
     * The Hearth block entity at exactly {@code pos} if it is loaded, else null. An indexed position
     * whose loaded chunk holds no Hearth there is a ghost (the block vanished without the block
     * hearing it): it is forgotten on the spot.
     */
    @Nullable
    public static HearthBlockEntity entity(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof HearthBlockEntity hearth) {
            return hearth;
        }
        if (HearthIndexData.get(level).contains(pos) && !level.getBlockState(pos).is(HearthBlocks.HEARTH.get())) {
            HearthIndexData.forget(level, pos);
        }
        return null;
    }

    /** The Hearth this player is in range of (any state, smouldering included), or null. */
    @Nullable
    public static BlockPos homeOf(ServerPlayer player) {
        PlayerHearth data = HearthAttachments.of(player);
        String dimension = player.level().dimension().location().toString();
        if (!data.inRange(dimension, player.level().getGameTime())) {
            return null;
        }
        return BlockPos.of(data.inRangePos());
    }

    /** The loaded block entity of {@link #homeOf}, or null. */
    @Nullable
    public static HearthBlockEntity homeEntity(ServerPlayer player) {
        BlockPos home = homeOf(player);
        return home == null ? null : entity(player.serverLevel(), home);
    }

    /** At home: in range of a lit Hearth that is not smouldering. */
    public static boolean isHome(ServerPlayer player) {
        HearthBlockEntity home = homeEntity(player);
        return home != null && !home.state().smouldering();
    }
}
