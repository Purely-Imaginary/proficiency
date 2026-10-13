package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Hearth's build guide through Patchouli's own multiblock visualizer, the one behind a Patchouli book's
 * "Visualize" button (Nature's Aura's Ritual of the Forest): the ghost follows the crosshair until a
 * right-click anchors it, then Patchouli draws the progress bar, the block under the crosshair, the red air
 * cells and "Complete!" with its sound. Nothing of Patchouli is copied or compiled against: it is called by
 * reflection when the pack has it (Pandowo, Reclamation), and {@link #available()} is false otherwise
 * (Lesznica), where the panel keeps {@link HearthGhost}.
 *
 * <p>A stage becomes a sparse multiblock keyed by its offsets from the fire. Patchouli puts a sparse
 * multiblock's origin one block above the anchor, so a right-click on the ground puts the fire on it, and the
 * panel anchors at the block under the Hearth. Every multiblock is symmetrical (never rotated) because the
 * stage check reads the design in one orientation.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class HearthVisualize {

    /** Patchouli's eye button: its book texture, the icon's corner, size, and the texture's size. */
    private static final ResourceLocation EYE_TEXTURE = ResourceLocation.tryParse("patchouli:textures/gui/book_brown.png");
    private static final int EYE_U = 308;
    private static final int EYE_V = 31;
    public static final int EYE = 11;

    private static boolean resolved;
    private static boolean ready;
    private static Object api;
    private static Method makeSparse;
    private static Method predicateMatcher;
    private static Method airMatcher;
    private static Method showMultiblock;
    private static Method clearMultiblock;
    private static Method currentMultiblock;
    private static Method setSymmetrical;
    private static Method setMultiblock;
    private static Field hasMultiblock;
    /** Patchouli's private ticks-since-complete counter; null when it cannot be reached. */
    @Nullable
    private static Field timeComplete;
    /** Built multiblocks per lane id and stage: the cells are fixed, the tags are read at each test. */
    private static final Map<String, Object> BUILT = new HashMap<>();

    private HearthVisualize() {
    }

    /** Whether Patchouli is installed and its API answers (not its stub). */
    public static boolean available() {
        if (!resolved) {
            resolved = true;
            ready = resolve();
        }
        return ready;
    }

    private static boolean resolve() {
        try {
            ClassLoader loader = HearthVisualize.class.getClassLoader();
            Class<?> entry = Class.forName("vazkii.patchouli.api.PatchouliAPI", true, loader);
            Class<?> iface = Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI", true, loader);
            Class<?> multiblock = Class.forName("vazkii.patchouli.api.IMultiblock", true, loader);
            Class<?> handler = Class.forName("vazkii.patchouli.client.handler.MultiblockVisualizationHandler", true, loader);
            Class<?> bookmark = Class.forName("vazkii.patchouli.client.base.PersistentData$Bookmark", true, loader);
            api = entry.getMethod("get").invoke(null);
            if ((boolean) iface.getMethod("isStub").invoke(api)) {
                return false;
            }
            makeSparse = iface.getMethod("makeSparseMultiblock", Map.class);
            predicateMatcher = iface.getMethod("predicateMatcher", BlockState.class, Predicate.class);
            airMatcher = iface.getMethod("airMatcher");
            showMultiblock = iface.getMethod("showMultiblock", multiblock, Component.class, BlockPos.class, Rotation.class);
            clearMultiblock = iface.getMethod("clearMultiblock");
            currentMultiblock = iface.getMethod("getCurrentMultiblock");
            setSymmetrical = multiblock.getMethod("setSymmetrical", boolean.class);
            setMultiblock = handler.getMethod("setMultiblock", multiblock, Component.class, bookmark, boolean.class);
            hasMultiblock = handler.getField("hasMultiblock");
            try {
                timeComplete = handler.getDeclaredField("timeComplete");
                timeComplete.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                timeComplete = null;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return false;
        }
    }

    /** Whether this lane's stage is the structure Patchouli shows now. */
    public static boolean showing(int lane, int stage) {
        if (!available()) {
            return false;
        }
        try {
            Object built = BUILT.get(key(lane, stage));
            return built != null && hasMultiblock.getBoolean(null) && currentMultiblock.invoke(api) == built;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Whether Patchouli shows any of the Hearth's stages now. */
    public static boolean showingAny() {
        if (!available()) {
            return false;
        }
        try {
            Object current = hasMultiblock.getBoolean(null) ? currentMultiblock.invoke(api) : null;
            return current != null && BUILT.containsValue(current);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * The guide's Visualize button, as a Patchouli book page's: shows the stage unanchored (it follows the
     * crosshair until a right-click), or, when any structure is shown already, clears it. Returns whether
     * this stage is shown afterwards.
     */
    public static boolean toggle(int lane, int stage) {
        Object built = multiblock(lane, stage);
        if (built == null) {
            return false;
        }
        try {
            resetComplete();
            setMultiblock.invoke(null, built, name(lane, stage), null, true);
        } catch (ReflectiveOperationException e) {
            return false;
        }
        return showing(lane, stage);
    }

    /** The panel's build guide: the stage anchored on this Hearth (the fire at {@code fire}), shown at once. */
    public static boolean showAt(int lane, int stage, BlockPos fire) {
        Object built = multiblock(lane, stage);
        if (built == null) {
            return false;
        }
        try {
            resetComplete();
            showMultiblock.invoke(api, built, name(lane, stage), fire.below(), Rotation.NONE);
        } catch (ReflectiveOperationException e) {
            return false;
        }
        return showing(lane, stage);
    }

    /**
     * Zeroes Patchouli's ticks-since-complete counter. After a "Complete!" the old structure stays anchored and
     * complete, so the counter keeps climbing until a tick sees something unanchored; a structure shown
     * anchored at once (the panel's guide) would then be dropped by the next HUD frame, as if the first click
     * did nothing. Patchouli's API does not reset it (seen in a real client, 1.9.0 test).
     */
    private static void resetComplete() {
        if (timeComplete != null) {
            try {
                timeComplete.setInt(null, 0);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Unreachable after all: the second click still works.
            }
        }
    }

    /** Clears whatever structure Patchouli shows. */
    public static void clear() {
        if (!available()) {
            return;
        }
        try {
            clearMultiblock.invoke(api);
        } catch (ReflectiveOperationException ignored) {
            // Patchouli changed: nothing to clear through it.
        }
    }

    /** Patchouli's eye button at {@code x, y} ({@value #EYE} square), the hovered half when {@code hovered}. */
    public static void drawEye(GuiGraphics graphics, int x, int y, boolean hovered) {
        if (EYE_TEXTURE != null) {
            graphics.blit(EYE_TEXTURE, x, y, EYE_U + (hovered ? EYE : 0), EYE_V, EYE, EYE, 512, 256);
        }
    }

    /** The eye button's tooltip, in Patchouli's own words and languages. */
    public static List<Component> eyeTooltip() {
        return List.of(Component.translatable("patchouli.gui.lexicon.button.visualize"),
                Component.translatable("patchouli.gui.lexicon.button.visualize.info")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    // ---- Building ---------------------------------------------------------------------------------

    private static String key(int lane, int stage) {
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        return lanes.isEmpty() ? "" : lanes.get(Math.floorMod(lane, lanes.size())).id() + "/" + stage;
    }

    /** "II · Hearthstead (Longhouse)": the stage heading and the lane, for Patchouli's title line. */
    private static Component name(int lane, int stage) {
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        Component heading = Component.translatable("proficiency.hearth.client.panel.heading", HearthText.roman(stage),
                HearthText.stageName(stage));
        if (lanes.isEmpty()) {
            return heading;
        }
        String id = lanes.get(Math.floorMod(lane, lanes.size())).id();
        return Component.empty().append(heading).append(" (").append(HearthGuide.laneName(id)).append(")");
    }

    @Nullable
    private static Object multiblock(int lane, int stage) {
        List<HearthDesigns.Lane> lanes = HearthDesigns.shipped().lanes();
        if (!available() || lanes.isEmpty() || stage < 1 || stage > HearthDesigns.STAGES) {
            return null;
        }
        String key = key(lane, stage);
        Object built = BUILT.get(key);
        if (built != null) {
            return built;
        }
        try {
            HearthDesigns.Lane design = lanes.get(Math.floorMod(lane, lanes.size()));
            Map<BlockPos, Object> cells = new HashMap<>();
            for (HearthDesigns.Cell cell : design.stage(stage).cells()) {
                Object matcher = matcher(design, cell.role());
                if (matcher != null) {
                    cells.put(new BlockPos(cell.dx(), cell.dy(), cell.dz()), matcher);
                }
            }
            built = makeSparse.invoke(api, cells);
            setSymmetrical.invoke(built, true);
            BUILT.put(key, built);
            return built;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** A role's Patchouli matcher: shows the lane's block, accepts what the stage check accepts. */
    @Nullable
    private static Object matcher(HearthDesigns.Lane lane, char role) throws ReflectiveOperationException {
        if (role == '.') {
            return airMatcher.invoke(api);
        }
        if (role == 'F') {
            Predicate<BlockState> fire = state -> state.getBlock() instanceof HearthBlock || state.is(BlockTags.CAMPFIRES);
            return predicateMatcher.invoke(api, Blocks.CAMPFIRE.defaultBlockState(), fire);
        }
        if (HearthDesigns.isFluid(role)) {
            Predicate<BlockState> source = state -> StageBlocks.isFluidSource(role, state);
            return predicateMatcher.invoke(api, StageBlocks.defaultState(role), source);
        }
        TagKey<Block> tag = StageBlocks.roleTag(role);
        if (tag == null) {
            return null;
        }
        BlockState shown = StageBlocks.laneState(lane, role);
        if (shown.isAir()) {
            shown = StageBlocks.defaultState(role);
        }
        Predicate<BlockState> holds = state -> state.is(tag);
        return predicateMatcher.invoke(api, shown, holds);
    }
}
