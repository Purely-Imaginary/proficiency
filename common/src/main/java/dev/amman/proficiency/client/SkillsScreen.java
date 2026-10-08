package dev.amman.proficiency.client;

import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.perk.Requirements;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
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
    /** Narrowest a skill column gets before the two columns become one. */
    private static final int MIN_COLUMN = 150;
    private static final int STREAK_LINE = 10;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 13;
    /** Rows and headers share one height, so a scrolled list always lands on a whole row. */
    private static final int HEADER_HEIGHT = 13;
    private static final int COLUMN_GAP = 12;
    private static final int BAR_HEIGHT = 3;
    /** Survival streak block under the columns: title line, two bars, caption line. */
    private static final int STREAK_HEIGHT = 34;
    private static final int STREAK_BAR_HEIGHT = 4;
    private static final int STREAK_GAP = 8;
    private static final int CAP_MARK = 0xFFF2D98A;
    private static final int SPARK_PITCH = 2;
    private static final int SPARK_HEIGHT = 7;
    private static final float[] SPARK = new float[SkillActivity.BUCKETS];

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
    private int panelWidth = PANEL_WIDTH;
    private int columnWidth;
    private int columns = 2;
    private int buttonWidth = 60;
    /** The streak block's pairs of texts stacked on two lines when they do not fit side by side. */
    private boolean stackTitle;
    private boolean stackNext;
    private int streakHeight = STREAK_HEIGHT;
    private final TextFit.Hovers hovers = new TextFit.Hovers();
    /** Height of the scrolling body (skill columns) actually shown, and how far it is scrolled. */
    private int viewportHeight;
    private int bodyHeight;
    private int scroll;
    /** Whether each column has room for the skill icons; see columnFitsIcons. */
    private boolean leftColumnIcons;
    private boolean rightColumnIcons;

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
        int levelWidth = this.font.width("100");
        int need = 0;
        boolean icons = SkillIcons.enabled();
        for (Skill skill : Skill.VALUES) {
            need = Math.max(need, this.font.width(Component.translatable(skill.translationKey()))
                    + 6 + levelWidth);
        }
        for (SkillCategory category : SkillCategory.values()) {
            need = Math.max(need, this.font.width(Component.translatable(category.translationKey())));
        }
        // Two columns as wide as the longest name asks (never narrower than the original 340-pixel
        // panel); one scrolling column when the window cannot hold two.
        int iconRoom = icons ? SkillIcons.advance(SkillIcons.SMALL) : 0;
        int column = Math.max(MIN_COLUMN, need + iconRoom);
        int availWidth = this.width - 8;
        int twoColumns = column * 2 + COLUMN_GAP + PADDING * 2;
        columns = twoColumns <= availWidth ? 2 : 1;
        if (columns == 2) {
            panelWidth = Math.min(availWidth, Math.max(PANEL_WIDTH, twoColumns));
            columnWidth = (panelWidth - PADDING * 2 - COLUMN_GAP) / 2;
        } else {
            panelWidth = Math.min(availWidth, Math.max(column + PADDING * 2, 200));
            columnWidth = panelWidth - PADDING * 2 - 4;
        }
        if (columns == 1) {
            TextFit.note("panel.single_column");
        }

        buttonWidth = Math.max(60, this.font.width(Component.translatable("proficiency.journal.button")) + 12);
        measureStreak(panelWidth - PADDING * 2);

        int leftRows = countRows(LEFT);
        int rightRows = countRows(RIGHT);
        bodyHeight = columns == 2 ? Math.max(leftRows, rightRows) : leftRows + rightRows;
        // At a large GUI scale the whole list no longer fits the window. The streak block stays
        // pinned under the list and the skill columns scroll (mouse wheel) inside what is left.
        // The viewport is a whole number of rows, so no row is ever half under the footer.
        int fixed = PADDING + HEADER_HEIGHT + streakHeight + PADDING;
        int room = this.height - 8 - fixed;
        viewportHeight = Math.max(ROW_HEIGHT * 3, Math.min(bodyHeight, room / ROW_HEIGHT * ROW_HEIGHT));
        scroll = Math.max(0, Math.min(scroll, bodyHeight - viewportHeight));
        panelHeight = fixed + viewportHeight;

        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(4, (this.height - panelHeight) / 2);

        // Event handling only (addWidget): the panel is filled after super.render, which would
        // paint over a renderable widget, so the button is drawn at the end of render.
        journalButton = addWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("proficiency.journal.button"),
                        button -> this.minecraft.setScreen(new DiscoveryJournalScreen(this)))
                .bounds(panelX + panelWidth - PADDING - buttonWidth, panelY + PADDING - 3, buttonWidth, 14).build());
        rows.clear();
        if (columns == 2) {
            layoutColumn(LEFT, panelX + PADDING, columnWidth, 0);
            layoutColumn(RIGHT, panelX + PADDING + columnWidth + COLUMN_GAP, columnWidth, 0);
        } else {
            int used = layoutColumn(LEFT, panelX + PADDING, columnWidth, 0);
            layoutColumn(RIGHT, panelX + PADDING, columnWidth, used);
        }
        leftColumnIcons = columnFitsIcons(panelX + PADDING, true);
        rightColumnIcons = columns == 1 ? leftColumnIcons : columnFitsIcons(panelX + PADDING, false);
    }

    /** Decides, with the widest numbers the block can show, which text pairs need two lines. */
    private void measureStreak(int width) {
        int cap = Math.max(50, SurvivalStreak.capPercent());
        int title = this.font.width(Component.translatable("proficiency.streak.title", cap));
        int capLabel = this.font.width(Component.translatable("proficiency.streak.cap", cap));
        int next = Math.max(this.font.width(Component.translatable("proficiency.streak.next", cap, 99)),
                this.font.width(Component.translatable("proficiency.streak.next_max")));
        int value = this.font.width(Component.translatable("proficiency.streak.value", cap, cap));
        stackTitle = title + capLabel + 8 > width;
        stackNext = next + value + 8 > width;
        streakHeight = STREAK_HEIGHT + (stackTitle ? STREAK_LINE : 0) + (stackNext ? STREAK_LINE : 0);
        if (stackTitle) {
            TextFit.note("panel.streak_title");
        }
        if (stackNext) {
            TextFit.note("panel.streak_next");
        }
    }

    /** Screen position to point at for this skill's row (scrolls it into view); the layout demo uses it. */
    int[] rowPoint(Skill skill) {
        int bodyTop = panelY + PADDING + HEADER_HEIGHT;
        for (Row row : rows) {
            if (row.skill() == skill) {
                if (row.y() - scroll < bodyTop || row.y() - scroll > bodyTop + viewportHeight - ROW_HEIGHT) {
                    scroll = Math.max(0, Math.min(bodyHeight - viewportHeight, row.y() - bodyTop - ROW_HEIGHT));
                }
                return new int[] {row.x() + row.width() / 2, row.y() - scroll + 4};
            }
        }
        return new int[] {0, 0};
    }

    private int countRows(List<SkillCategory> categories) {
        int height = 0;
        for (SkillCategory category : categories) {
            height += HEADER_HEIGHT + grouped.get(category).size() * ROW_HEIGHT;
        }
        return height;
    }

    /** Places the rows of these categories from {@code skipped} pixels down; returns the height used. */
    private int layoutColumn(List<SkillCategory> categories, int x, int width, int skipped) {
        int y = panelY + PADDING + HEADER_HEIGHT + skipped;
        for (SkillCategory category : categories) {
            y += HEADER_HEIGHT;
            for (Skill skill : grouped.get(category)) {
                rows.add(new Row(skill, x, y, width));
                y += ROW_HEIGHT;
            }
        }
        return y - (panelY + PADDING + HEADER_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        hovers.clear();
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SkillPalette.PANEL);
        drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, SkillPalette.PANEL_BORDER);

        // The title is centred on the panel unless the Journal button would touch it.
        int titleWidth = this.font.width(this.title);
        int titleLeft = panelX + (panelWidth - titleWidth) / 2;
        int titleLimit = panelX + panelWidth - PADDING - buttonWidth - 6;
        if (titleLeft + titleWidth > titleLimit) {
            titleLeft = Math.max(panelX + PADDING, titleLimit - titleWidth);
        }
        TextFit.draw(graphics, this.font, "panel.title", this.title.getString(), titleLeft, panelY + PADDING,
                titleLimit - titleLeft, SkillPalette.TEXT, false);

        PlayerSkills skills = playerSkills();
        int bodyTop = panelY + PADDING + HEADER_HEIGHT;
        boolean inBody = mouseY >= bodyTop && mouseY < bodyTop + viewportHeight;
        graphics.enableScissor(panelX, bodyTop, panelX + panelWidth, bodyTop + viewportHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        if (columns == 2) {
            drawHeaders(graphics, LEFT, panelX + PADDING, 0);
            drawHeaders(graphics, RIGHT, panelX + PADDING + columnWidth + COLUMN_GAP, 0);
        } else {
            drawHeaders(graphics, LEFT, panelX + PADDING, 0);
            drawHeaders(graphics, RIGHT, panelX + PADDING, countRows(LEFT));
        }

        Row hovered = null;
        // A monotonic clock, so a stepped system clock cannot make a glow last or flip.
        long now = net.minecraft.Util.getMillis();
        boolean activity = ProficiencyClientConfig.screensActivity();
        // Icons are on or off for a whole column, so the names in it stay in one line.
        boolean icons = SkillIcons.enabled();
        int leftX = panelX + PADDING;
        boolean leftIcons = icons && leftColumnIcons;
        boolean rightIcons = icons && rightColumnIcons;
        for (Row row : rows) {
            drawRow(graphics, row, skills, now, activity, row.x() == leftX ? leftIcons : rightIcons);
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
            graphics.fill(panelX + panelWidth - 5, bodyTop, panelX + panelWidth - 3, bodyTop + viewportHeight,
                    SkillPalette.TRACK);
            graphics.fill(panelX + panelWidth - 5, thumbTop, panelX + panelWidth - 3, thumbTop + thumb,
                    SkillPalette.TEXT);
        }
        drawStreak(graphics, skills);
        journalButton.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null && skills != null) {
            TextFit.tooltip(graphics, this.font, tooltip(hovered.skill(), skills), mouseX, mouseY,
                    Math.min(300, this.width - 16));
        } else {
            List<Component> full = hovers.at(mouseX, mouseY);
            if (full != null) {
                TextFit.tooltip(graphics, this.font, full, mouseX, mouseY, Math.min(300, this.width - 16));
            }
        }
    }

    private void drawHeaders(GuiGraphics graphics, List<SkillCategory> categories, int x, int skipped) {
        int y = panelY + PADDING + HEADER_HEIGHT + skipped;
        for (SkillCategory category : categories) {
            Component name = Component.translatable(category.translationKey());
            TextFit.draw(graphics, this.font, "panel.category", name.getString(), x, y + 3, columnWidth,
                    SkillPalette.accent(category), false);
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
            if (skills.check(talent) != TalentOutcome.OK) {
                continue;
            }
            boolean fills = skills.rank(talent) + 1 == talent.maxRank();
            if (!fills || skills.hasPaid(talent)) {
                return true;
            }
            boolean stocked = true;
            for (var requirement : TalentService.payableMaterials(talent)) {
                if (Requirements.countIn(requirement, this.minecraft.player.getInventory()) < requirement.count()) {
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

    /**
     * Whether every row of one column has room for its icon beside the name and a three-digit
     * level. Worked out once per layout, so a frame measures nothing for it.
     */
    private boolean columnFitsIcons(int leftX, boolean left) {
        int levelWidth = this.font.width("100");
        for (Row row : rows) {
            if ((row.x() == leftX) != left) {
                continue;
            }
            int content = this.font.width(Component.translatable(row.skill().translationKey()))
                    + 4 + levelWidth;
            if (SkillIcons.fit(row.width(), content, SkillIcons.SMALL) == 0) {
                return false;
            }
        }
        return true;
    }

    private void drawRow(GuiGraphics graphics, Row row, PlayerSkills skills, long now, boolean activity,
            boolean icons) {
        Skill skill = row.skill();
        int level = skills == null ? 0 : skills.level(skill);
        float progress = skills == null ? 0f : skills.progress(skill);
        boolean maxed = level >= SkillMath.MAX_LEVEL;
        int accent = maxed ? SkillPalette.MAXED : SkillPalette.accent(skill.category());

        if (activity) {
            // Gained XP in the last ten minutes: a faint wash behind the row, fading with age.
            float glow = SkillActivity.glow(skill, now);
            if (glow > 0f) {
                int alpha = 0x08 + (int) (0x1C * glow);
                graphics.fill(row.x() - 2, row.y() - 2, row.x() + row.width() + 2, row.y() + ROW_HEIGHT - 2,
                        (accent & 0x00FFFFFF) | (alpha << 24));
            }
        }

        if (skills != null && readyToBuy(skill, skills)) {
            // A quiet marker rather than a popup: something here is affordable.
            graphics.fill(row.x() - 4, row.y() - 1, row.x() - 2, row.y() + 8, accent);
        }

        Component name = Component.translatable(skill.translationKey());
        String levelText = String.valueOf(level);
        int levelWidth = this.font.width(levelText);
        int nameWidth = this.font.width(name);
        // The column decided whether its rows have room for icons (see columnFitsIcons).
        int icon = icons ? SkillIcons.SMALL : 0;
        if (icon > 0) {
            SkillIcons.draw(graphics, skill, row.x(), SkillIcons.smallTop(row.y()), icon, level > 0 ? 255 : 150);
        }
        int nameX = row.x() + SkillIcons.advance(icon);
        // Mastery stars sit just left of the level, small and gold.
        int starCount = skills == null ? 0 : MasteryStars.shown(skills, skill);
        int starsW = starCount > 0 ? MasteryStars.width(starCount) + 3 : 0;
        // A name too long for its column is cut with an ellipsis; the row's tooltip names it whole.
        String nameText = name.getString();
        String shown = TextFit.clip(this.font, nameText, row.width() - SkillIcons.advance(icon) - levelWidth - starsW - 4);
        if (!shown.equals(nameText)) {
            TextFit.note("panel.row");
            nameWidth = this.font.width(shown);
        }
        graphics.drawString(this.font, shown,
                nameX, row.y(), level > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
        graphics.drawString(this.font, levelText,
                row.x() + row.width() - levelWidth, row.y(),
                level > 0 ? accent : SkillPalette.TEXT_DIM, false);

        if (starCount > 0) {
            MasteryStars.draw(graphics, this.font, row.x() + row.width() - levelWidth - starsW, row.y(),
                    starCount, 255);
        }
        if (activity) {
            sparkline(graphics, row, skill, nameX + nameWidth, levelWidth + starsW, now);
        }

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
     * The skill's XP over the last two hours as 24 two-pixel columns between the name and the
     * level. A row with no room for it (a long name, a narrow panel) gets none rather than an
     * overlap, and a skill with no gains in the window draws nothing at all.
     */
    private void sparkline(GuiGraphics graphics, Row row, Skill skill, int nameRight, int levelWidth,
            long now) {
        // Data first: most rows have none, and they should cost nothing.
        float max = SkillActivity.series(skill, now, SPARK);
        if (max <= 0f) {
            return;
        }
        int width = SkillActivity.BUCKETS * SPARK_PITCH - (SPARK_PITCH - 1);
        int left = row.x() + row.width() - levelWidth - 5 - width;
        if (left < nameRight + 5) {
            return;
        }
        int base = row.y() + 8;
        int colour = SkillPalette.accent(skill.category());
        for (int i = 0; i < SkillActivity.BUCKETS; i++) {
            int x = left + i * SPARK_PITCH;
            float v = SPARK[i];
            if (v <= 0f) {
                graphics.fill(x, base - 1, x + 1, base, SkillPalette.TRACK);
                continue;
            }
            int height = 1 + Math.round(v / max * (SPARK_HEIGHT - 1));
            int tint = i == SkillActivity.BUCKETS - 1 ? colour : (colour & 0x00FFFFFF) | 0xB0000000;
            graphics.fill(x, base - height, x + 1, base, tint);
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
        int width = panelWidth - PADDING * 2;
        int top = panelY + panelHeight - PADDING - streakHeight + 2;

        graphics.fill(left, top - 3, left + width, top - 2, SkillPalette.PANEL_BORDER);
        // Each pair (title and cap, next and value) shares a line when both fit, else the second
        // goes on a line of its own, right-aligned, so neither is cut and neither overlaps.
        Component title = Component.translatable("proficiency.streak.title", percent);
        Component capLabel = Component.translatable("proficiency.streak.cap", cap);
        pair(graphics, title, off ? SkillPalette.TEXT_DIM : accent, capLabel, SkillPalette.MAXED, left, top,
                width, stackTitle);
        if (stackTitle) {
            top += STREAK_LINE;
        }

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
        Component now = Component.translatable("proficiency.streak.value", percent, cap);
        pair(graphics, next, SkillPalette.TEXT_DIM, now, SkillPalette.TEXT_DIM, left,
                barTop + STREAK_BAR_HEIGHT + 3, width, stackNext);
    }

    /** Two texts on one line, the second flush right, or stacked (first, then second below it). */
    private void pair(GuiGraphics graphics, Component first, int firstColour, Component second, int secondColour,
            int left, int y, int width, boolean stacked) {
        int secondWidth = this.font.width(second);
        if (stacked) {
            if (clippedDraw(graphics, first, left, y, width, firstColour)) {
                hovers.add(left, y, width, 9, first);
            }
            int secondRoom = Math.min(width, secondWidth);
            clippedDraw(graphics, second, left + width - secondRoom, y + STREAK_LINE, secondRoom, secondColour);
            return;
        }
        int room = width - secondWidth - 8;
        if (clippedDraw(graphics, first, left, y, room, firstColour)) {
            hovers.add(left, y, room, 9, first);
        }
        graphics.drawString(this.font, second, left + width - secondWidth, y, secondColour, false);
    }

    private boolean clippedDraw(GuiGraphics graphics, Component text, int x, int y, int room, int colour) {
        String full = text.getString();
        String shown = TextFit.clip(this.font, full, room);
        graphics.drawString(this.font, shown, x, y, colour, false);
        boolean cut = !shown.equals(full);
        if (cut) {
            TextFit.note("panel.streak");
        }
        return cut;
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

    /**
     * Short: the name, how far to the next level and the stat that matters (a row with points to
     * spend is marked in the list itself). Detailed (Shift): what the skill does, every stat as base to current,
     * the signature move, the tree and its synergies.
     */
    private List<Component> tooltip(Skill skill, PlayerSkills skills) {
        boolean detailed = TooltipDetail.detailed();
        int level = skills.level(skill);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(skill.translationKey())
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category()))));
        lines.add(progressLine(skill, skills, level));
        lines.addAll(MasteryStars.tooltip(skills, skill));
        lines.add(SkillNumbers.headline(skills, skill));
        int points = skills.pointsAvailable(skill);
        if (!detailed) {
            return TooltipDetail.withHint(lines, true);
        }

        // Detailed, top-down: what it is, the numbers, the signature move, the tree.
        lines.remove(lines.size() - 1);
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));
        lines.addAll(SkillNumbers.statRows(skills, skill));

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
        lines.add(Component.translatable("proficiency.tooltip.points", points).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static Component progressLine(Skill skill, PlayerSkills skills, int level) {
        if (level >= SkillMath.MAX_LEVEL) {
            return Component.translatable("proficiency.tooltip.maxed").withStyle(ChatFormatting.GOLD);
        }
        int done = (int) Math.floor(skills.xp(skill));
        int need = (int) Math.ceil(SkillMath.xpToNext(level));
        return Component.translatable("proficiency.tooltip.progress", level, level + 1, done, need)
                .withStyle(ChatFormatting.GRAY);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, bodyHeight - viewportHeight);
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
