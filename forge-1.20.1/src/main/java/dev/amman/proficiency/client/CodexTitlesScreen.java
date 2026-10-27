package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Codex title picker, opened from the skills panel. One row per skill with its four milestone
 * cells (10, 25, 50, 100 kinds): an earned cell picks that title for the tab list, the picked one
 * is ringed in gold, a locked one is dim and its tooltip says what earns it. Cosmetic only.
 */
public final class CodexTitlesScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_MAX_HEIGHT = 320;
    private static final int PADDING = 10;
    private static final int ROW = 15;
    private static final int CELL_W = 28;
    private static final int CELL_GAP = 3;
    private static final int BUTTON_H = 16;
    private static final int GOLD = 0xFFF2D98A;

    /** One list row: a category header (skill null) or a skill. */
    private record Entry(dev.amman.proficiency.skill.SkillCategory category, @Nullable Skill skill) {
    }

    /** The rows, grouped under a header per category so the colour is said once, not 38 times. */
    private static final List<Entry> ENTRIES = buildEntries();

    private static List<Entry> buildEntries() {
        java.util.Map<dev.amman.proficiency.skill.SkillCategory, List<Skill>> by = new java.util.LinkedHashMap<>();
        for (Skill skill : Skill.VALUES) {
            by.computeIfAbsent(skill.category(), c -> new ArrayList<>()).add(skill);
        }
        List<Entry> list = new ArrayList<>();
        by.forEach((category, skills) -> {
            list.add(new Entry(category, null));
            for (Skill skill : skills) {
                list.add(new Entry(category, skill));
            }
        });
        return List.copyOf(list);
    }

    @Nullable
    private final Screen parent;
    private final TextFit.Hovers hovers = new TextFit.Hovers();
    private Button backButton;
    private Button clearButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int viewTop;
    private int viewHeight;
    private int scroll;
    private final ClickFx click = new ClickFx();

    public CodexTitlesScreen(@Nullable Screen parent) {
        super(Component.translatable("proficiency.codex.picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, this.width - 8);
        panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 8);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        int backTop = panelY + panelHeight - PADDING - BUTTON_H;
        Component back = Component.translatable("proficiency.journal.back");
        Component clear = Component.translatable("proficiency.codex.picker.clear");
        int backW = Math.max(70, this.font.width(back) + 16);
        int clearW = Math.max(90, this.font.width(clear) + 16);
        int gap = 6;
        int left = panelX + (panelWidth - backW - clearW - gap) / 2;
        clearButton = addWidget(Button.builder(clear, b -> ClientCodex.pick(""))
                .bounds(left, backTop, clearW, BUTTON_H).build());
        backButton = addWidget(Button.builder(back, b -> onClose())
                .bounds(left + clearW + gap, backTop, backW, BUTTON_H).build());
        viewTop = panelY + PADDING + 34;
        viewHeight = Math.max(ROW * 3, (backTop - 6 - viewTop) / ROW * ROW);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() {
        return Math.max(0, ENTRIES.size() * ROW - viewHeight);
    }

    private int cellLeft(int index) {
        int total = Codex.MILESTONES.length * CELL_W + (Codex.MILESTONES.length - 1) * CELL_GAP;
        return panelX + panelWidth - PADDING - 6 - total + index * (CELL_W + CELL_GAP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SkillPalette.PANEL);
        border(graphics, panelX, panelY, panelWidth, panelHeight, SkillPalette.PANEL_BORDER);
        hovers.clear();
        String title = TextFit.clip(this.font, this.title.getString(), panelWidth - PADDING * 2);
        graphics.drawString(this.font, title, panelX + (panelWidth - this.font.width(title)) / 2,
                panelY + PADDING, SkillPalette.TEXT, false);

        // What is showing now, under the title.
        Codex.Title picked = Codex.parse(ClientCodex.picked());
        Component showing = picked == null ? Component.translatable("proficiency.command.title_none")
                : Component.translatable("proficiency.command.title_current",
                        dev.amman.proficiency.skill.CodexService.titleName(picked));
        graphics.drawString(this.font, TextFit.clip(this.font, showing.getString(), panelWidth - PADDING * 2),
                panelX + PADDING, panelY + PADDING + 13, picked == null ? SkillPalette.TEXT_DIM : GOLD, false);
        graphics.drawString(this.font, TextFit.clip(this.font,
                        Component.translatable("proficiency.codex.picker.hint").getString(), panelWidth - PADDING * 2),
                panelX + PADDING, panelY + PADDING + 23, SkillPalette.TEXT_DIM, false);

        graphics.fill(panelX + PADDING, viewTop - 3, panelX + panelWidth - PADDING, viewTop - 2,
                SkillPalette.PANEL_BORDER);
        graphics.enableScissor(panelX, viewTop, panelX + panelWidth, viewTop + viewHeight);
        List<Component> hover = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            int y = viewTop + i * ROW - scroll;
            if (y + ROW <= viewTop || y >= viewTop + viewHeight) {
                continue;
            }
            Entry entry = ENTRIES.get(i);
            int accent = SkillPalette.accent(entry.category());
            if (entry.skill() == null) {
                String group = Component.translatable(entry.category().translationKey()).getString();
                int ruleEnd = Math.min(cellLeft(Codex.MILESTONES.length - 1) + CELL_W, panelX + panelWidth - 8);
                String shownGroup = TextFit.clip(this.font, group, Math.max(8, ruleEnd - (panelX + PADDING) - 8));
                graphics.drawString(this.font, shownGroup, panelX + PADDING, y + 4, accent, false);
                int lineX = panelX + PADDING + this.font.width(shownGroup) + 5;
                if (lineX < ruleEnd) {
                    graphics.fill(lineX, y + 8, ruleEnd, y + 9, (accent & 0x00FFFFFF) | 0x55000000);
                }
                continue;
            }
            Skill skill = entry.skill();
            int found = ClientCodex.kinds(skill);
            int countWidth = this.font.width("100") + 6;
            int nameRoom = cellLeft(0) - 6 - countWidth - (panelX + PADDING + 6);
            String name = Component.translatable(skill.translationKey()).getString();
            String shown = TextFit.clip(this.font, name, nameRoom);
            graphics.drawString(this.font, shown, panelX + PADDING + 6, y + 3,
                    found > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
            if (!shown.equals(name)) {
                TextFit.note("codex.picker.name");
                hovers.add(panelX + PADDING + 6, y, nameRoom, ROW, Component.literal(name));
            }
            String count = String.valueOf(found);
            graphics.drawString(this.font, count, cellLeft(0) - 6 - this.font.width(count), y + 3,
                    SkillPalette.TEXT_DIM, false);
            for (int c = 0; c < Codex.MILESTONES.length; c++) {
                int milestone = Codex.MILESTONES[c];
                Codex.Title cell = new Codex.Title(skill, milestone);
                boolean earned = found >= milestone;
                boolean selected = cell.id().equals(ClientCodex.picked());
                int x = cellLeft(c);
                boolean over = mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + ROW - 1
                        && mouseY >= viewTop && mouseY < viewTop + viewHeight;
                int fill = earned ? (over ? 0xFF38424F : 0xFF2A323C) : 0xFF1A1E24;
                int cellKey = skill.ordinal() * 16 + c;
                long clock = System.currentTimeMillis();
                click.begin(graphics, cellKey, x, y, CELL_W, ROW - 2, clock);
                graphics.fill(x, y, x + CELL_W, y + ROW - 2, fill);
                border(graphics, x, y, CELL_W, ROW - 2, selected ? GOLD : earned ? accent : SkillPalette.PANEL_BORDER);
                String label = String.valueOf(milestone);
                graphics.drawString(this.font, label, x + (CELL_W - this.font.width(label)) / 2, y + 3,
                        earned ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
                click.end(graphics, cellKey, x, y, CELL_W, ROW - 2, clock);
                if (over) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(dev.amman.proficiency.skill.CodexService.titleName(cell).copy()
                            .withStyle(style -> style.withColor(earned ? accent : SkillPalette.TEXT_DIM & 0xFFFFFF)));
                    tip.add(Component.translatable("proficiency.codex.picker.needs", milestone,
                            Component.translatable(skill.translationKey())).withStyle(ChatFormatting.GRAY));
                    tip.add(ClientCodex.line(found).copy().withStyle(ChatFormatting.DARK_GRAY));
                    tip.add(Component.translatable(selected ? "proficiency.codex.picker.picked"
                            : earned ? "proficiency.codex.picker.click" : "proficiency.codex.picker.locked")
                            .withStyle(selected ? ChatFormatting.GOLD : earned ? ChatFormatting.GREEN
                                    : ChatFormatting.DARK_GRAY));
                    hover = tip;
                }
            }
        }
        graphics.disableScissor();
        click.render(graphics, System.currentTimeMillis(), 1f);
        int content = ENTRIES.size() * ROW;
        if (content > viewHeight) {
            int thumb = Math.max(8, viewHeight * viewHeight / content);
            int travel = viewHeight - thumb;
            int thumbTop = viewTop + (int) (travel * (scroll / (float) (content - viewHeight)));
            graphics.fill(panelX + panelWidth - 5, viewTop, panelX + panelWidth - 3, viewTop + viewHeight,
                    SkillPalette.TRACK);
            graphics.fill(panelX + panelWidth - 5, thumbTop, panelX + panelWidth - 3, thumbTop + thumb,
                    SkillPalette.TEXT);
        }
        clearButton.render(graphics, mouseX, mouseY, partialTick);
        backButton.render(graphics, mouseX, mouseY, partialTick);
        if (hover == null && mouseY >= viewTop && mouseY < viewTop + viewHeight) {
            hover = hovers.at(mouseX, mouseY);
        }
        if (hover != null) {
            TextFit.tooltip(graphics, this.font, hover, mouseX, mouseY, Math.min(300, this.width - 16));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= viewTop && mouseY < viewTop + viewHeight && this.minecraft != null) {
            int row = (int) ((mouseY - viewTop + scroll) / ROW);
            if (row >= 0 && row < ENTRIES.size() && ENTRIES.get(row).skill() != null) {
                Skill skill = ENTRIES.get(row).skill();
                for (int c = 0; c < Codex.MILESTONES.length; c++) {
                    int x = cellLeft(c);
                    if (mouseX < x || mouseX >= x + CELL_W) {
                        continue;
                    }
                    int cellY = viewTop + row * ROW - scroll;
                    int cellKey = skill.ordinal() * 16 + c;
                    long now = System.currentTimeMillis();
                    if (ClientCodex.kinds(skill) >= Codex.MILESTONES[c]) {
                        String id = new Codex.Title(skill, Codex.MILESTONES[c]).id();
                        boolean fresh = !id.equals(ClientCodex.picked());
                        ClickFx.clickIfOff();
                        ClientCodex.pick(id);
                        // A new title pops and bursts in gold; picking the one already shown only blips.
                        click.success(cellKey, x, cellY, CELL_W, ROW - 2, GOLD, fresh ? 1 : 0,
                                c / (float) Math.max(1, Codex.MILESTONES.length - 1), now);
                        return true;
                    }
                    click.no(cellKey, x, cellY, CELL_W, ROW - 2, now);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * ROW));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
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

    private static void border(GuiGraphics graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }
}
