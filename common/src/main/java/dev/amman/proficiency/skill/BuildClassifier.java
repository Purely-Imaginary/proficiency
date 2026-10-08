package dev.amman.proficiency.skill;

import dev.amman.proficiency.xp.XpDomain;
import dev.amman.proficiency.xp.XpMatch;
import dev.amman.proficiency.xp.XpSources;
import dev.amman.proficiency.xp.XpSubjects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Structural or decorative. Shared so the client's ability key agrees with the server's XP.
 *
 * <p>The classification is data now: the "place" rules of xp_sources decide the skill, whether a
 * block is planting, decorative or split between two skills, and what it pays. What stays here is
 * what only code can answer (shape classes, the collision shape), exposed to the rules as the
 * {@code proficiency:builtin/*} tags in {@code XpBuiltin}.
 */
public final class BuildClassifier {

    private BuildClassifier() {
    }

    /** The winning "place" rule, machines included: what the server pays for this placement. */
    public static XpMatch placement(BlockState state) {
        return XpSources.table().match(XpDomain.PLACE, XpSubjects.block(state));
    }

    /** As {@link #placement} but without the machine rules, which the client's ability key never knew. */
    private static XpMatch buildMatch(BlockState state) {
        return XpSources.table().matchWithout(XpDomain.PLACE, XpSubjects.block(state), "machine");
    }

    /**
     * Which skill a placed block pays. Level-free on purpose: the client's ability key calls this
     * with a default state and no world, so the shape test uses the static collision shape.
     * With the shipped rules the order is: planting (Farming); the decorative list; decorative
     * shapes and glass (Decorating); stairs, slabs and walls (Masonry here, and half of the XP goes
     * to Decorating, see {@link #isSplit}); any other full cube (Masonry); any other block
     * (Decorating). Machines (Engineering) are handled by the placement handler before this.
     */
    public static Skill skillFor(BlockState state) {
        XpMatch match = buildMatch(state);
        return match == null || match.paysNothing() ? Skill.DECORATING : match.rule().skill;
    }

    /**
     * Stairs, slabs and walls: half Masonry, half Decorating. Only Masonry rolls procs and
     * refunds and pays a first-time bonus for them, so one block can never refund twice.
     */
    public static boolean isSplit(BlockState state) {
        XpMatch match = buildMatch(state);
        return match != null && !match.paysNothing() && match.rule().also != null;
    }

    /**
     * A crop, stem, sapling or bush put in the ground from seeds: that is farming, not building.
     * Flowers stay decorative, so the shipped rules exclude them even if a pack tags one as a crop.
     */
    public static boolean isPlanting(BlockState state) {
        XpMatch match = buildMatch(state);
        return match != null && match.has("planting");
    }

    /** Furniture mods and the decor tags: the blocks a cozy room counts. */
    public static boolean isDecorative(BlockState state) {
        XpMatch match = buildMatch(state);
        return match != null && match.has("decor");
    }

    /** Fences, gates, panes and bars, doors, trapdoors, buttons, plates, lanterns, chains, ladders, glass classes. */
    public static boolean isDecorativeClass(Block block) {
        return block instanceof FenceBlock || block instanceof FenceGateBlock
                || block instanceof IronBarsBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock || block instanceof LanternBlock
                || block instanceof ChainBlock || block instanceof LadderBlock
                || block instanceof TransparentBlock || block instanceof StainedGlassBlock
                || block instanceof TintedGlassBlock;
    }

    /** A full cube by its static collision shape (no world needed, so the client can ask too). */
    public static boolean isFullCube(BlockState state) {
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
