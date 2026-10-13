package dev.amman.proficiency.hearth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import dev.amman.proficiency.hearth.stages.StageGhost;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hearth panel's "Show build guide" (docs/HEARTH-DESIGNS-SPEC.md): translucent ghosts of the chosen
 * lane's next stage, drawn in the world around one Hearth. A cell that does not hold its role shows a ghost
 * of the role's default block; a cell that must be air and is blocked is outlined in red; cells that match
 * show nothing, checked again each frame so a block just placed loses its ghost at once (a ghost drawn over
 * the real block z-fights and flickers). The alpha is baked into the vertices (a shader colour is dropped by
 * shader packs), and each ghost is drawn a hair inside its cell so no face shares a plane with a real block. A plain campfire's panel
 * (stage 0) turns it on too, with stage I's ghosts; lighting the campfire keeps it on. The world is read on the client (block tags are synced), the cell list is rebuilt at most
 * every {@value #REBUILD_TICKS} ticks and a frame only walks that list, so it costs a few hundred small
 * model draws at most. Off by itself when the Hearth is gone, in another dimension or out of range.
 *
 * <p>With Patchouli installed the panel's guide is Patchouli's own visualizer instead ({@link HearthVisualize}),
 * anchored on this Hearth: the same ghosts, progress bar and "Complete!" as a Patchouli book's Visualize. This
 * class then only keeps the on/off state and the lane, and Patchouli draws and switches itself off.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class HearthGhost {

    static final int REBUILD_TICKS = 10;
    private static final float ALPHA = 0.22f;
    /** Ghost size within its cell: a hair under 1 so a ghost face never lies on a real block's face. */
    private static final float INSET_SCALE = 0.96f;
    private static final float[] FULL_BRIGHT = {1.0f, 1.0f, 1.0f, 1.0f};
    private static final net.minecraft.core.Direction[] FACES_AND_NULL = {net.minecraft.core.Direction.DOWN,
            net.minecraft.core.Direction.UP, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
            net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST, null};
    private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();
    /** Past this many blocks from the camera nothing is drawn. */
    private static final double DRAW_RANGE = 96;

    /** One wrong cell, resolved for drawing: where, what, how lit. */
    private record Ghost(BlockPos pos, BlockState state, BakedModel model, boolean outline, int light, int fluid,
                         StageGhost.Cell cell) {
    }

    /** The tints of a missing water source (blue) and lava source (orange), ARGB. */
    private static final int WATER_TINT = 0x553F76E4;
    private static final int LAVA_TINT = 0x88FF8C1A;
    /** A fluid source's surface stands at 14/16 of its block. */
    private static final double FLUID_TOP = 14.0 / 16.0;

    private static boolean on;
    /** The guide is shown through Patchouli ({@link HearthVisualize}), not drawn here. */
    private static boolean patchouli;
    private static long pos;
    private static String dimension = "";
    private static int lane;
    private static boolean dirty;
    private static int ticks;
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    /** Per lane and role letter: the lane's block, resolved once (the models are looked up at each rebuild, so a reload shows). */
    private static final java.util.Map<String, BlockState[]> ROLE_STATE = new java.util.HashMap<>();

    private HearthGhost() {
    }

    // ---- State --------------------------------------------------------------------------------------

    /** Whether the guide is on for the Hearth at {@code at}. */
    public static boolean showing(long at) {
        if (on && patchouli && !HearthVisualize.showingAny()) {
            // Patchouli switched it off itself: the structure was completed, or another one replaced it.
            on = false;
        }
        return on && pos == at;
    }

    /** The chosen lane, an index into the shipped designs. */
    public static int lane() {
        return lane;
    }

    /** Turns the guide on for a Hearth (the lane the build is closest to) or off. Returns the new state. */
    public static boolean toggle(HearthStatePayload state) {
        if (showing(state.pos())) {
            off();
            return false;
        }
        on = true;
        pos = state.pos();
        dimension = state.dimension() == null ? "" : state.dimension();
        lane = closestLane(state);
        dirty = true;
        ticks = 0;
        patchouli = HearthVisualize.available();
        if (patchouli) {
            showPatchouli(state.stage());
        }
        return on;
    }

    /** Patchouli shows the lane's next stage (stage VI once sealed) anchored on this Hearth. */
    private static void showPatchouli(int stage) {
        int next = Math.max(1, Math.min(HearthDesigns.STAGES, stage + 1));
        on = HearthVisualize.showAt(lane, next, BlockPos.of(pos));
    }

    /** The next lane (wraps), for the panel's button. */
    public static void cycleLane() {
        int count = HearthDesigns.shipped().lanes().size();
        lane = (lane + 1) % count;
        dirty = true;
        if (on && patchouli) {
            HearthStatePayload state = HearthView.state();
            ClientLevel level = Minecraft.getInstance().level;
            BlockPos at = BlockPos.of(pos);
            showPatchouli(state != null && state.pos() == pos ? state.stage()
                    : level != null && level.isLoaded(at) ? stageOf(level, at) : 0);
        }
    }

    /** Sets the lane without the toggle (the layout demo's way in). */
    static void setLane(int next) {
        lane = Math.floorMod(next, HearthDesigns.shipped().lanes().size());
        dirty = true;
    }

    public static void off() {
        if (on && patchouli && HearthVisualize.showingAny()) {
            HearthVisualize.clear();
        }
        on = false;
        GHOSTS.clear();
    }

    /**
     * The lane whose next stage (or stage VI, once sealed) has the fewest wrong cells around this Hearth, read
     * from the client's world; the first lane (Longhouse) when the world is not there.
     */
    public static int closestLane(@Nullable HearthStatePayload state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (state == null || level == null) {
            return 0;
        }
        BlockPos at = BlockPos.of(state.pos());
        if (!level.isLoaded(at)) {
            return 0;
        }
        return StageGhost.closestLane(HearthDesigns.shipped(), StageBlocks.probe(level, at, HearthData.DEFAULTS),
                state.stage() + 1);
    }

    // ---- Tick ---------------------------------------------------------------------------------------

    /** Once per client tick: the switch-off conditions, and the rebuild every few ticks. */
    public static void tick(Minecraft minecraft) {
        if (!on) {
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            off();
            return;
        }
        HearthStatePayload state = HearthView.state();
        BlockPos at = BlockPos.of(pos);
        BlockState fire = level.isLoaded(at) ? level.getBlockState(at) : null;
        // A plain campfire's guide (stage 0, opened from its panel) has no range of its own: it ends past the draw range.
        boolean campfire = fire != null && fire.is(net.minecraft.world.level.block.Blocks.CAMPFIRE);
        boolean hearth = fire == null || fire.getBlock() instanceof HearthBlock;
        if (!dimension.isEmpty() && !dimension.equals(level.dimension().location().toString())
                || !campfire && !hearth
                || hearth && !HearthView.home() && !near(minecraft, at)
                || campfire && !near(minecraft, at)) {
            off();
            return;
        }
        if (patchouli) {
            // Patchouli draws, checks and ends the guide itself; only the switch-off above runs here.
            if (!HearthVisualize.showingAny()) {
                on = false;
            }
            return;
        }
        if (!dirty && ++ticks < REBUILD_TICKS) {
            return;
        }
        ticks = 0;
        dirty = false;
        rebuild(level, at, state != null && state.pos() == pos ? state.stage() : stageOf(level, at));
    }

    /** The player is within the draw range of the fire. */
    private static boolean near(Minecraft minecraft, BlockPos at) {
        return minecraft.player.position().distanceToSqr(at.getCenter()) <= DRAW_RANGE * DRAW_RANGE;
    }

    private static int stageOf(ClientLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof HearthBlock ? state.getValue(HearthBlock.STAGE) : 0;
    }

    private static void rebuild(ClientLevel level, BlockPos origin, int stage) {
        GHOSTS.clear();
        int next = stage + 1;
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        if (next > HearthDesigns.STAGES || lanes.isEmpty()) {
            return;
        }
        HearthDesigns.Lane laneDesign = lanes.get(Math.floorMod(lane, lanes.size()));
        HearthDesigns.Stage design = laneDesign.stage(next);
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        for (StageGhost.Cell cell : StageGhost.diff(design, StageBlocks.probe(level, origin, HearthData.DEFAULTS))) {
            BlockPos at = origin.offset(cell.dx(), cell.dy(), cell.dz());
            int light = LevelRenderer.getLightColor(level, at);
            if (cell.kind() == StageGhost.Kind.BLOCKED) {
                GHOSTS.add(new Ghost(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null, true, light, 0,
                        cell));
                continue;
            }
            if (HearthDesigns.isFluid(cell.role())) {
                // A liquid has no baked block model: a tinted box up to the surface stands for it.
                GHOSTS.add(new Ghost(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null, false, light,
                        cell.role() == 'v' ? WATER_TINT : LAVA_TINT, cell));
                continue;
            }
            BlockState state = roleState(laneDesign, cell.role());
            if (state.isAir()) {
                continue;
            }
            // A block with no model of its own (a banner) is outlined where it goes.
            boolean outline = state.getRenderShape() != RenderShape.MODEL;
            GHOSTS.add(new Ghost(at, state, outline ? null : blocks.getBlockModel(state), outline, light, 0, cell));
        }
    }

    /** The lane's suggested block for a role, resolved; cached per lane and role (the models are looked up at each rebuild). */
    private static BlockState roleState(HearthDesigns.Lane lane, char role) {
        if (role >= 128) {
            return StageBlocks.laneState(lane, role);
        }
        BlockState[] known = ROLE_STATE.computeIfAbsent(lane.id(), id -> new BlockState[128]);
        BlockState state = known[role];
        if (state == null) {
            state = StageBlocks.laneState(lane, role);
            known[role] = state;
        }
        return state;
    }

    // ---- Render -------------------------------------------------------------------------------------

    /** Once a frame, in the world pass; {@code stack} is the loader's world stack (camera rotation applied). */
    public static void render(PoseStack stack, Camera camera) {
        if (!on || GHOSTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eye = camera.getPosition();
        if (eye.distanceToSqr(BlockPos.of(pos).getCenter()) > DRAW_RANGE * DRAW_RANGE) {
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        // The cell list is up to REBUILD_TICKS old: a cell the world matches by now is skipped this frame.
        var probe = StageBlocks.probe(level, BlockPos.of(pos), HearthData.DEFAULTS);
        boolean[] done = new boolean[GHOSTS.size()];
        for (int i = 0; i < done.length; i++) {
            StageGhost.Cell cell = GHOSTS.get(i).cell();
            done[i] = probe.is(cell.role(), cell.dx(), cell.dy(), cell.dz());
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType blocks = Sheets.translucentCullBlockSheet();
        VertexConsumer consumer = buffers.getBuffer(blocks);
        int[] lights = new int[4];
        boolean any = false;
        for (int i = 0; i < done.length; i++) {
            Ghost ghost = GHOSTS.get(i);
            if (done[i] || ghost.outline() || ghost.fluid() != 0) {
                continue;
            }
            stack.pushPose();
            stack.translate(ghost.pos().getX() - eye.x + 0.5, ghost.pos().getY() - eye.y + 0.5,
                    ghost.pos().getZ() - eye.z + 0.5);
            stack.scale(INSET_SCALE, INSET_SCALE, INSET_SCALE);
            stack.translate(-0.5, -0.5, -0.5);
            java.util.Arrays.fill(lights, ghost.light());
            for (net.minecraft.core.Direction face : FACES_AND_NULL) {
                RANDOM.setSeed(42L);
                for (var quad : ghost.model().getQuads(ghost.state(), face, RANDOM)) {
                    // Both versions have this overload; the alpha goes into every vertex.
                    consumer.putBulkData(stack.last(), quad, FULL_BRIGHT, 1.0f, 1.0f, 1.0f, ALPHA, lights,
                            OverlayTexture.NO_OVERLAY, false);
                }
            }
            stack.popPose();
            any = true;
        }
        if (any) {
            buffers.endBatch(blocks);
        }
        boolean boxes = false;
        for (int i = 0; i < done.length; i++) {
            Ghost ghost = GHOSTS.get(i);
            int tint = ghost.fluid();
            if (tint == 0 || done[i]) {
                continue;
            }
            BlockPos at = ghost.pos();
            AABB box = new AABB(at.getX() + 0.01, at.getY() + 0.01, at.getZ() + 0.01, at.getX() + 0.99,
                    at.getY() + FLUID_TOP, at.getZ() + 0.99).move(-eye.x, -eye.y, -eye.z);
            DebugRenderer.renderFilledBox(stack, buffers, box, (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
                    (tint & 255) / 255f, (tint >>> 24) / 255f);
            boxes = true;
        }
        if (boxes) {
            buffers.endBatch(RenderType.debugFilledBox());
        }
        VertexConsumer lines = null;
        for (int i = 0; i < done.length; i++) {
            Ghost ghost = GHOSTS.get(i);
            if (!ghost.outline() || done[i]) {
                continue;
            }
            if (lines == null) {
                lines = buffers.getBuffer(RenderType.lines());
            }
            boolean blocked = ghost.state().isAir();
            AABB box = new AABB(ghost.pos()).inflate(0.002).move(-eye.x, -eye.y, -eye.z);
            LevelRenderer.renderLineBox(stack, lines, box, blocked ? 0.95f : 0.85f, blocked ? 0.15f : 0.85f,
                    blocked ? 0.12f : 0.95f, 0.8f);
        }
        if (lines != null) {
            buffers.endBatch(RenderType.lines());
        }
    }
}
