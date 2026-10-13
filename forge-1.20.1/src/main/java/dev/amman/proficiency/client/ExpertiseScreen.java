package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.ExpertisePayload;
import dev.amman.proficiency.perk.Expertise;
import dev.amman.proficiency.perk.ExpertiseNode;
import dev.amman.proficiency.perk.ExpertiseService;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SkillCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Expertise tree: one grid across every skill, drawn like a talent tree (the same nodes, elbow
 * lines and {@link UnlockFx} sweep), with the points header on top and what the tree gives down the
 * right-hand side. Click a node for one rank, shift-click to fill it. The server re-checks every
 * click, so everything drawn here is only a forecast. Opened from the skills panel's button.
 */
public final class ExpertiseScreen extends Screen {

    private static final int PADDING = 10;
    private static final int COLUMNS = 5;
    private static final int ROWS = 6;
    private static final int SIDE_GAP = 14;
    private static final int SIDE_ROW = 11;
    private static final int HEADER_LINE = 9;
    private static final int BUTTON_H = 14;
    /** The respec button wants a second click within this long. */
    private static final long CONFIRM_MS = 4000L;
    /** Node size, column step and row step, biggest first; the first that fits the window is used. */
    private static final int[][] GEOMETRIES = {{24, 46, 34}, {22, 42, 30}, {20, 38, 26}, {18, 36, 24}};
    private static final int SIDE_MIN = 120;
    private static final int SIDE_MAX = 220;
    /** Every-skill nodes: the gold of a maxed node, softened. */
    private static final int ALL_ACCENT = 0xFFE2C46E;

    @Nullable
    private final Screen parent;
    private final UnlockFx fx = new UnlockFx(COLUMNS * ROWS, 0);

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
    private float uiScale = 1f;
    private int panelX;
    private int panelY;
    private List<String> headerLines = List.of();
    /** How many header lines the points text took (the first is the title); the sources lines follow, dimmer. */
    private int pointsLines = 1;
    private List<String> footerLines = List.of();
    private String layoutKey = "";
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private List<Component> pendingTip = List.of();
    private int buttonX;
    private int buttonY;
    private int buttonW;
    private long confirmUntil;
    /** The pointer is on a side list that had to cut something; its tooltip shows it whole. */
    private boolean sideHovered;

    public ExpertiseScreen(@Nullable Screen parent) {
        super(Component.translatable("proficiency.expertise.title"));
        this.parent = parent;
    }

    private static int idx(ExpertiseNode n) {
        return Math.max(0, Math.min(COLUMNS * ROWS - 1, n.row() * COLUMNS + n.col()));
    }

    @Override
    protected void init() {
        layoutKey = "";
        PlayerSkills skills = playerSkills();
        if (skills != null) {
            layout(skills);
        }
    }

    /** "Expertise 12 / 70 · spent 8 · 4 free". */
    private static Component pointsLine(PlayerSkills skills) {
        return Component.translatable("proficiency.expertise.points", skills.expertisePoints(), Expertise.MAX_POINTS,
                skills.expertiseSpent(), skills.expertiseAvailable());
    }

    /** "Levels 1,234: 12/35 · Stars 17: 3/35". */
    private static Component sourcesLine(PlayerSkills skills) {
        return Component.translatable("proficiency.expertise.sources",
                skills.totalLevels(), Expertise.pointsFromLevels(skills.totalLevels()), Expertise.MAX_FROM_LEVELS,
                skills.totalStars(), Expertise.pointsFromStars(skills.totalStars()), Expertise.MAX_FROM_STARS);
    }

