package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Hearth guide opened from the Homebuilder tree: how to build each stage, what feeds which pool and
 * what Comfort and Character count, in one scrolling list per tab. It reads the shipped defaults, the
 * server's numbers where the last state packet carried them, and, while the player is home, marks what
 * that Hearth already has (sealed stages, Comfort kinds in the home, Character).
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava); the two
 * {@code HearthGuideScreen} classes only add the panel, the tabs and the version-specific overrides.
 */
public final class HearthGuide {

    public enum Tab {
        BUILD, BOONS, COMFORT;

        public Component label() {
            return Component.translatable(KEY + "tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    static final String KEY = "proficiency.hearth.guide.";
    private static final int ICON = 16;
    private static final int ICON_GAP = 5;
    private static final int LINE = 10;
    private static final int ROW_GAP = 6;
    private static final int OK = 0xFF8FD46A;
    /** The spinning preview's square, and the gap between it and the text. */
    static final int PREVIEW = 72;
    private static final int PREVIEW_GAP = 8;
    /** The Visualize eye's gap from the preview's left and bottom edges. */
    private static final int EYE_INSET = 2;

    /**
     * One entry of the list. A section heading has only {@code title}; a paragraph only {@code body}.
     * {@code item} is drawn under the icon (a stage's offering), level with its "Offering" line.
     */
    private record Row(@Nullable ResourceLocation icon, ItemStack item, String title, int titleColour,
            String right, int rightColour, List<Line> body, boolean section, boolean marked,
            List<Component> tip, int height, int preview) {
    }

    /** One wrapped body line and its colour. */
    private record Line(String text, int colour) {
    }

    private final List<Row> rows = new ArrayList<>();
    /** Patchouli's Visualize eyes drawn this frame, inside the view: x, y and the stage of each. */
    private final List<int[]> eyes = new ArrayList<>();
    private Tab builtTab;
    private int builtWidth = -1;
    /** The lane the Build tab shows (an index into the shipped designs); -1 until the first build picks one. */
    private int lane = -1;
    private int builtLane = -1;
    private String builtState = "";

    /** The button's icon on the Homebuilder tree: the Firepit's. */
    public static ResourceLocation buttonIcon() {
        return TrackerHud.icon("stage/1");
    }

    /** The lanes the picker offers, in the designs' order. */
    public static List<HearthDesigns.Lane> lanes() {
        return HearthDesigns.shipped().lanes();
    }

    /** A lane's name ("Longhouse"). */
    public static Component laneName(String id) {
        return Component.translatable(KEY + "lane." + id);
    }

    /** The lane the Build tab shows: the one this Hearth's next stage is closest to when at home, else the first. */
    public int lane() {
        if (lane < 0) {
            lane = HearthGhost.closestLane(homeState());
        }
        return lane;
    }

    public void setLane(int next) {
        lane = Math.max(0, Math.min(lanes().size() - 1, next));
    }

    /**
     * The line under the tabs: this Hearth's stage, Comfort and Character, or how to see them. The short
     * form (narrow windows) drops "Your Hearth" and the stage's name and keeps the three numbers.
     */
    public static Component status(boolean compact) {
        HearthStatePayload state = homeState();
        if (state == null) {
            return Component.translatable(KEY + "status.away");
        }
        int stage = state.stage();
        Component stageText = stage <= 0 ? Component.translatable("proficiency.hearth.client.stage.none")
                : compact ? Component.translatable(KEY + "stage", HearthText.roman(stage))
                : Component.translatable("proficiency.hearth.client.panel.heading", HearthText.roman(stage),
                        HearthText.stageName(stage));
        return Component.translatable(KEY + (compact ? "status.compact" : "status.home"), stageText,
                HearthText.comfort(state.comfort(), HearthView.comfortAll().size()),
                Component.translatable(KEY + "character", state.character()));
    }

    /** The state packet of the Hearth the player stands by, or null when away from every Hearth. */
    @Nullable
    static HearthStatePayload homeState() {
        return HearthView.home() ? HearthView.state() : null;
    }

    // ---- Content ------------------------------------------------------------------------------------

    private void rebuild(Tab tab, Font font, int width) {
        HearthStatePayload state = homeState();
        String stateKey = state == null ? "" : state.stage() + "/" + state.ceiling() + "/" + state.comfortKinds()
                + "/" + state.character();
        int laneNow = tab == Tab.BUILD ? lane() : 0;
        if (tab == builtTab && width == builtWidth && stateKey.equals(builtState) && laneNow == builtLane) {
            return;
        }
        builtLane = laneNow;
        builtTab = tab;
        builtWidth = width;
        builtState = stateKey;
        rows.clear();
        switch (tab) {
            case BUILD -> build(font, width, state);
            case BOONS -> boons(font, width, state);
            case COMFORT -> comfort(font, width, state);
        }
    }

    private void build(Font font, int width, @Nullable HearthStatePayload state) {
        paragraph(font, width, Component.translatable(KEY + "build.intro"));
        HearthData data = HearthData.DEFAULTS;
        int stage = state == null ? -1 : state.stage();
        HearthDesigns.Lane design = lanes().get(lane());
        for (int i = 1; i <= HearthData.TIERS; i++) {
            ItemStack offering = stack(offering(i));
            Map<String, Integer> materials = StageMaterials.count(design.stage(i));
            List<Component> body = new ArrayList<>();
            body.add(Component.translatable(KEY + "design." + design.id() + "." + i));
            body.addAll(materialLines(font, width - PREVIEW - PREVIEW_GAP - ICON - ICON_GAP, materials));
            if (!offering.isEmpty()) {
                body.add(Component.translatable("proficiency.hearth.client.panel.offering", offering.getHoverName()));
            }
            int need = data.character().forStage(i);
            String right = "";
            int rightColour = SkillPalette.TEXT_DIM;
            boolean marked = false;
            if (stage >= i) {
                right = "✔ " + Component.translatable("proficiency.hearth.client.panel.sealed").getString();
                rightColour = OK;
            } else if (stage + 1 == i) {
                right = Component.translatable(KEY + "next").getString();
                rightColour = HearthPalette.EMBER;
                marked = true;
            }
            if (need > 0) {
                boolean met = state != null && state.character() >= need;
                body.add(Component.translatable(KEY + "build.character", need).withStyle(style -> style.withColor(
                        (met ? OK : HearthPalette.LOCKED) & 0xFFFFFF)));
            }
            String title = Component.translatable("proficiency.hearth.client.panel.heading", HearthText.roman(i),
                    HearthText.stageName(i)).getString();
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(title).withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)));
            tip.add(Component.translatable(KEY + "design." + design.id() + "." + i).withStyle(ChatFormatting.GRAY));
            for (Map.Entry<String, Integer> each : materials.entrySet()) {
                tip.add(Component.translatable(KEY + "build.tip.line", Component.translatable(KEY + "mat." + each.getKey()),
                        amount(each.getKey(), each.getValue())));
            }
            tip.add(Component.translatable(KEY + "build.tip.any").withStyle(ChatFormatting.DARK_GRAY));
            entry(font, width, TrackerHud.icon("stage/" + i), offering, title,
                    i == HearthData.TIERS ? HearthPalette.GOLD : HearthPalette.EMBER, right, rightColour, body,
                    marked, tip, i);
        }
        section(font, width, Component.translatable(KEY + "build.rules"));
        HearthGeometry.Range range = state == null ? HearthGeometry.Range.DEFAULT
                : new HearthGeometry.Range(state.radius(), state.below(), state.above());
        bullet(font, width, Component.translatable(KEY + "build.range", Math.round(range.radius()), range.below(),
                range.above()));
        bullet(font, width, Component.translatable(KEY + "build.spacing", HearthView.spacing()));
        bullet(font, width, Component.translatable(KEY + "build.problem"));
        bullet(font, width, Component.translatable(KEY + "build.layer"));
        bullet(font, width, Component.translatable(KEY + "build.put_out"));
        bullet(font, width, Component.translatable(KEY + "build.sieges"));
        bullet(font, width, Component.translatable(KEY + "build.hover"));
    }

