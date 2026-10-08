package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.UnlockPerkPayload;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.XpFactors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One skill's talent tree, drawn as a grid of nodes joined by elbow lines, with the synergies that
 * touch this tree down the right-hand side. Click a node to put a rank in; shift-click to fill it.
 * The server re-checks every click, so everything drawn here is only a forecast.
 */
public final class TalentTreeScreen extends Screen {

    private static final int PADDING = 10;
    private static final int COLUMNS = 5;
    private static final int ROWS = 6;
    private static final int SIDE_GAP = 14;
    private static final int SYNERGY_ROW = 12;
    private static final int LOG_ROW = 10;
    /** Gap between the last synergy and the recent-XP header. */
    private static final int LOG_GAP = 6;
    /** A log line is drawn at full strength this long, then fades to {@link #LOG_FADED_ALPHA}. */
    private static final long LOG_FRESH_MILLIS = 10_000L;
    private static final long LOG_FADE_MILLIS = 50_000L;
    private static final int LOG_FADED_ALPHA = 0x70;


    private final Skill skill;
    @Nullable
    private final Screen parent;
    private final List<Synergy> synergies;

    /** Unlock animation timing; fed the real ranks each frame. See {@link UnlockFx}. */
    private final UnlockFx fx;

    /** Node size, column step and row step, biggest first; the first that fits the window is used. */
    private static final int[][] GEOMETRIES = {{24, 46, 34}, {22, 42, 30}, {20, 38, 26}, {18, 36, 24}};
    /** Tries in order: geometry, longest signature-move text in lines, vertical padding. */
    private static final int[][] TRIES = {{0, 2, 10}, {1, 2, 10}, {2, 2, 10}, {3, 2, 10}, {3, 1, 6}};
    private static final int SIDE_MIN = 120;
    private static final int SIDE_WANTED_MAX = 230;
    private static final int HEADER_LINE = 9;

    private int padV = PADDING;
    private int procLinesMax = 2;
    private int node = GEOMETRIES[0][0];
    private int colStep = GEOMETRIES[0][1];
    private int rowStep = GEOMETRIES[0][2];
    private int gridW;
    private int gridH;
    private int sideW;
    private int panelW;
    private int panelH;
    private int headerH;
    private int footerH;
    /** Whole-screen shrink for a window too small for even the tightest grid; 1 almost always. */
    private float uiScale = 1f;
    /** The window size in this screen's own (scaled) units. */
    private int vw;
    private int vh;
    private int panelX;
    private int panelY;
    /** Where the synergy list ended on the last frame; the recent-XP list starts below it. */
    private int synergiesBottom;

    /** A header line: its text, colour and which block (0 title, 1 summary, 2 signature move, 3 numbers). */
    private record HLine(String text, int colour, int block) {
    }

    private List<HLine> headerLines = List.of();
    private List<String> footerLines = List.of();
    private String layoutKey = "";
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    /** Tooltip chosen during the scaled pass, drawn after it at the real pointer position. */
    private List<Component> pendingTip = List.of();

    public TalentTreeScreen(Skill skill, @Nullable Screen parent) {
        super(Component.translatable("proficiency.tree.title",
                Component.translatable(skill.translationKey())));
        this.skill = skill;
        this.parent = parent;
        this.synergies = Synergies.involving(skill);
        this.fx = new UnlockFx(COLUMNS * ROWS, synergies.size());
    }

    private static int idx(Talent talent) {
        return Math.max(0, Math.min(COLUMNS * ROWS - 1, talent.row() * COLUMNS + talent.col()));
    }

    @Override
    protected void init() {
        layoutKey = "";
        PlayerSkills skills = playerSkills();
        if (skills != null) {
            layout(skills);
        }
    }

    /** The header's line: the signature move and its one-line description. The hover has the rest. */
    private Component procLine() {
        return Component.translatable("proficiency.tree.proc",
                Component.translatable(skill.procKey()), TooltipDetail.shortOr(skill.procShortKey(), skill.procDescKey()));
    }

    /** The hover of that line: short, or with the whole description and the numbers (Shift). */
    private List<Component> procTooltip(PlayerSkills skills) {
        List<Component> lines = new ArrayList<>();
        Component name = Component.translatable(skill.procKey()).withStyle(style -> style.withColor(accent()));
        double chance = skills.procChance(skill);
        if (!TooltipDetail.detailed()) {
            lines.add(name);
            lines.add(TooltipDetail.shortOr(skill.procShortKey(), skill.procDescKey()).copy().withStyle(ChatFormatting.GRAY));
            lines.add(chance > 0
                    ? Component.translatable("proficiency.stats.proc", SkillNumbers.pct(chance),
                            SkillNumbers.times(skills.procPower(skill))).withStyle(ChatFormatting.LIGHT_PURPLE)
                    : Component.translatable("proficiency.tooltip.proc_locked",
                            dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel())
                            .withStyle(ChatFormatting.DARK_PURPLE));
            return TooltipDetail.withHint(lines, true);
        }
        lines.add(name);
        lines.add(Component.translatable(skill.procDescKey()).withStyle(ChatFormatting.GRAY));
        lines.add(chance > 0
                ? Component.translatable("proficiency.stats.proc", SkillNumbers.pct(chance),
                        SkillNumbers.times(skills.procPower(skill))).withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable("proficiency.tooltip.proc_locked",
                        dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel())
                        .withStyle(ChatFormatting.DARK_PURPLE));
        return lines;
    }