    private void layout(PlayerSkills skills) {
        String title = this.title.getString();
        String points = pointsLine(skills).getString();
        String sources = sourcesLine(skills).getString();
        String footer = Component.translatable("proficiency.expertise.footer").getString();
        String key = title + '\n' + points + '\n' + sources + '\n' + footer;
        if (key.equals(layoutKey) && layoutWidth == this.width && layoutHeight == this.height) {
            return;
        }
        layoutKey = key;
        layoutWidth = this.width;
        layoutHeight = this.height;

        int sideWanted = 150;
        for (SideLine line : bonusTexts(fullTree())) {
            sideWanted = Math.max(sideWanted, this.font.width(line.text()) + line.indent() + 4);
        }
        sideWanted = Math.max(sideWanted, this.font.width(respecLabel(Expertise.MAX_POINTS)) + 14);
        sideWanted = Math.min(SIDE_MAX, sideWanted);
        int availW = this.width - 8;
        int availH = this.height - 8;
        for (int[] geometry : GEOMETRIES) {
            node = geometry[0];
            colStep = geometry[1];
            rowStep = geometry[2];
            gridW = (COLUMNS - 1) * colStep + node;
            gridH = (ROWS - 1) * rowStep + node;
            sideW = sideWanted;
            panelW = PADDING * 2 + gridW + SIDE_GAP + sideW;
            if (panelW > availW) {
                sideW = Math.max(SIDE_MIN, sideW - (panelW - availW));
                panelW = PADDING * 2 + gridW + SIDE_GAP + sideW;
            }
            buildText(title, points, sources, footer, panelW - PADDING * 2);
            panelH = PADDING + headerH + gridH + 6 + footerH + PADDING;
            if (panelW <= availW && panelH <= availH) {
                break;
            }
        }
        uiScale = Math.min(1f, Math.min(availW / (float) panelW, availH / (float) panelH));
        int vw = Math.round(this.width / uiScale);
        int vh = Math.round(this.height / uiScale);
        panelX = (vw - panelW) / 2;
        panelY = Math.max(2, (vh - panelH) / 2);
        if (uiScale < 1f) {
            TextFit.note("expertise.scaled");
        }
    }

    private void buildText(String title, String points, String sources, String footer, int width) {
        List<String> lines = new ArrayList<>();
        boolean[] cut = new boolean[1];
        List<String> pointsPacked = TextFit.pack(this.font, points, width, 2, cut, TextFit.DOT_RE, TextFit.DOT);
        pointsLines = pointsPacked.size();
        lines.addAll(pointsPacked);
        lines.addAll(TextFit.pack(this.font, sources, width, 2, cut, TextFit.DOT_RE, TextFit.DOT));
        if (cut[0]) {
            TextFit.note("expertise.header");
        }
        headerLines = lines;
        headerH = 10 + HEADER_LINE * (lines.size() - 1) + 4;
        boolean[] footerCut = new boolean[1];
        footerLines = TextFit.pack(this.font, footer, width, 3, footerCut, "\\s{2,}", "   ");
        if (footerCut[0]) {
            TextFit.note("expertise.footer");
        }
        footerH = HEADER_LINE * footerLines.size() + 1;
    }

    private int nodeX(ExpertiseNode n) {
        return panelX + PADDING + n.col() * colStep;
    }

    private int nodeY(ExpertiseNode n) {
        return panelY + PADDING + headerH + n.row() * rowStep;
    }

    /** Screen position of a node's centre (real GUI pixels); the layout demo hovers it. */
    int[] nodePoint(String id) {
        PlayerSkills skills = playerSkills();
        if (skills != null) {
            layout(skills);
        }
        ExpertiseNode n = Expertise.byId(id);
        return new int[] {Math.round((nodeX(n) + node / 2) * uiScale), Math.round((nodeY(n) + node / 2) * uiScale)};
    }

    /** Centre of the respec button (real GUI pixels); the layout demo hovers it. */
    int[] respecPoint() {
        return new int[] {Math.round((buttonX + buttonW / 2) * uiScale), Math.round((buttonY + BUTTON_H / 2) * uiScale)};
    }

    /** Centre of the points header (real GUI pixels); the layout demo hovers it. */
    int[] headerPoint() {
        return new int[] {Math.round((panelX + PADDING + 30) * uiScale), Math.round((panelY + PADDING + 4) * uiScale)};
    }

