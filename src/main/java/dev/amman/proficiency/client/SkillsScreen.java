package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SurvivalStreak;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The skills panel. Two columns of rows, each row a name, a level and a bar. */
public final class SkillsScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 13;
    private static final int HEADER_HEIGHT = 14;
    private static final int COLUMN_GAP = 12;
    private static final int BAR_HEIGHT = 3;
    /** Survival streak block under the columns: title line, two bars, caption line. */
    private static final int STREAK_HEIGHT = 34;
    private static final int STREAK_BAR_HEIGHT = 4;
    private static final int STREAK_GAP = 8;
    private static final int CAP_MARK = 0xFFF2D98A;

    /** Left column holds these categories, right column the rest. Keeps the two sides even. */
    private static final List<SkillCategory> LEFT =
            List.of(SkillCategory.COMBAT, SkillCategory.CONSTRUCTION, SkillCategory.SOCIAL,
                    SkillCategory.SURVIVAL);
    private static final List<SkillCategory> RIGHT =
            List.of(SkillCategory.GATHERING, SkillCategory.MOVEMENT,
                    SkillCategory.CRAFTING, SkillCategory.MASTERY, SkillCategory.EXPEDITION);

    private final Map<SkillCategory, List<Skill>> grouped = new EnumMap<>(SkillCategory.class);
    private final List<Row> rows = new ArrayList<>();

    /** Opens the Discovery journal. Drawn by hand after the panel, see render. */
    private net.minecraft.client.gui.components.Button journalButton;

    private int panelX;
    private int panelY;
    private int panelHeight;
    /** Height of the scrolling body (skill columns) actually shown, and how far it is scrolled. */
    private int viewportHeight;
    private int bodyHeight;
    private int scroll;

    private record Row(Skill skill, int x, int y, int width) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 1;
        }
    }

    public SkillsScreen() {
        super(Component.translatable("proficiency.screen.title"));
        for (Skill skill : Skill.VALUES) {
            grouped.computeIfAbsent(skill.category(), c -> new ArrayList<>()).add(skill);
        }
    }

    @Override
    protected void init() {
        int leftRows = countRows(LEFT);
        int rightRows = countRows(RIGHT);
        bodyHeight = Math.max(leftRows, rightRows);
        // At a large GUI scale the whole list no longer fits the window. The streak block stays
        // pinned under the list and the skill columns scroll (mouse wheel) inside what is left.
        int fixed = PADDING + HEADER_HEIGHT + STREAK_HEIGHT + PADDING;
        viewportHeight = Math.max(ROW_HEIGHT * 3, Math.min(bodyHeight, this.height - 8 - fixed));
        scroll = Math.max(0, Math.min(scroll, bodyHeight - viewportHeight));
        panelHeight = fixed + viewportHeight;

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = Math.max(4, (this.height - panelHeight) / 2);

        int columnWidth = (PANEL_WIDTH - PADDING * 2 - COLUMN_GAP) / 2;
        // Event handling only (addWidget): the panel is filled after super.render, which would
        // paint over a renderable widget, so the button is drawn at the end of render.
        journalButton = addWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("proficiency.journal.button"),
                        button -> this.minecraft.setScreen(new DiscoveryJournalScreen(this)))
                .bounds(panelX + PANEL_WIDTH - PADDING - 60, panelY + PADDING - 3, 60, 14).build());
        rows.clear();
        layoutColumn(LEFT, panelX + PADDING, columnWidth);
        layoutColumn(RIGHT, panelX + PADDING + columnWidth + COLUMN_GAP, columnWidth);
    }

    private int countRows(List<SkillCategory> categories) {
        int height = 0;
        for (SkillCategory category : categories) {
            height += HEADER_HEIGHT + grouped.get(category).size() * ROW_HEIGHT;
        }
        return height;
    }

    private void layoutColumn(List<SkillCategory> categories, int x, int width) {
        int y = panelY + PADDING + HEADER_HEIGHT;
        for (SkillCategory category : categories) {
            y += HEADER_HEIGHT;
            for (Skill skill : grouped.get(category)) {
                rows.add(new Row(skill, x, y, width));
                y += ROW_HEIGHT;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21's Screen.render draws the background itself; 1.20.1's does not.
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, SkillPalette.PANEL);
        drawBorder(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, SkillPalette.PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title,
                panelX + PANEL_WIDTH / 2, panelY + PADDING, SkillPalette.TEXT);

        PlayerSkills skills = playerSkills();
        int bodyTop = panelY + PADDING + HEADER_HEIGHT;
        boolean inBody = mouseY >= bodyTop && mouseY < bodyTop + viewportHeight;
        graphics.enableScissor(panelX, bodyTop, panelX + PANEL_WIDTH, bodyTop + viewportHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        drawHeaders(graphics, LEFT, panelX + PADDING);
        drawHeaders(graphics, RIGHT,
                panelX + PADDING + (PANEL_WIDTH - PADDING * 2 - COLUMN_GAP) / 2 + COLUMN_GAP);

        Row hovered = null;
        for (Row row : rows) {
            drawRow(graphics, row, skills);
            if (inBody && row.contains(mouseX, mouseY + scroll)) {
                hovered = row;
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        if (bodyHeight > viewportHeight) {
            // A slim thumb on the panel's right edge, only when there is something to scroll to.
            int thumb = Math.max(8, viewportHeight * viewportHeight / bodyHeight);
            int travel = viewportHeight - thumb;
            int thumbTop = bodyTop + (int) (travel * (scroll / (float) (bodyHeight - viewportHeight)));
            graphics.fill(panelX + PANEL_WIDTH - 3, thumbTop, panelX + PANEL_WIDTH - 1, thumbTop + thumb,
                    SkillPalette.TEXT_DIM);
        }
        drawStreak(graphics, skills);
        journalButton.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null && skills != null) {
            graphics.renderComponentTooltip(this.font, tooltip(hovered.skill(), skills), mouseX, mouseY);
        }
    }

    private void drawHeaders(GuiGraphics graphics, List<SkillCategory> categories, int x) {
        int y = panelY + PADDING + HEADER_HEIGHT;
        for (SkillCategory category : categories) {
            graphics.drawString(this.font,
                    Component.translatable(category.translationKey()),
                    x, y + 3, SkillPalette.accent(category), false);
            y += HEADER_HEIGHT + grouped.get(category).size() * ROW_HEIGHT;
        }
    }

    /**
     * Whether any node in this tree would take a rank right now, materials included. Decided
     * client-side from data the client already has, so it costs no packet; the server re-checks.
     */
    private boolean readyToBuy(Skill skill, PlayerSkills skills) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        for (Talent talent : Talents.of(skill)) {
            if (skills.check(talent) != TalentService.Outcome.OK) {
                continue;
            }
            boolean fills = skills.rank(talent) + 1 == talent.maxRank();
            if (!fills || skills.hasPaid(talent)) {
                return true;
            }
            boolean stocked = true;
            for (var requirement : TalentService.payableMaterials(talent)) {
                if (requirement.countIn(this.minecraft.player.getInventory()) < requirement.count()) {
                    stocked = false;
                    break;
                }
            }
            if (stocked) {
                return true;
            }
        }
        return false;
    }

    private void drawRow(GuiGraphics graphics, Row row, PlayerSkills skills) {
        Skill skill = row.skill();
        int level = skills == null ? 0 : skills.level(skill);
        float progress = skills == null ? 0f : skills.progress(skill);
        boolean maxed = level >= SkillMath.MAX_LEVEL;
        int accent = maxed ? SkillPalette.MAXED : SkillPalette.accent(skill.category());

        if (skills != null && readyToBuy(skill, skills)) {
            // A quiet marker rather than a popup: something here is affordable.
            graphics.fill(row.x() - 4, row.y() - 1, row.x() - 2, row.y() + 8, accent);
        }

        graphics.drawString(this.font, Component.translatable(skill.translationKey()),
                row.x(), row.y(), level > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);

        String levelText = String.valueOf(level);
        int levelWidth = this.font.width(levelText);
        graphics.drawString(this.font, levelText,
                row.x() + row.width() - levelWidth, row.y(),
                level > 0 ? accent : SkillPalette.TEXT_DIM, false);

        // Every row's bar spans the row's own width at one fixed offset. It used to start after
        // the name and stop before the level digits, so no two bars began or ended at the same x.
        int barTop = row.y() + 9;
        int barLeft = row.x();
        int barWidth = row.width();
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + BAR_HEIGHT, SkillPalette.TRACK);
        int filled = Math.round(barWidth * (maxed ? 1.0f : progress));
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + Math.min(barWidth, filled), barTop + BAR_HEIGHT, accent);
        }
    }

    /**
     * The survival streak: an XP bonus that builds while you stay alive and is wiped by a death.
     * Left bar, progress to the next whole percent. Right bar, the bonus from 0 to the cap, with
     * the cap marked at its far end.
     */
    private void drawStreak(GuiGraphics graphics, PlayerSkills skills) {
        boolean off = !SurvivalStreak.enabled();
        boolean capped = skills != null && SurvivalStreak.atCap(skills);
        int stacks = skills == null ? 0 : SurvivalStreak.stacks(skills);
        int percent = SurvivalStreak.percent(stacks);
        int cap = SurvivalStreak.capPercent();
        float step = skills == null ? 0f : SurvivalStreak.stepProgress(skills);
        float fill = skills == null ? 0f : SurvivalStreak.capFill(skills);
        int accent = capped ? SkillPalette.MAXED : SkillPalette.TEXT;

        int left = panelX + PADDING;
        int width = PANEL_WIDTH - PADDING * 2;
        int top = panelY + panelHeight - PADDING - STREAK_HEIGHT + 2;

        graphics.fill(left, top - 3, left + width, top - 2, SkillPalette.PANEL_BORDER);
        Component title = Component.translatable("proficiency.streak.title", percent);
        graphics.drawString(this.font, title, left, top, off ? SkillPalette.TEXT_DIM : accent, false);
        Component capLabel = Component.translatable("proficiency.streak.cap", cap);
        graphics.drawString(this.font, capLabel, left + width - this.font.width(capLabel), top,
                SkillPalette.MAXED, false);

        int barWidth = (width - STREAK_GAP) / 2;
        int barTop = top + 12;
        int rightLeft = left + width - barWidth;
        drawStreakBar(graphics, left, barTop, barWidth, step,
                capped ? SkillPalette.MAXED : SkillPalette.accent(SkillCategory.GATHERING));
        drawStreakBar(graphics, rightLeft, barTop, barWidth, fill,
                capped ? SkillPalette.MAXED : SkillPalette.accent(SkillCategory.MOVEMENT));
        // The cap tick sits on the end of the second bar, over the fill, so 50 reads as the maximum.
        graphics.fill(rightLeft + barWidth - 1, barTop - 2, rightLeft + barWidth,
                barTop + STREAK_BAR_HEIGHT + 2, CAP_MARK);

        Component next = capped || off
                ? Component.translatable("proficiency.streak.next_max")
                : Component.translatable("proficiency.streak.next", percent + 1,
                        (int) Math.floor(step * 100));
        graphics.drawString(this.font, next, left, barTop + STREAK_BAR_HEIGHT + 3,
                SkillPalette.TEXT_DIM, false);
        Component now = Component.translatable("proficiency.streak.value", percent, cap);
        graphics.drawString(this.font, now, rightLeft + barWidth - this.font.width(now),
                barTop + STREAK_BAR_HEIGHT + 3, SkillPalette.TEXT_DIM, false);
    }

    private static void drawStreakBar(GuiGraphics graphics, int left, int top, int width,
            float fraction, int color) {
        graphics.fill(left, top, left + width, top + STREAK_BAR_HEIGHT, SkillPalette.TRACK);
        int filled = Math.round(width * fraction);
        if (fraction > 0f && filled < 1) {
            filled = 1;
        }
        if (filled > 0) {
            graphics.fill(left, top, left + Math.min(width, filled), top + STREAK_BAR_HEIGHT, color);
        }
    }

    private List<Component> tooltip(Skill skill, PlayerSkills skills) {
        int level = skills.level(skill);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(skill.translationKey())
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category()))));
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));

        // The real passive, talents and synergies included (it used to show the bare level value).
        int percent = (int) Math.round(skills.bonus(skill) * 100);
        lines.add(Component.translatable("proficiency.tooltip.effect", percent)
                .withStyle(ChatFormatting.DARK_AQUA));

        double procChance = skills.procChance(skill);
        if (procChance > 0) {
            lines.add(Component.translatable("proficiency.tooltip.proc",
                            Component.translatable(skill.procKey()),
                            (int) Math.round(procChance * 100))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable(skill.procDescKey()).withStyle(ChatFormatting.GRAY));
        } else if (dev.amman.proficiency.config.ProficiencyConfig.procsEnabled()
                && level < dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel()) {
            lines.add(Component.translatable("proficiency.tooltip.proc_locked",
                            dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel())
                    .withStyle(ChatFormatting.DARK_PURPLE));
            lines.add(Component.translatable(skill.procDescKey()).withStyle(ChatFormatting.GRAY));
        }

        lines.add(Component.translatable("proficiency.tooltip.tree",
                skills.pointsSpent(skill), Talents.fullTreeCost(skill)).withStyle(ChatFormatting.YELLOW));
        long awake = Synergies.involving(skill).stream()
                .filter(synergy -> Synergies.isActive(skills, synergy)).count();
        if (awake > 0) {
            lines.add(Component.translatable("proficiency.tooltip.synergies", awake)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.translatable("proficiency.tooltip.points",
                skills.pointsAvailable(skill)).withStyle(ChatFormatting.GRAY));

        if (level >= SkillMath.MAX_LEVEL) {
            lines.add(Component.translatable("proficiency.tooltip.maxed")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            int done = (int) Math.floor(skills.xp(skill));
            int need = (int) Math.ceil(SkillMath.xpToNext(level));
            lines.add(Component.translatable("proficiency.tooltip.progress", level, level + 1, done, need)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private PlayerSkills playerSkills() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        return player == null ? null : ProficiencyAttachments.of(player);
    }

    /** Clicking a skill opens its tree. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.minecraft != null) {
            int bodyTop = panelY + PADDING + HEADER_HEIGHT;
            for (Row row : rows) {
                if (mouseY >= bodyTop && mouseY < bodyTop + viewportHeight
                        && row.contains(mouseX, mouseY + scroll)) {
                    this.minecraft.setScreen(new TalentTreeScreen(row.skill(), this));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int max = Math.max(0, bodyHeight - viewportHeight);
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