    private Component summaryLine(PlayerSkills skills) {
        return Component.translatable("proficiency.tree.summary",
                skills.level(skill), skills.pointsAvailable(skill),
                skills.pointsSpent(skill), Talents.fullTreeCost(skill));
    }

    private Component disciplineLine(PlayerSkills skills) {
        double discipline = Synergies.discipline(skills, skill.category());
        return Component.translatable("proficiency.tree.discipline",
                Component.translatable(skill.category().translationKey()), (int) Math.round(discipline * 100));
    }

    private String synergyText(Synergy synergy) {
        return "✦ " + synergy.displayName().getString();
    }

    /**
     * Works out the geometry and the text lines for this window: the roomiest grid that fits, the
     * side column as wide as its longest synergy asks (up to a limit), a header that spans the whole
     * panel and wraps whole pieces to a second line, and a footer that does the same. Redone when
     * the window or any of the text changes (the numbers move as XP comes in).
     */
    private void layout(PlayerSkills skills) {
        String title = this.title.getString();
        String summary = summaryLine(skills).getString();
        String proc = procLine().getString();
        String numbers = SkillNumbers.line(skills, skill).getString();
        String footer = Component.translatable("proficiency.tree.footer", skill.id()).getString();
        String key = title + '\n' + summary + '\n' + proc + '\n' + numbers + '\n' + footer;
        if (key.equals(layoutKey) && layoutWidth == this.width && layoutHeight == this.height) {
            return;
        }
        layoutKey = key;
        layoutWidth = this.width;
        layoutHeight = this.height;

        int sideWanted = 150;
        for (Synergy synergy : synergies) {
            sideWanted = Math.max(sideWanted, this.font.width("0/9 " + synergy.displayName().getString()) + 4);
        }
        sideWanted = Math.max(sideWanted, this.font.width(Component.translatable("proficiency.tree.xp_log")) + 4);
        sideWanted = Math.min(SIDE_WANTED_MAX, sideWanted);

        int availW = this.width - 8;
        int availH = this.height - 8;
        for (int[] attempt : TRIES) {
            node = GEOMETRIES[attempt[0]][0];
            colStep = GEOMETRIES[attempt[0]][1];
            rowStep = GEOMETRIES[attempt[0]][2];
            procLinesMax = attempt[1];
            padV = attempt[2];
            gridW = (COLUMNS - 1) * colStep + node;
            gridH = (ROWS - 1) * rowStep + node;
            sideW = sideWanted;
            panelW = PADDING * 2 + gridW + SIDE_GAP + sideW;
            if (panelW > availW) {
                sideW = Math.max(SIDE_MIN, sideW - (panelW - availW));
                panelW = PADDING * 2 + gridW + SIDE_GAP + sideW;
            }
            buildText(title, summary, proc, numbers, footer, panelW - PADDING * 2);
            panelH = padV + headerH + gridH + 4 + footerH + padV;
            if (panelW <= availW && panelH <= availH) {
                break;
            }
        }
        uiScale = Math.min(1f, Math.min(availW / (float) panelW, availH / (float) panelH));
        vw = Math.round(this.width / uiScale);
        vh = Math.round(this.height / uiScale);
        panelX = (vw - panelW) / 2;
        panelY = Math.max(2, (vh - panelH) / 2);
        if (uiScale < 1f) {
            TextFit.note("tree.scaled");
        }
    }

    private void buildText(String title, String summary, String proc, String numbers, String footer, int width) {
        List<HLine> lines = new java.util.ArrayList<>();
        int accent = accent();
        lines.add(new HLine(TextFit.clip(this.font, title, width - SkillIcons.advance(SkillIcons.LARGE)), accent, 0));
        boolean[] cut = new boolean[1];
        for (String line : TextFit.pack(this.font, summary, width, 2, cut, TextFit.DOT_RE, TextFit.DOT)) {
            lines.add(new HLine(line, SkillPalette.TEXT_DIM, 1));
        }
        for (String line : TextFit.wrapLimited(this.font, proc, width, procLinesMax, cut)) {
            lines.add(new HLine(line, accent, 2));
        }
        for (String line : TextFit.pack(this.font, numbers, width, 2, cut, TextFit.DOT_RE, TextFit.DOT)) {
            lines.add(new HLine(line, SkillPalette.TEXT, 3));
        }
        if (cut[0]) {
            TextFit.note("tree.header");
        }
        headerLines = lines;
        headerH = 10 + HEADER_LINE * (lines.size() - 1) + 3;
        boolean[] footerCut = new boolean[1];
        footerLines = TextFit.pack(this.font, footer, width, 3, footerCut, "\\s{2,}", "   ");
        if (footerCut[0]) {
            TextFit.note("tree.footer");
        }
        footerH = HEADER_LINE * footerLines.size() + 1;
    }

    /** Screen position of a node's centre (real GUI pixels); the layout demo hovers it. */
    int[] nodePoint(int row, int col) {
        PlayerSkills skills = playerSkills();
        if (skills != null) {
            layout(skills);
        }
        return new int[] {Math.round((panelX + PADDING + col * colStep + node / 2) * uiScale),
                Math.round((panelY + padV + headerH + row * rowStep + node / 2) * uiScale)};
    }