    /** The colour of a node: its branch's category accent, gold for every-skill nodes. */
    static int accent(ExpertiseNode n) {
        return switch (Expertise.branch(n)) {
            case "martial" -> SkillPalette.accent(SkillCategory.COMBAT);
            case "craft" -> SkillPalette.accent(SkillCategory.GATHERING);
            case "roaming" -> SkillPalette.accent(SkillCategory.MOVEMENT);
            default -> ALL_ACCENT;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21's Screen.render draws the background itself; 1.20.1's does not.
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        PlayerSkills skills = playerSkills();
        if (skills == null) {
            return;
        }
        layout(skills);
        pendingTip = List.of();
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
        int y = panelY + PADDING;
        for (int i = 0; i < headerLines.size(); i++) {
            int colour = i == 0 ? SkillPalette.MAXED : i < pointsLines ? SkillPalette.TEXT : SkillPalette.TEXT_DIM;
            graphics.drawString(this.font, headerLines.get(i), panelX + PADDING, y, colour, false);
            y += i == 0 ? 10 : HEADER_LINE;
        }
        boolean headerHovered = inside(mouseX, mouseY, panelX + PADDING, panelY + PADDING - 1, panelW - PADDING * 2,
                headerH - 3);

        long now = System.currentTimeMillis();
        boolean fxOn = ProficiencyClientConfig.screensUnlockFx();
        List<ExpertiseNode> nodes = Expertise.nodes();
        for (ExpertiseNode n : nodes) {
            if (fxOn) {
                fx.observe(idx(n), skills.expertiseRank(n), n.maxRank(), now);
            } else {
                fx.snap(idx(n), skills.expertiseRank(n));
            }
        }
        for (ExpertiseNode n : nodes) {
            for (String parentId : n.parents()) {
                ExpertiseNode from = Expertise.byId(parentId);
                if (from == null) {
                    continue;
                }
                if (fxOn && fx.linesPending(idx(from), now)) {
                    connector(graphics, from, n, SkillPalette.TRACK, accent(from), fx.travel(idx(from), now));
                } else {
                    connector(graphics, from, n, skills.isExpertiseFull(from) ? accent(from) : SkillPalette.TRACK, 0, -1f);
                }
            }
        }
        ExpertiseNode hovered = null;
        for (ExpertiseNode n : nodes) {
            drawNode(graphics, n, skills, now, fxOn);
            if (inside(mouseX, mouseY, nodeX(n), nodeY(n), node, node)) {
                hovered = n;
            }
        }

        boolean buttonHovered = drawSide(graphics, skills, mouseX, mouseY, now);

        int footY = panelY + panelH - PADDING - footerH + 1;
        for (String line : footerLines) {
            graphics.drawString(this.font, line, panelX + PADDING, footY, SkillPalette.TEXT_DIM, false);
            footY += HEADER_LINE;
        }

        if (hovered != null) {
            pendingTip = nodeTooltip(hovered, skills, fxOn ? fx.shownRank(idx(hovered), now) : skills.expertiseRank(hovered));
        } else if (sideHovered) {
            pendingTip = new ArrayList<>();
            pendingTip.add(Component.translatable("proficiency.expertise.gives").withStyle(style -> style.withColor(SkillPalette.MAXED)));
            for (SideLine line : bonusTexts(skills.expertiseRanks())) {
                pendingTip.add(Component.literal((line.indent() > 0 ? "  " : "") + line.text())
                        .withStyle(line.heading() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
            }
        } else if (buttonHovered) {
            pendingTip = respecTooltip(skills);
        } else if (headerHovered) {
            pendingTip = headerTooltip(skills);
        }
    }

    /** What the tree gives, grouped, then the respec button. True while the pointer is on the button. */
    private boolean drawSide(GuiGraphics graphics, PlayerSkills skills, int mouseX, int mouseY, long now) {
        int x = panelX + PADDING + gridW + SIDE_GAP;
        int y = panelY + PADDING + headerH;
        int limit = x + sideW;
        sideHovered = false;
        graphics.drawString(this.font, TextFit.clip(this.font,
                Component.translatable("proficiency.expertise.gives").getString(), sideW), x, y, SkillPalette.MAXED, false);
        y += SIDE_ROW + 2;
        List<SideLine> bonuses = bonusTexts(skills.expertiseRanks());
        if (bonuses.isEmpty()) {
            for (String line : TextFit.wrapLimited(this.font,
                    Component.translatable("proficiency.expertise.gives.none").getString(), sideW, 4, new boolean[1])) {
                graphics.drawString(this.font, line, x, y, SkillPalette.TEXT_DIM, false);
                y += SIDE_ROW;
            }
        } else {
            // The list stops above the respec button. In a small window the rows close up, and what
            // still does not fit ends in "+3 more"; a hover on the list shows all of it, whole.
            int top = y;
            int room = panelY + PADDING + headerH + gridH - BUTTON_H - 3 - y;
            int row = bonuses.size() * SIDE_ROW <= room ? SIDE_ROW : 9;
            int fits = Math.max(1, room / row);
            boolean cut = false;
            for (int i = 0; i < bonuses.size(); i++) {
                SideLine line = bonuses.get(i);
                if (i == fits - 1 && bonuses.size() > fits) {
                    graphics.drawString(this.font, Component.translatable("proficiency.expertise.gives.more",
                            bonuses.size() - i).getString(), x, y, SkillPalette.TEXT_DIM, false);
                    TextFit.note("expertise.side_more");
                    y += row;
                    cut = true;
                    break;
                }
                int width = limit - x - line.indent();
                cut |= this.font.width(line.text()) > width;
                TextFit.draw(graphics, this.font, "expertise.side", line.text(), x + line.indent(), y,
                        width, line.heading() ? SkillPalette.TEXT_DIM : SkillPalette.TEXT, false);
                y += row;
            }
            sideHovered = cut && inside(mouseX, mouseY, x, top, sideW, y - top);
        }

        int spent = skills.expertiseSpent();
        String label = now < confirmUntil
                ? Component.translatable("proficiency.expertise.respec.confirm").getString()
                : respecLabel(spent);
        buttonW = Math.min(sideW, this.font.width(label) + 12);
        buttonX = x;
        buttonY = panelY + PADDING + headerH + gridH - BUTTON_H;
        boolean enabled = spent > 0;
        boolean hover = inside(mouseX, mouseY, buttonX, buttonY, buttonW, BUTTON_H);
        int edge = !enabled ? SkillPalette.TRACK : now < confirmUntil ? 0xFFE08F8F : hover ? SkillPalette.MAXED : ALL_ACCENT;
        graphics.fill(buttonX, buttonY, buttonX + buttonW, buttonY + BUTTON_H,
                hover && enabled ? (ALL_ACCENT & 0x00FFFFFF) | 0x40000000 : 0xFF181B20);
        border(graphics, buttonX, buttonY, buttonW, BUTTON_H, edge);
        TextFit.draw(graphics, this.font, "expertise.respec", label, buttonX + 6, buttonY + 3, buttonW - 10,
                enabled ? (hover ? SkillPalette.MAXED : SkillPalette.TEXT) : SkillPalette.TEXT_DIM, false);
        return hover;
    }

    private String respecLabel(int spent) {
        return Component.translatable("proficiency.expertise.respec.button", Expertise.respecPrice(spent)).getString();
    }

    private static Map<String, Integer> fullTree() {
        Map<String, Integer> full = new LinkedHashMap<>();
        for (ExpertiseNode n : Expertise.nodes()) {
            full.put(n.id(), n.maxRank());
        }
        return full;
    }

    /** One line of the side column: a heading ("In every skill:") or a bonus, maybe indented under it. */
    record SideLine(String text, int indent, boolean heading) {
    }

    /**
     * What the tree gives, in tree order: the every-skill bonuses under one heading ("+7% XP"), then
     * one line per effect and branch ("+6% passive (Martial)").
     */
    static List<SideLine> bonusTexts(Map<String, Integer> ranks) {
        Map<String, Double> sums = new LinkedHashMap<>();
        for (ExpertiseNode n : Expertise.nodes()) {
            int rank = ranks.getOrDefault(n.id(), 0);
            if (rank <= 0) {
                continue;
            }
            for (Map.Entry<PerkEffect, Double> effect : n.perRank().entrySet()) {
                sums.merge(effect.getKey().name() + '|' + Expertise.branch(n), effect.getValue() * rank, Double::sum);
            }
        }
        List<SideLine> everySkill = new ArrayList<>();
        List<SideLine> branches = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sums.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            Component effect = effectText(PerkEffect.valueOf(parts[0]), entry.getValue());
            if (parts[1].equals("all")) {
                everySkill.add(new SideLine(effect.getString(), 6, false));
            } else {
                branches.add(new SideLine(Component.translatable("proficiency.expertise.bonus_line", effect,
                        Component.translatable("proficiency.expertise.branch." + parts[1])).getString(), 0, false));
            }
        }
        List<SideLine> lines = new ArrayList<>();
        if (!everySkill.isEmpty()) {
            lines.add(new SideLine(Component.translatable("proficiency.expertise.gives.all").getString(), 0, true));
            lines.addAll(everySkill);
        }
        lines.addAll(branches);
        return lines;
    }

    private static Component effectText(PerkEffect effect, double value) {
        return Component.translatable("proficiency.expertise.effect." + effect.name().toLowerCase(java.util.Locale.ROOT),
                signedPercent(value));
    }

    /** Elbow from the bottom of one node to the top of the next: down, across, down. */
    private void connector(GuiGraphics graphics, ExpertiseNode from, ExpertiseNode to, int colour, int lit, float travel) {
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

    private void drawNode(GuiGraphics graphics, ExpertiseNode n, PlayerSkills skills, long now, boolean fxOn) {
        int x = nodeX(n);
        int y = nodeY(n);
        int rank = fxOn ? fx.shownRank(idx(n), now) : skills.expertiseRank(n);
        boolean full = fxOn ? rank >= n.maxRank() : skills.isExpertiseFull(n);
        boolean open = skills.checkExpertise(n) == TalentOutcome.OK;
        int accent = accent(n);
        int fill = full ? (accent & 0x00FFFFFF) | 0x88000000
                : rank > 0 ? (accent & 0x00FFFFFF) | 0x44000000
                : 0xFF181B20;
        int edge = full ? SkillPalette.MAXED : open || rank > 0 ? accent : SkillPalette.TRACK;
        graphics.fill(x, y, x + node, y + node, fill);
        border(graphics, x, y, node, node, edge);
        if (n.kind() == Talent.Kind.CAPSTONE) {
            border(graphics, x - 2, y - 2, node + 4, node + 4, edge);
        }
        if (n.kind() == Talent.Kind.BRIDGE) {
            graphics.fill(x - 3, y + node / 2 - 1, x, y + node / 2 + 1, edge);
            graphics.fill(x + node, y + node / 2 - 1, x + node + 3, y + node / 2 + 1, edge);
        }
        if (fxOn) {
            float sweep = fx.sweep(idx(n), now);
            if (sweep >= 0f) {
                int height = Math.round((float) XpGainDots.ease(sweep) * node);
                boolean nowFull = rank + 1 >= n.maxRank();
                int body = (accent & 0x00FFFFFF) | (nowFull ? 0xB0000000 : 0x90000000);
                graphics.fill(x + 1, y + node - height, x + node - 1, y + node - 1, body);
                if (height > 0 && height < node) {
                    graphics.fill(x + 1, y + node - height, x + node - 1, y + node - height + 1, 0xFFFFFFFF);
                }
            } else if (full && n.kind() == Talent.Kind.CAPSTONE) {
                shimmer(graphics, x, y, now);
            }
        }
        String text = n.maxRank() == 1 ? (full ? "✔" : "◆") : rank + "/" + n.maxRank();
        int colour = full ? SkillPalette.MAXED : open || rank > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM;
        graphics.drawCenteredString(this.font, text, x + node / 2 + 1, y + (node - 8) / 2, colour);
    }

    private void shimmer(GuiGraphics graphics, int x, int y, long now) {
        float t = UnlockFx.shimmer(now, 777L);
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

    /** The categories a node reaches, by name, or "every skill". */
    private static Component reaches(ExpertiseNode n) {
        if (n.categories().isEmpty()) {
            return Component.translatable("proficiency.expertise.branch.all");
        }
        MutableComponent names = Component.empty();
        boolean first = true;
        for (SkillCategory category : SkillCategory.values()) {
            if (n.categories().contains(category)) {
                names.append(first ? Component.empty() : Component.literal(", "));
                names.append(Component.translatable(category.translationKey()));
                first = false;
            }
        }
        return names;
    }

    /**
     * Short: the name with its rank, what one rank gives and to whom, and what a click does.
     * Detailed (Shift): the kind, the description, every effect per rank and now, the categories it
     * reaches, the rank and cost.
     */
    private List<Component> nodeTooltip(ExpertiseNode n, PlayerSkills skills, int rank) {
        List<Component> lines = new ArrayList<>();
        TalentOutcome outcome = skills.checkExpertise(n);
        int accent = accent(n);
        Component branch = Component.translatable("proficiency.expertise.branch." + Expertise.branch(n));
        if (!TooltipDetail.detailed()) {
            lines.add(Component.translatable(n.nameKey()).copy().withStyle(style -> style.withColor(accent))
                    .append(Component.literal(" " + rank + "/" + n.maxRank()).withStyle(ChatFormatting.DARK_GRAY)));
            for (Map.Entry<PerkEffect, Double> effect : n.perRank().entrySet()) {
                lines.add(Component.translatable("proficiency.expertise.bonus_line",
                        effectText(effect.getKey(), effect.getValue()), branch).withStyle(ChatFormatting.GRAY));
            }
            lines.add(clickLine(skills, n, outcome, true));
            return TooltipDetail.withHint(lines, true);
        }
        lines.add(Component.translatable(n.nameKey()).copy().withStyle(style -> style.withColor(accent)));
        lines.add(Component.translatable("proficiency.talent.kind." + n.kind().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(n.descriptionKey()).withStyle(ChatFormatting.GRAY));
        for (Map.Entry<PerkEffect, Double> effect : n.perRank().entrySet()) {
            Component text = effectText(effect.getKey(), effect.getValue());
            lines.add(n.maxRank() == 1 ? text.copy().withStyle(ChatFormatting.DARK_AQUA)
                    : Component.translatable("proficiency.tree.per_rank", text,
                            signedPercent(effect.getValue() * rank)).withStyle(ChatFormatting.DARK_AQUA));
        }
        lines.add(Component.translatable("proficiency.expertise.reaches", reaches(n)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("proficiency.expertise.rank", rank, n.maxRank(), n.costPerRank())
                .withStyle(ChatFormatting.GRAY));
        lines.add(clickLine(skills, n, outcome, false));
        return lines;
    }

    private static Component clickLine(PlayerSkills skills, ExpertiseNode n, TalentOutcome outcome, boolean brief) {
        if (outcome == TalentOutcome.OK) {
            return brief ? Component.translatable("proficiency.tree.click.short", n.costPerRank()).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("proficiency.tree.click").withStyle(ChatFormatting.YELLOW);
        }
        return ExpertiseService.explain(skills, n, outcome).copy()
                .withStyle(outcome == TalentOutcome.MAXED ? ChatFormatting.GOLD : ChatFormatting.RED);
    }

    private List<Component> respecTooltip(PlayerSkills skills) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.expertise.respec.title").withStyle(style -> style.withColor(ALL_ACCENT)));
        lines.add(Component.translatable("proficiency.expertise.respec.tip",
                Expertise.respecPrice(skills.expertiseSpent())).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private List<Component> headerTooltip(PlayerSkills skills) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.expertise.title").withStyle(style -> style.withColor(SkillPalette.MAXED)));
        lines.add(Component.translatable("proficiency.expertise.how", Expertise.LEVELS_PER_POINT,
                Expertise.MAX_FROM_LEVELS, Expertise.STARS_PER_POINT, Expertise.MAX_FROM_STARS)
                .withStyle(ChatFormatting.GRAY));
        int levelsAt = Expertise.nextLevelsAt(skills.totalLevels());
        lines.add((levelsAt < 0 ? Component.translatable("proficiency.expertise.next_levels.max")
                : Component.translatable("proficiency.expertise.next_levels", levelsAt - skills.totalLevels()))
                .withStyle(ChatFormatting.DARK_AQUA));
        int starsAt = Expertise.nextStarsAt(skills.totalStars());
        lines.add((starsAt < 0 ? Component.translatable("proficiency.expertise.next_stars.max")
                : Component.translatable("proficiency.expertise.next_stars", starsAt - skills.totalStars()))
                .withStyle(ChatFormatting.DARK_AQUA));
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
            if (inside(px, py, buttonX, buttonY, buttonW, BUTTON_H) && skills.expertiseSpent() > 0) {
                long now = System.currentTimeMillis();
                if (now < confirmUntil) {
                    confirmUntil = 0L;
                    send(new ExpertisePayload(ExpertisePayload.RESPEC, ""));
                } else {
                    confirmUntil = now + CONFIRM_MS;
                }
                click();
                return true;
            }
            for (ExpertiseNode n : Expertise.nodes()) {
                if (!inside(px, py, nodeX(n), nodeY(n), node, node)) {
                    continue;
                }
                int clicks = hasShiftDown() ? n.maxRank() - skills.expertiseRank(n) : 1;
                for (int i = 0; i < Math.max(1, clicks); i++) {
                    send(new ExpertisePayload(ExpertisePayload.INVEST, n.id()));
                }
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void send(ExpertisePayload payload) {
        dev.amman.proficiency.net.ProficiencyNetwork.sendToServer(payload);
    }

    private void click() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
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