    /**
     * "Materials: Stone 24 · Wood 32 · Iron bars 4", wrapped between materials so a name never splits from
     * its count, each line in the bright text colour.
     */
    private static List<Component> materialLines(Font font, int width, Map<String, Integer> materials) {
        List<Component> out = new ArrayList<>();
        String prefix = Component.translatable(KEY + "build.materials", "").getString();
        StringBuilder line = new StringBuilder(prefix);
        boolean empty = true;
        for (Map.Entry<String, Integer> each : materials.entrySet()) {
            String item = Component.translatable(KEY + "mat." + each.getKey()).getString() + " "
                    + amount(each.getKey(), each.getValue()).getString();
            if (!empty && font.width(line + " · " + item + " ·") > width) {
                out.add(bright(line + " ·"));
                line = new StringBuilder(item);
            } else {
                line.append(empty ? "" : " · ").append(item);
            }
            empty = false;
        }
        out.add(bright(line.toString()));
        return out;
    }

    /** A material's amount: the block count, or "2 buckets" for a fluid (one source block is one bucket). */
    private static Component amount(String group, int count) {
        if (!StageMaterials.inBuckets(group)) {
            return Component.literal(Integer.toString(count));
        }
        int mod100 = count % 100;
        int mod10 = count % 10;
        String form = count == 1 ? "one" : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "few" : "many";
        return Component.translatable(KEY + "mat.bucket." + form, count);
    }