    /** Screen position of the i-th synergy line; the layout demo hovers it. */
    int[] synergyPoint(int index) {
        return new int[] {Math.round((panelX + PADDING + gridW + SIDE_GAP + 20) * uiScale),
                Math.round((synergyTop() + discLines * SYNERGY_ROW + 4 + index * SYNERGY_ROW + 4) * uiScale)};
    }

    /** A point on the header's numbers line (real GUI pixels); the layout demo hovers it. */
    int[] statsPoint(boolean proc) {
        PlayerSkills skills = playerSkills();
        if (skills != null) {
            layout(skills);
        }
        int y = panelY + padV;
        for (HLine line : headerLines) {
            if (line.block() == (proc ? 2 : 3)) {
                return new int[] {Math.round((panelX + PADDING + 30) * uiScale), Math.round((y + 3) * uiScale)};
            }
            y += line.block() == 0 ? 10 : HEADER_LINE;
        }
        return new int[] {0, 0};
    }

    private int nodeX(Talent talent) {
        return panelX + PADDING + talent.col() * colStep;
    }

    private int nodeY(Talent talent) {
        return panelY + padV + headerH + talent.row() * rowStep;
    }

    private int accent() {
        return SkillPalette.accent(skill.category());
    }

    /** Top of the side column: level with the first row of nodes. */
    private int synergyTop() {
        return panelY + padV + headerH;
    }

