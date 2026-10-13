package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.SkillPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hearth guide, opened from the Homebuilder tree: tabs Build, Boons and Comfort over one scrolling
 * list ({@link HearthGuide}), the player's own Hearth on the line under the tabs. Same panel look as the
 * Discovery Journal. forge-1.20.1 keeps a copy that differs only in the background and scroll overrides.
 */
public final class HearthGuideScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_MAX_HEIGHT = 340;
    private static final int PADDING = 10;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP = 3;
    private static final int BUTTON_HEIGHT = 16;
    private static final int SCROLL_STEP = 30;
    private static final int LANE_HEIGHT = 14;
    /** The lane picker: this many buttons to a row, as many rows as the lanes need (eight lanes: two rows of four). */
    private static final int LANE_COLUMNS = 4;
    /** Build tab only: the lane picker's rows above the status line. */
    private final int laneRows = (HearthGuide.lanes().size() + LANE_COLUMNS - 1) / LANE_COLUMNS;
    private final int laneBlock = laneRows * LANE_HEIGHT + (laneRows - 1) * TAB_GAP + 5;

    @Nullable
    private final Screen parent;
    private final HearthGuide guide = new HearthGuide();
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<Button> laneButtons = new ArrayList<>();
    private Button backButton;
    private HearthGuide.Tab tab = HearthGuide.Tab.BUILD;
    private int scroll;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int viewTop;
    private int viewHeight;
    private int baseViewTop;
    private int backTop;

    public HearthGuideScreen(@Nullable Screen parent) {
        super(Component.translatable(HearthGuide.KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, this.width - 8);
        panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 8);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        int inner = panelWidth - PADDING * 2;
        int count = HearthGuide.Tab.values().length;
        int tabW = (inner - TAB_GAP * (count - 1)) / count;
        int tabsTop = panelY + PADDING + 16;
        tabButtons.clear();
        for (HearthGuide.Tab each : HearthGuide.Tab.values()) {
            tabButtons.add(addWidget(Button.builder(each.label(), b -> select(each))
                    .bounds(panelX + PADDING + each.ordinal() * (tabW + TAB_GAP), tabsTop, tabW, TAB_HEIGHT).build()));
        }
        baseViewTop = tabsTop + TAB_HEIGHT + 18;
        backTop = panelY + panelHeight - PADDING - BUTTON_HEIGHT;
        // The lane picker: tab-like buttons in rows of their own, on the Build tab only.
        laneButtons.clear();
        List<dev.amman.proficiency.hearth.stages.HearthDesigns.Lane> lanes = HearthGuide.lanes();
        int columns = Math.min(LANE_COLUMNS, lanes.size());
        int laneW = (inner - TAB_GAP * (columns - 1)) / columns;
        int laneTop = tabsTop + TAB_HEIGHT + 5;
        for (int i = 0; i < lanes.size(); i++) {
            int index = i;
            laneButtons.add(addWidget(Button.builder(pickerName(lanes.get(i).id(), laneW), b -> guide.setLane(index))
                    .bounds(panelX + PADDING + i % columns * (laneW + TAB_GAP),
                            laneTop + i / columns * (LANE_HEIGHT + TAB_GAP), laneW, LANE_HEIGHT).build()));
        }
        layoutView();
        Component back = Component.translatable("proficiency.journal.back");
        int backWidth = Math.max(80, this.font.width(back) + 16);
        backButton = addWidget(Button.builder(back, b -> onClose())
                .bounds(panelX + (panelWidth - backWidth) / 2, backTop, backWidth, BUTTON_HEIGHT).build());
        clampScroll();
    }

    /** A lane's name for its picker button: the full name when it fits, else the lane's compact name (lang key {@code lane.<id>.compact}). */
    private Component pickerName(String id, int width) {
        Component full = HearthGuide.laneName(id);
        String key = HearthGuide.KEY + "lane." + id + ".compact";
        if (this.font.width(full) <= width - 8 || !net.minecraft.locale.Language.getInstance().has(key)) {
            return full;
        }
        return Component.translatable(key);
    }

    /** The list's top and height: the Build tab gives a row to the lane picker. */
    private void layoutView() {
        boolean build = tab == HearthGuide.Tab.BUILD;
        viewTop = baseViewTop + (build ? laneBlock : 0);
        viewHeight = Math.max(30, backTop - 6 - viewTop);
        for (Button button : laneButtons) {
            button.visible = build;
            button.active = build;
        }
    }

    private void select(HearthGuide.Tab next) {
        tab = next;
        scroll = 0;
        layoutView();
    }

    /** The layout demo's way in: the Build tab on lane {@code index}, at the top. */
    public void showLane(int index) {
        guide.setLane(index);
        select(HearthGuide.Tab.BUILD);
    }

    /** The layout demo's way in: a tab, scrolled to {@code to} pixels (clamped). */
    public void show(HearthGuide.Tab next, int to) {
        select(next);
        scroll = to;
        clampScroll();
    }

    private int listWidth() {
        return panelWidth - PADDING * 2 - 6;
    }

    private void clampScroll() {
        int max = Math.max(0, guide.contentHeight(tab, this.font, listWidth()) - viewHeight);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: it paints the widgets, and the panel has to go under them.
        renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SkillPalette.PANEL);
        border(graphics, panelX, panelY, panelWidth, panelHeight, SkillPalette.PANEL_BORDER);

        String title = HudKit.clip(this.font, this.title.getString(), panelWidth - PADDING * 2 - 12);
        int titleW = this.font.width(title) + (HudKit.iconsOn() ? 12 : 0);
        int titleX = panelX + (panelWidth - titleW) / 2;
        if (HudKit.iconsOn()) {
            HudKit.icon(graphics, HearthGuide.buttonIcon(), titleX, panelY + PADDING - 1, 9, 255);
            titleX += 12;
        }
        graphics.drawString(this.font, title, titleX, panelY + PADDING, HearthPalette.EMBER, false);

        for (int i = 0; i < tabButtons.size(); i++) {
            Button button = tabButtons.get(i);
            button.render(graphics, mouseX, mouseY, partialTick);
            if (i == tab.ordinal()) {
                graphics.fill(button.getX(), button.getY() + button.getHeight(),
                        button.getX() + button.getWidth(), button.getY() + button.getHeight() + 2, HearthPalette.EMBER);
            }
        }
        backButton.render(graphics, mouseX, mouseY, partialTick);
        if (tab == HearthGuide.Tab.BUILD) {
            for (int i = 0; i < laneButtons.size(); i++) {
                Button button = laneButtons.get(i);
                button.render(graphics, mouseX, mouseY, partialTick);
                if (i == guide.lane()) {
                    graphics.fill(button.getX(), button.getY() + button.getHeight(),
                            button.getX() + button.getWidth(), button.getY() + button.getHeight() + 2, HearthPalette.EMBER);
                }
            }
        }

        int left = panelX + PADDING;
        int statusW = panelWidth - PADDING * 2;
        String status = HearthGuide.status(false).getString();
        if (this.font.width(status) > statusW) {
            status = HearthGuide.status(true).getString();
        }
        HudKit.draw(graphics, this.font, "hearth.guide.status", status, left, viewTop - 13,
                panelWidth - PADDING * 2, HearthGuide.homeState() != null ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
        graphics.fill(left, viewTop - 3, left + panelWidth - PADDING * 2, viewTop - 2, SkillPalette.PANEL_BORDER);

        clampScroll();
        List<Component> tip = guide.render(graphics, this.font, tab, left + 4, viewTop, listWidth() - 4, viewHeight,
                scroll, mouseX, mouseY);
        int content = guide.contentHeight(tab, this.font, listWidth());
        if (content > viewHeight) {
            int thumb = Math.max(8, viewHeight * viewHeight / content);
            int thumbTop = viewTop + (int) ((viewHeight - thumb) * (scroll / (float) (content - viewHeight)));
            graphics.fill(panelX + panelWidth - 4, thumbTop, panelX + panelWidth - 2, thumbTop + thumb,
                    SkillPalette.TEXT_DIM);
        }
        if (!tip.isEmpty()) {
            HudKit.tooltip(graphics, this.font, tip, mouseX, mouseY, Math.min(300, this.width - 16));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Patchouli's Visualize eyes are drawn inside the list, not widgets: the list hit-tests them.
        if (button == 0 && guide.clickEye(mouseX, mouseY, viewTop, viewHeight)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int before = scroll;
        scroll -= (int) Math.signum(scrollY) * SCROLL_STEP;
        clampScroll();
        return scroll != before || super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