    private static Component bright(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(SkillPalette.TEXT & 0xFFFFFF));
    }

    private void boons(Font font, int width, @Nullable HearthStatePayload state) {
        HearthData data = HearthData.DEFAULTS;
        paragraph(font, width, Component.translatable(KEY + "boons.intro",
                Math.round(data.mirrorShare() * 100)));
        entry(font, width, TrackerHud.icon("warmth"), ItemStack.EMPTY,
                Component.translatable(KEY + "boons.warmth").getString(), HearthPalette.EMBER, "", 0,
                List.of(Component.translatable(KEY + "boons.warmth.body",
                        Math.round(data.warmth().riseSeconds()))), false, List.of());
        for (HearthPool pool : HearthPool.VALUES) {
            HearthData.PoolSpec spec = data.pool(pool);
            if (spec == null || spec.boon() == null) {
                continue;
            }
            String boon = spec.boon().name().toLowerCase(Locale.ROOT);
            Component first = HearthText.boonText(boon, (float) boonValue(spec, 1, data), (float) boonExtra(spec, 1, data));
            Component top = HearthText.boonText(boon, (float) boonValue(spec, HearthData.TIERS, data),
                    (float) boonExtra(spec, HearthData.TIERS, data));
            List<Component> body = new ArrayList<>();
            body.add(Component.translatable("proficiency.hearth.client.panel.feeds." + pool.id()));
            if (first != null && top != null) {
                body.add(Component.translatable(KEY + "boons.range", first, top));
            }
            String right = "";
            if (state != null) {
                int tier = tierOf(state, pool);
                if (tier > 0) {
                    right = Component.translatable(KEY + "boons.tier", HearthText.roman(tier)).getString();
                }
            }
            int colour = HearthPalette.pool(pool.ordinal());
            entry(font, width, TrackerHud.poolIcon(pool.ordinal()), ItemStack.EMPTY,
                    HearthText.pool(pool.ordinal()).getString(), colour, right, colour, body, false, List.of());
        }
        section(font, width, Component.translatable(KEY + "boons.tiers"));
        StringBuilder thresholds = new StringBuilder();
        double[] values = HearthView.thresholds();
        for (int i = 0; i < values.length && i < HearthData.TIERS; i++) {
            if (i > 0) {
                thresholds.append(" · ");
            }
            thresholds.append(HearthText.roman(i + 1)).append(' ').append(Math.round(values[i]));
        }
        bullet(font, width, Component.translatable(KEY + "boons.thresholds", thresholds.toString()));
        bullet(font, width, Component.translatable(KEY + "boons.cap", Math.round(data.hourlyCap())));
        bullet(font, width, Component.translatable(KEY + "boons.ceiling"));
        bullet(font, width, Component.translatable("proficiency.hearth.client.panel.comfort.gates"));
        bullet(font, width, Component.translatable(KEY + "boons.decay"));
    }

    private void comfort(Font font, int width, @Nullable HearthStatePayload state) {
        paragraph(font, width, Component.translatable(KEY + "comfort.intro"));
        for (String kind : HearthView.comfortAll()) {
            String right = "";
            int rightColour = SkillPalette.TEXT_DIM;
            if (state != null) {
                boolean has = state.comfortKinds().contains(kind);
                right = Component.translatable(has ? "proficiency.hearth.client.panel.kind.has"
                        : "proficiency.hearth.client.panel.kind.missing").getString();
                rightColour = has ? OK : HearthPalette.LOCKED;
            }
            entry(font, width, TrackerHud.icon("comfort/" + kind), ItemStack.EMPTY, HearthText.kind(kind).getString(),
                    SkillPalette.TEXT, right, rightColour, List.of(HearthText.kindDesc(kind)), false, List.of());
        }
        section(font, width, Component.translatable(KEY + "comfort.character"));
        HearthData data = HearthData.DEFAULTS;
        StringBuilder needs = new StringBuilder();
        for (int i = 1; i <= HearthData.TIERS; i++) {
            int need = data.character().forStage(i);
            if (need > 0) {
                if (needs.length() > 0) {
                    needs.append(" · ");
                }
                needs.append(HearthText.roman(i)).append(' ').append(need);
            }
        }
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable("proficiency.hearth.client.panel.character.tip"));
        body.add(Component.translatable("proficiency.hearth.client.panel.character.gates"));
        body.add(Component.translatable(KEY + "comfort.character.needs", needs.toString()));
        String right = state == null ? ""
                : Component.translatable(KEY + "character", state.character()).getString();
        entry(font, width, TrackerHud.CHARACTER_ICON, ItemStack.EMPTY,
                Component.translatable(KEY + "comfort.character.title").getString(), SkillPalette.TEXT, right,
                SkillPalette.TEXT, body, false, List.of());
    }

    // ---- Rows ---------------------------------------------------------------------------------------

    private void paragraph(Font font, int width, Component text) {
        List<Line> lines = new ArrayList<>();
        for (String line : HudKit.wrap(font, text.getString(), width)) {
            lines.add(new Line(line, SkillPalette.TEXT_DIM));
        }
        rows.add(new Row(null, ItemStack.EMPTY, "", 0, "", 0, lines, false, false, List.of(),
                lines.size() * LINE + ROW_GAP, 0));
    }

    private void bullet(Font font, int width, Component text) {
        List<String> wrapped = HudKit.wrap(font, text.getString(), width - 8);
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new Line((i == 0 ? "• " : "  ") + wrapped.get(i), SkillPalette.TEXT));
        }
        rows.add(new Row(null, ItemStack.EMPTY, "", 0, "", 0, lines, false, false, List.of(),
                lines.size() * LINE + 2, 0));
    }

    private void section(Font font, int width, Component text) {
        rows.add(new Row(null, ItemStack.EMPTY, HudKit.clip(font, text.getString(), width), HearthPalette.EMBER, "", 0,
                List.of(), true, false, List.of(), LINE + 8, 0));
    }

    private void entry(Font font, int width, @Nullable ResourceLocation icon, ItemStack item, String title, int titleColour,
            String right, int rightColour, List<Component> body, boolean marked, List<Component> tip) {
        entry(font, width, icon, item, title, titleColour, right, rightColour, body, marked, tip, 0);
    }

    /** {@code preview} is a stage (1..6) whose spinning model goes in a square to the left, or 0 for none. */
    private void entry(Font font, int width, @Nullable ResourceLocation icon, ItemStack item, String title, int titleColour,
            String right, int rightColour, List<Component> body, boolean marked, List<Component> tip, int preview) {
        int lead = preview > 0 ? PREVIEW + PREVIEW_GAP : 0;
        int indent = lead + ICON + ICON_GAP;
        int textW = width - indent;
        int rightW = right.isEmpty() ? 0 : font.width(right) + 6;
        String shownTitle = HudKit.clip(font, title, textW - rightW);
        if (!shownTitle.equals(title)) {
            HudKit.note("hearth.guide.title");
        }
        // A body part keeps its own colour (Character met or not); the rest is dim.
        List<Line> lines = new ArrayList<>();
        for (Component part : body) {
            int colour = part.getStyle().getColor() == null ? SkillPalette.TEXT_DIM
                    : part.getStyle().getColor().getValue() | 0xFF000000;
            for (String line : HudKit.wrap(font, part.getString(), textW)) {
                lines.add(new Line(line, colour));
            }
        }
        int height = Math.max(Math.max(item.isEmpty() ? ICON : ICON * 2 + 2, LINE + lines.size() * LINE),
                preview > 0 ? PREVIEW : 0) + ROW_GAP;
        rows.add(new Row(icon, item, shownTitle, titleColour, right, rightColour, lines, false, marked, tip, height,
                preview));
    }

    // ---- Drawing ------------------------------------------------------------------------------------

    /** Total height of the list for this tab and width, for the scroll limit. */
    public int contentHeight(Tab tab, Font font, int width) {
        rebuild(tab, font, width);
        int total = 0;
        for (Row row : rows) {
            total += row.height();
        }
        return total;
    }

    /**
     * Draws the list clipped to the view and returns the tooltip of the hovered row (empty when none).
     * The caller enables no scissor; this does.
     */
    public List<Component> render(GuiGraphics graphics, Font font, Tab tab, int x, int top, int width, int height,
            int scroll, int mouseX, int mouseY) {
        rebuild(tab, font, width);
        List<Component> tip = List.of();
        eyes.clear();
        boolean eyeOn = tab == Tab.BUILD && HearthVisualize.available();
        graphics.enableScissor(x - 4, top, x + width + 4, top + height);
        int y = top - scroll;
        for (Row row : rows) {
            if (y + row.height() > top && y < top + height) {
                drawRow(graphics, font, row, x, y, width);
                if (!row.tip().isEmpty() && mouseY >= Math.max(top, y) && mouseY < Math.min(top + height, y + row.height())
                        && mouseX >= x && mouseX < x + width) {
                    tip = row.tip();
                }
                if (eyeOn && row.preview() > 0) {
                    // Patchouli's Visualize eye, in the preview's bottom-left corner as on a book page.
                    int ex = x + EYE_INSET;
                    int ey = y + PREVIEW - HearthVisualize.EYE - EYE_INSET;
                    boolean over = mouseX >= ex && mouseX < ex + HearthVisualize.EYE && mouseY >= ey
                            && mouseY < ey + HearthVisualize.EYE && mouseY >= top && mouseY < top + height;
                    HearthVisualize.drawEye(graphics, ex, ey, over);
                    if (ey + HearthVisualize.EYE > top && ey < top + height) {
                        eyes.add(new int[] {ex, ey, row.preview()});
                    }
                    if (over) {
                        tip = HearthVisualize.eyeTooltip();
                    }
                }
            }
            y += row.height();
        }
        graphics.disableScissor();
        return tip;
    }

    /**
     * A click on a Visualize eye drawn last frame (the screen passes every click): toggles that stage of the
     * shown lane in Patchouli's visualizer. True when it was an eye. {@code top}/{@code height} are the view's.
     */
    public boolean clickEye(double mouseX, double mouseY, int top, int height) {
        if (mouseY < top || mouseY >= top + height) {
            return false;
        }
        for (int[] eye : eyes) {
            if (mouseX >= eye[0] && mouseX < eye[0] + HearthVisualize.EYE && mouseY >= eye[1]
                    && mouseY < eye[1] + HearthVisualize.EYE) {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                HearthVisualize.toggle(lane(), eye[2]);
                return true;
            }
        }
        return false;
    }

    private void drawRow(GuiGraphics graphics, Font font, Row row, int x, int y, int width) {
        if (row.section()) {
            graphics.fill(x, y + 2, x + width, y + 3, SkillPalette.PANEL_BORDER);
            graphics.drawString(font, row.title(), x, y + 6, row.titleColour(), false);
            return;
        }
        if (row.title().isEmpty()) {
            int ly = y;
            for (Line line : row.body()) {
                graphics.drawString(font, line.text(), x, ly, line.colour(), false);
                ly += LINE;
            }
            return;
        }
        if (row.marked()) {
            // The next stage to build: an ember bar down the left and a faint wash behind it.
            graphics.fill(x - 4, y - 2, x + width, y + row.height() - ROW_GAP + 2, 0x22E89A3C);
            graphics.fill(x - 4, y - 2, x - 2, y + row.height() - ROW_GAP + 2, HearthPalette.EMBER);
        }
        int lead = 0;
        if (row.preview() > 0) {
            lead = PREVIEW + PREVIEW_GAP;
            graphics.fill(x, y, x + PREVIEW, y + PREVIEW, 0x40000000);
            HearthPreview.draw(graphics, lane(), row.preview(), x, y, PREVIEW, net.minecraft.Util.getMillis());
        }
        int ix = x + lead;
        if (row.icon() != null) {
            if (HudKit.iconsOn()) {
                HudKit.icon(graphics, row.icon(), ix, y, ICON, 255);
            } else {
                graphics.fill(ix, y, ix + ICON, y + ICON, 0xFF181B20);
            }
        }
        int tx = ix + ICON + ICON_GAP;
        graphics.drawString(font, row.title(), tx, y + 1, row.titleColour(), false);
        if (!row.item().isEmpty()) {
            graphics.renderItem(row.item(), ix, y + ICON + 2);
        }
        if (!row.right().isEmpty()) {
            graphics.drawString(font, row.right(), x + width - font.width(row.right()), y + 1, row.rightColour(), false);
        }
        int ly = y + 1 + LINE;
        for (Line line : row.body()) {
            graphics.drawString(font, line.text(), tx, ly, line.colour(), false);
            ly += LINE;
        }
    }

    // ---- Data helpers -------------------------------------------------------------------------------

    /** A stage's offering: the server's when the last state packet carried them, else the shipped one. */
    private static String offering(int stage) {
        HearthStatePayload state = HearthView.state();
        if (state != null && state.extra() != null && state.extra().offerings().size() >= stage) {
            String id = state.extra().offerings().get(stage - 1);
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }
        List<HearthData.Stage> stages = HearthData.DEFAULTS.stages();
        return stage - 1 < stages.size() ? stages.get(stage - 1).offering() : "";
    }

    private static ItemStack stack(String id) {
        ResourceLocation key = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int tierOf(HearthStatePayload state, HearthPool pool) {
        for (var row : state.pools()) {
            if (row.pool() == pool.ordinal()) {
                return row.tier();
            }
        }
        return 0;
    }

    /** The same numbers the state packet sends ({@code HearthStatePayload.Extra}), for any tier. */
    private static double boonValue(HearthData.PoolSpec spec, int tier, HearthData data) {
        return switch (spec.boon()) {
            case READINESS -> HearthRules.warmthRiseSeconds(tier, data);
            case REFUND_CHANCE -> spec.refundAt(tier);
            default -> spec.value(tier);
        };
    }

    private static double boonExtra(HearthData.PoolSpec spec, int tier, HearthData data) {
        return switch (spec.boon()) {
            case REGEN_OUT_OF_COMBAT -> spec.absorptionPerTier() * tier;
            case STATION_SPEED -> spec.refundAt(tier);
            case READINESS -> HearthRules.provisionedMinutes(tier, data);
            default -> 0.0;
        };
    }
}