    /** How many lines the discipline line took on the last frame (it wraps). */
    private int discLines = 1;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        PlayerSkills skills = playerSkills();
        if (skills == null) {
            return;
        }
        layout(skills);
        pendingTip = List.of();
        // Everything below is laid out in this screen's own units, drawn shrunk when the window is
        // too small, and the pointer is converted to match. The tooltip is drawn afterwards at the
        // real pointer position, at full size.
        int mx = Math.round(mouseX / uiScale);
        int my = Math.round(mouseY / uiScale);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1f);
        renderPanel(graphics, skills, mx, my);
        graphics.pose().popPose();
        if (!pendingTip.isEmpty()) {
            TextFit.tooltip(graphics, this.font, pendingTip, mouseX, mouseY, Math.min(300, this.width - 16));
        }
    }

    private void renderPanel(GuiGraphics graphics, PlayerSkills skills, int mouseX, int mouseY) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, SkillPalette.PANEL);
        border(graphics, panelX, panelY, panelW, panelH, SkillPalette.PANEL_BORDER);

        int innerW = panelW - PADDING * 2;
        // The icon: full size beside the title and the first summary line when both still fit
        // whole, else small beside the title only, else none.
        int icon = 0;
        if (SkillIcons.enabled()) {
            int titleW = this.font.width(headerLines.get(0).text());
            int both = titleW;
            for (HLine line : headerLines) {
                if (line.block() == 1) {
                    both = Math.max(both, this.font.width(line.text()));
                    break;
                }
            }
            icon = SkillIcons.fit(innerW, both, SkillIcons.LARGE) == SkillIcons.LARGE
                    ? SkillIcons.LARGE : SkillIcons.fit(innerW, titleW, SkillIcons.SMALL);
        }
        int titleY = panelY + padV;
        if (icon == SkillIcons.LARGE) {
            SkillIcons.draw(graphics, skill, panelX + PADDING, titleY - 1, icon);
        } else if (icon > 0) {
            SkillIcons.draw(graphics, skill, panelX + PADDING, SkillIcons.smallTop(titleY), icon);
        }
        int y = titleY;
        boolean firstSummary = true;
        int procTop = -1;
        int procBottom = -1;
        int statsTop = -1;
        int statsBottom = -1;
        for (HLine line : headerLines) {
            int x = panelX + PADDING;
            if (line.block() == 0 || (line.block() == 1 && firstSummary && icon == SkillIcons.LARGE)) {
                x += SkillIcons.advance(icon);
            }
            if (line.block() == 1) {
                firstSummary = false;
            }
            if (line.block() == 2) {
                procTop = procTop < 0 ? y : procTop;
                procBottom = y + HEADER_LINE;
            } else if (line.block() == 3) {
                statsTop = statsTop < 0 ? y : statsTop;
                statsBottom = y + HEADER_LINE;
            }
            graphics.drawString(this.font, line.text(), x, y, line.colour(), false);
            y += line.block() == 0 ? 10 : HEADER_LINE;
        }
        boolean procHovered = procTop >= 0 && inside(mouseX, mouseY, panelX + PADDING, procTop, innerW,
                procBottom - procTop);
        boolean statsHovered = statsTop >= 0 && inside(mouseX, mouseY, panelX + PADDING, statsTop, innerW,
                statsBottom - statsTop);

        List<Talent> tree = Talents.of(skill);
        long now = System.currentTimeMillis();
        boolean fxOn = ProficiencyClientConfig.screensUnlockFx();
        for (Talent talent : tree) {
            if (fxOn) {
                fx.observe(idx(talent), skills.rank(talent), talent.maxRank(), now);
            } else {
                fx.snap(idx(talent), skills.rank(talent));
            }
        }
        for (Talent talent : tree) {
            for (String parentId : talent.parents()) {
                Talent from = Talents.get(skill, parentId);
                if (from == null) {
                    continue;
                }
                if (fxOn && fx.linesPending(idx(from), now)) {
                    // The line lights as the sweep ends and the light runs towards this node.
                    connector(graphics, from, talent, SkillPalette.TRACK, accent(),
                            fx.travel(idx(from), now));
                } else {
                    connector(graphics, from, talent,
                            skills.isFull(from) ? accent() : SkillPalette.TRACK, 0, -1f);
                }
            }
        }

        Talent hovered = null;
        for (Talent talent : tree) {
            drawNode(graphics, talent, skills, now, fxOn);
            if (inside(mouseX, mouseY, nodeX(talent), nodeY(talent), node, node)) {
                hovered = talent;
            }
        }

        Synergy hoveredSynergy = drawSynergies(graphics, skills, mouseX, mouseY);
        ClientXpLog.Line hoveredLog = drawXpLog(graphics, synergiesBottom + LOG_GAP, mouseX, mouseY);

        // The hint line spans the panel under the grid and the side column, wrapped by whole
        // pieces, so it never runs under another element.
        int footY = panelY + panelH - padV - footerH + 1;
        for (String line : footerLines) {
            graphics.drawString(this.font, line, panelX + PADDING, footY, SkillPalette.TEXT_DIM, false);
            footY += HEADER_LINE;
        }

        if (statsHovered && hovered == null) {
            pendingTip = TooltipDetail.withHint(new ArrayList<>(SkillNumbers.tooltip(skills, skill, TooltipDetail.detailed())), true);
        } else if (procHovered && hovered == null) {
            pendingTip = procTooltip(skills);
        } else if (hovered != null) {
            pendingTip = nodeTooltip(hovered, skills, fxOn ? fx.shownRank(idx(hovered), now) : skills.rank(hovered));
        } else if (hoveredSynergy != null) {
            pendingTip = synergyTooltip(hoveredSynergy, skills);
        } else if (hoveredLog != null) {
            pendingTip = logTooltip(hoveredLog);
        }
    }

    /** Elbow from the bottom of one node to the top of the next: down, across, down. */
    private void connector(GuiGraphics graphics, Talent from, Talent to, int colour, int lit, float travel) {
        int x1 = nodeX(from) + node / 2;
        int y1 = nodeY(from) + node;
        int x2 = nodeX(to) + node / 2;
        int y2 = nodeY(to);
        int mid = y2 - (rowStep - node) / 2;
        graphics.fill(x1, y1, x1 + 1, mid + 1, colour);
        graphics.fill(Math.min(x1, x2), mid, Math.max(x1, x2) + 1, mid + 1, colour);
        graphics.fill(x2, mid, x2 + 1, y2, colour);
        if (travel < 0f) {
            return;
        }
        // The lit part: the same three legs, filled in order up to travel * total length, with a
        // bright head so the eye can follow it.
        int down1 = mid + 1 - y1;
        int across = Math.abs(x2 - x1);
        int down2 = y2 - mid;
        int left = Math.round((float) XpGainDots.ease(travel) * (down1 + across + down2));
        int len = Math.min(left, down1);
        graphics.fill(x1, y1, x1 + 1, y1 + len, lit);
        int hx = x1;
        int hy = y1 + len;
        left -= len;
        if (left > 0) {
            len = Math.min(left, across);
            int dir = x2 >= x1 ? 1 : -1;
            graphics.fill(Math.min(x1, x1 + dir * len), mid, Math.max(x1, x1 + dir * len) + 1, mid + 1, lit);
            hx = x1 + dir * len;
            hy = mid;
            left -= len;
            if (left > 0) {
                len = Math.min(left, down2);
                graphics.fill(x2, mid, x2 + 1, mid + len, lit);
                hx = x2;
                hy = mid + len;
            }
        }
        graphics.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFFFFFFFF);
    }

    private void drawNode(GuiGraphics graphics, Talent talent, PlayerSkills skills, long now, boolean fxOn) {
        int x = nodeX(talent);
        int y = nodeY(talent);
        // While a rank is still sweeping in, the node shows the rank it had before it.
        int rank = fxOn ? fx.shownRank(idx(talent), now) : skills.rank(talent);
        boolean full = fxOn ? rank >= talent.maxRank() : skills.isFull(talent);
        boolean open = skills.check(talent) == TalentService.Outcome.OK;
        boolean big = talent.kind() == Talent.Kind.KEYSTONE || talent.kind() == Talent.Kind.CAPSTONE;

        int fill = full ? (accent() & 0x00FFFFFF) | 0x88000000
                : rank > 0 ? (accent() & 0x00FFFFFF) | 0x44000000
                : 0xFF181B20;
        int edge = full ? SkillPalette.MAXED
                : open || rank > 0 ? accent()
                : SkillPalette.TRACK;

        graphics.fill(x, y, x + node, y + node, fill);
        border(graphics, x, y, node, node, edge);
        if (big) {
            border(graphics, x - 2, y - 2, node + 4, node + 4, edge);
        }
        if (talent.kind() == Talent.Kind.BRIDGE) {
            // Bridges are the synergy inside a tree; a notch on each side says "joins two".
            graphics.fill(x - 3, y + node / 2 - 1, x, y + node / 2 + 1, edge);
            graphics.fill(x + node, y + node / 2 - 1, x + node + 3, y + node / 2 + 1, edge);
        }
        if (!talent.materials().isEmpty() && !skills.hasPaid(talent)) {
            // A corner pip: this node will want something from your bags when it fills.
            graphics.fill(x + node - 4, y + 1, x + node - 1, y + 4, 0xFFD2A249);
        }

        if (fxOn) {
            float sweep = fx.sweep(idx(talent), now);
            if (sweep >= 0f) {
                // The new rank fills the node from the bottom, with a bright front edge.
                int height = Math.round((float) XpGainDots.ease(sweep) * node);
                boolean nowFull = rank + 1 >= talent.maxRank();
                int body = (accent() & 0x00FFFFFF) | (nowFull ? 0xB0000000 : 0x90000000);
                graphics.fill(x + 1, y + node - height, x + node - 1, y + node - 1, body);
                if (height > 0 && height < node) {
                    graphics.fill(x + 1, y + node - height, x + node - 1, y + node - height + 1, 0xFFFFFFFF);
                }
            } else if (full && talent.kind() == Talent.Kind.CAPSTONE) {
                shimmer(graphics, talent, x, y, now);
            }
        }

        String text = talent.maxRank() == 1 ? (full ? "✔" : "◆") : rank + "/" + talent.maxRank();
        int colour = full ? SkillPalette.MAXED : open || rank > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM;
        graphics.drawCenteredString(this.font, text, x + node / 2 + 1, y + (node - 8) / 2, colour);
    }

    /** A slow diagonal glint across a finished capstone, with a soft halo while it passes. */
    private void shimmer(GuiGraphics graphics, Talent talent, int x, int y, long now) {
        float t = UnlockFx.shimmer(now, talent.col() * 401L);
        if (t < 0f) {
            return;
        }
        int centre = Math.round(t * (node * 2 + 6)) - 3;
        for (int row = 1; row < node - 1; row++) {
            int from = Math.max(1, centre - row - 1);
            int to = Math.min(node - 1, centre - row + 2);
            if (to > from) {
                graphics.fill(x + from, y + row, x + to, y + row + 1, 0x66FFFFFF);
            }
        }
        int halo = (int) (Math.sin(t * Math.PI) * 0x58);
        border(graphics, x - 3, y - 3, node + 6, node + 6, (SkillPalette.MAXED & 0x00FFFFFF) | (halo << 24));
    }

    @Nullable
    private Synergy drawSynergies(GuiGraphics graphics, PlayerSkills skills, int mouseX, int mouseY) {
        int x = panelX + PADDING + gridW + SIDE_GAP;
        int y = synergyTop();
        TextFit.draw(graphics, this.font, "tree.synergies", Component.translatable("proficiency.tree.synergies").getString(),
                x, y, sideW, 0xFFB98BE0, false);
        y += 11;

        double discipline = Synergies.discipline(skills, skill.category());
        // "<Category> discipline +0%" wraps to a second line rather than being cut: the number is
        // the point of the line.
        List<String> disc = TextFit.wrapPlain(this.font, disciplineLine(skills).getString(), sideW);
        discLines = Math.min(2, disc.size());
        for (int i = 0; i < discLines; i++) {
            String text = i == discLines - 1 && disc.size() > discLines
                    ? TextFit.clip(this.font, String.join(" ", disc.subList(i, disc.size())), sideW) : disc.get(i);
            graphics.drawString(this.font, text, x, y, discipline > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
            y += SYNERGY_ROW - 2;
        }
        y += 6;

        Synergy hovered = null;
        long now = System.currentTimeMillis();
        boolean fxOn = ProficiencyClientConfig.screensUnlockFx();
        int index = -1;
        for (Synergy synergy : synergies) {
            index++;
            boolean active = Synergies.isActive(skills, synergy);
            fx.observeSynergy(index, active, now);
            float pulse = fxOn ? fx.pulse(index, now) : -1f;
            if (pulse >= 0f) {
                // Once, in the synergy colour, as the line switches on.
                int alpha = (int) (Math.sin(pulse * Math.PI) * 0x70);
                graphics.fill(x - 3, y - 2, x + sideW, y + SYNERGY_ROW - 2, 0xB98BE0 | (alpha << 24));
            }
            int met = 0;
            for (Synergy.Need need : synergy.requires()) {
                if (skills.rank(need.talent()) >= need.minRank()) {
                    met++;
                }
            }
            int total = synergy.requires().size();
            if (synergy.grandmastersNeeded() > 0) {
                met = Math.min(skills.grandmasters(), synergy.grandmastersNeeded());
                total = synergy.grandmastersNeeded();
            }
            String marker = active ? "\u2726 " : met + "/" + total + " ";
            // The marker stays whole; the name is cut with an ellipsis, and the hover has all of it.
            String name = synergy.displayName().getString();
            int room = sideW - this.font.width(marker);
            String shown = TextFit.clip(this.font, name, room);
            if (!shown.equals(name)) {
                TextFit.note("tree.synergy");
            }
            graphics.drawString(this.font, marker + shown,
                    x, y, active ? 0xFFD9B8FF : met > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
            if (inside(mouseX, mouseY, x, y - 1, sideW, SYNERGY_ROW)) {
                hovered = synergy;
            }
            y += SYNERGY_ROW;
        }
        synergiesBottom = y;
        return hovered;
    }

    /**
     * This skill's last few XP gains, under the synergies: "+12.0 · Iron Ore ×5" with the age on
     * the right. It is the one place that answers "did that just pay, and how much", which the bar
     * alone cannot for a skill at level 60 where a block is a sliver. Only this tree's skill is
     * listed; the hover tooltip has what does not fit on the line (see {@link #logTooltip}).
     *
     * <p>The list stops above the footer line rather than at the panel edge: the footer's text is
     * wider than the grid and runs underneath this column. Lines older than ten seconds fade, so
     * the eye lands on what just happened.
     */
    @Nullable
    private ClientXpLog.Line drawXpLog(GuiGraphics graphics, int top, int mouseX, int mouseY) {
        int x = panelX + PADDING + gridW + SIDE_GAP;
        int bottom = panelY + padV + headerH + gridH;
        if (top + 11 + LOG_ROW > bottom) {
            return null;
        }
        TextFit.draw(graphics, this.font, "tree.xp_log", Component.translatable("proficiency.tree.xp_log").getString(),
                x, top, sideW, 0xFFB98BE0, false);
        int y = top + 11;

        List<ClientXpLog.Line> lines = ClientXpLog.lines(skill);
        if (lines.isEmpty()) {
            for (String line : TextFit.wrapPlain(this.font,
                    Component.translatable("proficiency.tree.xp_log.empty").getString(), sideW)) {
                if (y + LOG_ROW > bottom + 2) {
                    break;
                }
                graphics.drawString(this.font, line, x, y, SkillPalette.TEXT_DIM, false);
                y += LOG_ROW;
            }
            return null;
        }
        long now = System.currentTimeMillis();
        ClientXpLog.Line hovered = null;
        for (ClientXpLog.Line line : lines) {
            if (y + LOG_ROW > bottom) {
                break;
            }
            long age = Math.max(0L, now - line.at());
            int alpha = fade(age);

            String ago = ago(age);
            int agoWidth = this.font.width(ago);
            graphics.drawString(this.font, ago, x + sideW - agoWidth, y,
                    withAlpha(SkillPalette.TEXT_DIM, alpha), false);

            // Amount in the skill's colour, then the source, then the count dimmed, all cut to
            // what is left once the age has its room on the right. Every line is this tree's
            // skill, so the skill's name would only take the source's room.
            int room = sideW - agoWidth - 4;
            String amount = String.format(java.util.Locale.ROOT, "+%.1f", line.amount());
            String source = line.source().isEmpty() ? "" : " · " + sourceName(line.source());
            String count = line.count() > 1 ? " ×" + line.count() : "";
            int cursor = x;
            cursor = drawClipped(graphics, amount, cursor, y, x + room, withAlpha(accent(), alpha));
            cursor = drawClipped(graphics, source, cursor, y, x + room, withAlpha(SkillPalette.TEXT, alpha));
            drawClipped(graphics, count, cursor, y, x + room, withAlpha(SkillPalette.TEXT_DIM, alpha));
            if (inside(mouseX, mouseY, x, y - 1, sideW, LOG_ROW)) {
                hovered = line;
            }
            y += LOG_ROW;
        }
        return hovered;
    }

    /**
     * Everything about one log line that does not fit on it: the whole source name, how many gains
     * it merges and over how long, what was asked for before multipliers against what was paid,
     * and each multiplier of the newest gain by name.
     */
    private List<Component> logTooltip(ClientXpLog.Line line) {
        List<Component> out = new java.util.ArrayList<>();
        java.util.Locale root = java.util.Locale.ROOT;
        boolean detailed = TooltipDetail.detailed();
        out.add(Component.translatable("proficiency.tree.xp_log.tip.title",
                        String.format(root, "%.1f", line.amount()),
                        Component.translatable(line.skill().translationKey()))
                .withColor(accent()));
        if (!line.source().isEmpty()) {
            out.add(Component.literal(sourceName(line.source())).withColor(SkillPalette.TEXT));
        }
        long now = System.currentTimeMillis();
        String last = ago(Math.max(0L, now - line.at()));
        if (line.count() > 1) {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.merged", line.count(),
                    ago(Math.max(0L, now - line.first())), last).withColor(SkillPalette.TEXT_DIM));
            if (!detailed) {
                return TooltipDetail.withHint(out, true);
            }
            out.add(Component.translatable("proficiency.tree.xp_log.tip.average",
                    String.format(root, "%.2f", line.amount() / line.count()))
                    .withColor(SkillPalette.TEXT_DIM));
        } else {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.single", last)
                    .withColor(SkillPalette.TEXT_DIM));
            if (!detailed) {
                return TooltipDetail.withHint(out, true);
            }
        }
        if (line.base() > 0) {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.base",
                    String.format(root, "%.1f", line.base()),
                    String.format(root, "%.1f", line.amount())).withColor(SkillPalette.TEXT_DIM));
        }
        List<XpFactors.Factor> factors = XpFactors.decode(line.factors());
        if (factors.isEmpty()) {
            out.add(Component.translatable("proficiency.tree.xp_log.tip.no_factors")
                    .withColor(SkillPalette.TEXT_DIM));
        } else {
            out.add(Component.translatable(line.count() > 1
                    ? "proficiency.tree.xp_log.tip.factors_newest"
                    : "proficiency.tree.xp_log.tip.factors").withColor(0xFFB98BE0));
            for (XpFactors.Factor factor : factors) {
                out.add(Component.literal("  ")
                        .append(Component.translatable("proficiency.xpfeed.factor." + factor.id()))
                        .append(String.format(root, " ×%.2f", factor.value()))
                        .withColor(factor.value() >= 1f ? 0xFF8FD18F : 0xFFE08F8F));
            }
        }
        return out;
    }

    /**
     * Draws as much of {@code text} as fits before {@code limit}, with an ellipsis when it had to
     * cut, and returns where the next piece starts.
     */
    private int drawClipped(GuiGraphics graphics, String text, int x, int y, int limit, int colour) {
        if (text.isEmpty() || x >= limit) {
            return x;
        }
        int room = limit - x;
        String shown = text;
        if (this.font.width(text) > room) {
            int ellipsis = this.font.width("…");
            shown = room > ellipsis ? this.font.plainSubstrByWidth(text, room - ellipsis) + "…" : "";
            TextFit.note("tree.log");
        }
        graphics.drawString(this.font, shown, x, y, colour, false);
        return x + this.font.width(shown);
    }

    /**
     * A source is a translation key. Blocks, entities and biomes have their own; the damage types
     * and movement lines are this mod's. Anything unknown still reads as words: the last part of
     * the key, underscores to spaces, first letter up.
     */
    static String sourceName(String key) {
        // The first-time bonus line: "first|<kind key>", shown as "First time · Iron Ore".
        if (key.startsWith("first|")) {
            return Component.translatable("proficiency.xplog.source.first_time",
                    sourceName(key.substring("first|".length()))).getString();
        }
        // The kill bonus line: "kill|<entity key>", shown as "Kill · Zombie".
        if (key.startsWith("kill|")) {
            return Component.translatable("proficiency.xplog.source.kill",
                    sourceName(key.substring("kill|".length()))).getString();
        }
        String tail = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ');
        String fallback = tail.isEmpty() ? key
                : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
        return Component.translatableWithFallback(key, fallback).getString();
    }

    private static String ago(long millis) {
        long seconds = millis / 1000L;
        if (seconds < 1) {
            return Component.translatable("proficiency.tree.xp_log.now").getString();
        }
        if (seconds < 60) {
            return Component.translatable("proficiency.tree.xp_log.seconds", seconds).getString();
        }
        if (seconds < 3600) {
            return Component.translatable("proficiency.tree.xp_log.minutes", seconds / 60).getString();
        }
        return Component.translatable("proficiency.tree.xp_log.hours", seconds / 3600).getString();
    }

    /** Full strength while fresh, then a straight fade down to a floor that stays readable. */
    private static int fade(long age) {
        if (age <= LOG_FRESH_MILLIS) {
            return 0xFF;
        }
        double t = Math.min(1.0, (age - LOG_FRESH_MILLIS) / (double) LOG_FADE_MILLIS);
        return (int) Math.round(0xFF - (0xFF - LOG_FADED_ALPHA) * t);
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * {@code rank} is the one the node shows, so a filling node and its tooltip agree. Short: the
     * name with its rank, one line of what it does, and what a click does. Detailed (Shift): the
     * kind, the full description, the numbers per rank, the cost, the materials and the synergies
     * the node feeds.
     */
    private List<Component> nodeTooltip(Talent talent, PlayerSkills skills, int rank) {
        List<Component> lines = new ArrayList<>();
        TalentService.Outcome outcome = skills.check(talent);
        if (!TooltipDetail.detailed()) {
            lines.add(talent.displayName().copy().withStyle(style -> style.withColor(accent()))
                    .append(Component.literal(" " + rank + "/" + talent.maxRank()).withStyle(ChatFormatting.DARK_GRAY)));
            lines.add(TooltipDetail.shortOr(talent.shortKey(), talent.descriptionKey()).copy()
                    .withStyle(ChatFormatting.GRAY));
            if (outcome == TalentService.Outcome.OK) {
                lines.add(Component.translatable("proficiency.tree.click.short", talent.costPerRank())
                        .withStyle(ChatFormatting.YELLOW));
            } else if (outcome == TalentService.Outcome.MISSING_MATERIALS) {
                lines.add(Component.translatable("proficiency.tree.materials.short").withStyle(ChatFormatting.GOLD));
            } else {
                lines.add(TalentService.explain(skills, talent, outcome).copy()
                        .withStyle(outcome == TalentService.Outcome.MAXED ? ChatFormatting.GOLD : ChatFormatting.RED));
            }
            return TooltipDetail.withHint(lines, true);
        }
        lines.add(talent.displayName().copy().withStyle(style -> style.withColor(accent())));
        lines.add(Component.translatable("proficiency.talent.kind." + talent.kind().name().toLowerCase())
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(talent.descriptionKey()).withStyle(ChatFormatting.GRAY));

        for (Map.Entry<PerkEffect, Double> effect : talent.perRank().entrySet()) {
            String per = signedPercent(effect.getValue());
            Component text = Component.translatable(effect.getKey().translationKey(), per);
            lines.add(talent.maxRank() == 1
                    ? text.copy().withStyle(ChatFormatting.DARK_AQUA)
                    : Component.translatable("proficiency.tree.per_rank", text,
                            signedPercent(effect.getValue() * rank)).withStyle(ChatFormatting.DARK_AQUA));
        }

        lines.add(Component.translatable("proficiency.tree.rank", rank, talent.maxRank(),
                talent.costPerRank(), talent.requiredLevel()).withStyle(ChatFormatting.GRAY));

        if (!talent.materials().isEmpty()) {
            if (skills.hasPaid(talent)) {
                lines.add(Component.translatable("proficiency.tree.paid").withStyle(ChatFormatting.DARK_GREEN));
            } else {
                lines.add(Component.translatable("proficiency.tree.materials").withStyle(ChatFormatting.GOLD));
                Player player = this.minecraft == null ? null : this.minecraft.player;
                for (Requirement requirement : TalentService.payableMaterials(talent)) {
                    int held = player == null ? 0 : requirement.countIn(player.getInventory());
                    lines.add(Component.translatable("proficiency.perk.missing_line",
                                    requirement.displayName(), Math.min(held, requirement.count()),
                                    requirement.count())
                            .withStyle(held >= requirement.count() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
                }
            }
        }

        if (outcome == TalentService.Outcome.OK) {
            lines.add(Component.translatable("proficiency.tree.click").withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(TalentService.explain(skills, talent, outcome).copy()
                    .withStyle(outcome == TalentService.Outcome.MAXED ? ChatFormatting.GOLD : ChatFormatting.RED));
        }

        // Which synergies elsewhere this node feeds, so the tree shows where it leads.
        for (Synergy synergy : Synergies.all()) {
            for (Synergy.Need need : synergy.requires()) {
                if (need.skill() == skill && need.talentId().equals(talent.id())) {
                    lines.add(Component.translatable("proficiency.tree.feeds", synergy.displayName(),
                            need.minRank()).withStyle(ChatFormatting.DARK_PURPLE));
                }
            }
        }
        return lines;
    }

    private List<Component> synergyTooltip(Synergy synergy, PlayerSkills skills) {
        List<Component> lines = new ArrayList<>();
        boolean active = Synergies.isActive(skills, synergy);
        lines.add(synergy.displayName().copy().withStyle(active ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE));
        if (!TooltipDetail.detailed()) {
            lines.add(TooltipDetail.shortOr(synergy.shortKey(), synergy.descriptionKey()).copy()
                    .withStyle(ChatFormatting.GRAY));
            int parts = synergy.requires().size() + (synergy.grandmastersNeeded() > 0 ? 1 : 0);
            int ready = (int) synergy.requires().stream()
                    .filter(need -> skills.rank(need.talent()) >= need.minRank()).count()
                    + (synergy.grandmastersNeeded() > 0 && skills.grandmasters() >= synergy.grandmastersNeeded() ? 1 : 0);
            lines.add(active
                    ? Component.translatable("proficiency.synergy.state.awake").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("proficiency.synergy.state.parts", ready, parts)
                            .withStyle(ChatFormatting.DARK_GRAY));
            return TooltipDetail.withHint(lines, true);
        }
        lines.add(Component.translatable(synergy.descriptionKey()).withStyle(ChatFormatting.GRAY));
        for (Synergy.Need need : synergy.requires()) {
            int rank = skills.rank(need.talent());
            lines.add(Component.translatable("proficiency.synergy.need",
                            Component.translatable(need.skill().translationKey()), need.talent().displayName(),
                            Math.min(rank, need.minRank()), need.minRank())
                    .withStyle(rank >= need.minRank() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
        if (synergy.grandmastersNeeded() > 0) {
            lines.add(Component.translatable("proficiency.synergy.need_grandmasters",
                    Math.min(skills.grandmasters(), synergy.grandmastersNeeded()),
                    synergy.grandmastersNeeded()).withStyle(ChatFormatting.DARK_GRAY));
        }
        for (Synergy.Grant grant : synergy.grants()) {
            Component target = grant.skill() == null
                    ? Component.translatable("proficiency.synergy.every_skill")
                    : Component.translatable(grant.skill().translationKey());
            lines.add(Component.translatable("proficiency.synergy.grant", target,
                            Component.translatable(grant.effect().translationKey(),
                                    signedPercent(grant.multiplier() - 1.0)))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines;
    }

    private static String signedPercent(double fraction) {
        long percent = Math.round(fraction * 100);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerSkills skills = playerSkills();
        if (button == 0 && skills != null) {
            double px = mouseX / uiScale;
            double py = mouseY / uiScale;
            for (Talent talent : Talents.of(skill)) {
                if (!inside(px, py, nodeX(talent), nodeY(talent), node, node)) {
                    continue;
                }
                // Shift fills the node in one go; the server takes each rank separately and stops
                // at the first one it refuses.
                int clicks = hasShiftDown() ? talent.maxRank() - skills.rank(talent) : 1;
                for (int i = 0; i < Math.max(1, clicks); i++) {
                    PacketDistributor.sendToServer(new UnlockPerkPayload(talent.key()));
                }
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Nullable
    private PlayerSkills playerSkills() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        return player == null ? null : ProficiencyAttachments.of(player);
    }
}
