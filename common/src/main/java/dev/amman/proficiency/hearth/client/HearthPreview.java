package dev.amman.proficiency.hearth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The guide's spinning preview of a lane's stage: the design's real block models (the role's default block,
 * the Hearth itself at the fire) drawn in the GUI, turning once every {@value #TURN_SECONDS} seconds. The
 * cell lists are built once per lane and stage and kept, with the states and models resolved, so a frame
 * only walks an array. Cells buried inside the build (all six neighbours solid) are not drawn. Each stage
 * is scaled to its own size, so a 3 x 3 Firepit fills its square as well as a 9 x 9 Heart of the Home (the
 * text beside it gives the size; a fixed scale left stage I a speck).
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class HearthPreview {

    static final int TURN_SECONDS = 16;
    /** The 9 x 9 x 7 box's diagonal view fits this many blocks across: the preview's size divided by it is one block. */
    private static final float FIT = 12.5f;
    private static final float PITCH = 28f;
    private static final float START_YAW = 45f;
    /** The box's centre (the fire's column, two above the fire's layer), in block corners. */
    private static final float CENTRE_Y = 2.5f;
    /** The tints of a water source (blue) and a lava source (orange), ARGB, drawn as boxes up to the 14/16 surface. */
    private static final int WATER_TINT = 0xB33F76E4;
    private static final int LAVA_TINT = 0xD9FF8C1A;

    /** A stage's drawable cells: offsets, states and models, parallel arrays. */
    private static final class Model {
        final int[] at;
        final BlockState[] states;
        final BakedModel[] models;
        /** The fluid source cells (offsets, three ints each) and their tint (ARGB): drawn as tinted boxes. */
        int[] fluidAt = new int[0];
        int[] fluidTint = new int[0];
        /** How many blocks across the square shows, and the height the turn centres on: from the stage's own extent. */
        float fit = FIT;
        float centreY = CENTRE_Y;

        Model(int n) {
            at = new int[n * 3];
            states = new BlockState[n];
            models = new BakedModel[n];
        }
    }

    private static final Model[][] CACHE = new Model[HearthDesigns.MAX_LANES][HearthDesigns.STAGES + 1];

    private HearthPreview() {
    }

    /** Forgets the cached models (a resource reload replaced them). */
    static void clear() {
        for (Model[] lane : CACHE) {
            java.util.Arrays.fill(lane, null);
        }
    }

    private static Model model(int lane, int stage) {
        Model model = CACHE[lane][stage];
        if (model == null) {
            HearthDesigns.Lane design = HearthDesigns.shipped().lanes().get(lane);
            model = build(design, design.stage(stage), stage);
            CACHE[lane][stage] = model;
        }
        return model;
    }

    private static Model build(HearthDesigns.Lane lane, HearthDesigns.Stage design, int stage) {
        int size = HearthDesigns.SIZE;
        int height = HearthDesigns.MAX_Y - HearthDesigns.MIN_Y + 1;
        BlockState[] grid = new BlockState[size * size * height];
        List<HearthDesigns.Cell> cells = new ArrayList<>();
        List<HearthDesigns.Cell> fluids = new ArrayList<>();
        for (HearthDesigns.Cell cell : design.cells()) {
            if (HearthDesigns.isFluid(cell.role())) {
                fluids.add(cell);
                continue;
            }
            BlockState state = cell.role() == 'F' ? hearth(stage) : StageBlocks.laneState(lane, cell.role());
            if (cell.role() == 'x' && !state.isAir() && state.getRenderShape() != RenderShape.MODEL) {
                // A chest has no block model (a block entity draws it): a barrel stands for storage.
                state = net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState();
            }
            if (state.isAir() || cell.role() != 'F' && state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            grid[index(cell.dx(), cell.dy(), cell.dz())] = state;
            cells.add(cell);
        }
        List<HearthDesigns.Cell> shown = new ArrayList<>();
        for (HearthDesigns.Cell cell : cells) {
            if (!buried(grid, cell)) {
                shown.add(cell);
            }
        }
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        Model model = new Model(shown.size());
        int radius = 1;
        int low = 0;
        // The Hearth's own model rises about two blocks above the fire cell.
        int high = 2;
        List<HearthDesigns.Cell> extent = new ArrayList<>(cells);
        extent.addAll(fluids);
        for (HearthDesigns.Cell cell : extent) {
            radius = Math.max(radius, Math.max(Math.abs(cell.dx()), Math.abs(cell.dz())));
            low = Math.min(low, cell.dy());
            high = Math.max(high, cell.dy() + 1);
        }
        // The diagonal of the footprint seen from above at the pitch, or the height, whichever is wider.
        model.fit = Math.max(4.5f, Math.max((2 * radius + 1) * 1.45f, (high - low) * 1.7f + 1f));
        model.centreY = (low + high) / 2f;
        for (int i = 0; i < shown.size(); i++) {
            HearthDesigns.Cell cell = shown.get(i);
            BlockState state = grid[index(cell.dx(), cell.dy(), cell.dz())];
            model.at[i * 3] = cell.dx();
            model.at[i * 3 + 1] = cell.dy();
            model.at[i * 3 + 2] = cell.dz();
            model.states[i] = state;
            model.models[i] = blocks.getBlockModel(state);
        }
        model.fluidAt = new int[fluids.size() * 3];
        model.fluidTint = new int[fluids.size()];
        for (int i = 0; i < fluids.size(); i++) {
            HearthDesigns.Cell cell = fluids.get(i);
            model.fluidAt[i * 3] = cell.dx();
            model.fluidAt[i * 3 + 1] = cell.dy();
            model.fluidAt[i * 3 + 2] = cell.dz();
            model.fluidTint[i] = cell.role() == 'v' ? WATER_TINT : LAVA_TINT;
        }
        return model;
    }

    private static BlockState hearth(int stage) {
        return HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, Math.max(1, Math.min(6, stage)));
    }

    private static int index(int dx, int dy, int dz) {
        return ((dy - HearthDesigns.MIN_Y) * HearthDesigns.SIZE + (dz + HearthDesigns.RADIUS)) * HearthDesigns.SIZE
                + (dx + HearthDesigns.RADIUS);
    }

    private static boolean inside(int dx, int dy, int dz) {
        return Math.abs(dx) <= HearthDesigns.RADIUS && Math.abs(dz) <= HearthDesigns.RADIUS
                && dy >= HearthDesigns.MIN_Y && dy <= HearthDesigns.MAX_Y;
    }

    private static boolean solid(BlockState[] grid, int dx, int dy, int dz) {
        if (!inside(dx, dy, dz)) {
            return false;
        }
        BlockState state = grid[index(dx, dy, dz)];
        return state != null && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static boolean buried(BlockState[] grid, HearthDesigns.Cell c) {
        return solid(grid, c.dx() + 1, c.dy(), c.dz()) && solid(grid, c.dx() - 1, c.dy(), c.dz())
                && solid(grid, c.dx(), c.dy() + 1, c.dz()) && solid(grid, c.dx(), c.dy() - 1, c.dz())
                && solid(grid, c.dx(), c.dy(), c.dz() + 1) && solid(grid, c.dx(), c.dy(), c.dz() - 1);
    }

    /**
     * Draws lane {@code lane}'s stage {@code stage} (1..6) in the square at ({@code x}, {@code y}) of
     * {@code size} pixels, turned by the clock. The caller's scissor clips it.
     */
    public static void draw(GuiGraphics graphics, int lane, int stage, int x, int y, int size, long millis) {
        if (lane < 0 || lane >= CACHE.length || stage < 1 || stage > HearthDesigns.STAGES) {
            return;
        }
        Model model = model(lane, stage);
        float yaw = START_YAW + (millis % (TURN_SECONDS * 1000L)) * (360f / (TURN_SECONDS * 1000f));
        float scale = size / model.fit;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + size / 2f, y + size / 2f, 100f);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(PITCH));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5f, -model.centreY, -0.5f);
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        int light = LightTexture.FULL_BRIGHT;
        for (int i = 0; i < model.states.length; i++) {
            pose.pushPose();
            pose.translate(model.at[i * 3], model.at[i * 3 + 1], model.at[i * 3 + 2]);
            renderer.renderModel(pose.last(), consumer, model.states[i], model.models[i], 1.0f, 1.0f, 1.0f, light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        graphics.flush();
        if (model.fluidTint.length > 0) {
            // Liquids have no block model: tinted boxes after the solid blocks, so they blend over them.
            for (int i = 0; i < model.fluidTint.length; i++) {
                int tint = model.fluidTint[i];
                float bx = model.fluidAt[i * 3];
                float by = model.fluidAt[i * 3 + 1];
                float bz = model.fluidAt[i * 3 + 2];
                DebugRenderer.renderFilledBox(pose, buffers, new AABB(bx + 0.01, by + 0.01, bz + 0.01, bx + 0.99,
                        by + 14.0 / 16.0, bz + 0.99), (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
                        (tint & 255) / 255f, (tint >>> 24) / 255f);
            }
            buffers.endBatch(RenderType.debugFilledBox());
        }
        // Draw now, inside the caller's scissor and before the next row's 2D.
        graphics.flush();
        pose.popPose();
    }
}
