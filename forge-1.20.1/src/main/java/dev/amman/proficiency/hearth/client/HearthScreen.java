package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.client.TooltipDetail;
import dev.amman.proficiency.client.UnlockFx;
import dev.amman.proficiency.client.XpGainDots;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Hearth panel, opened by right-clicking a Hearth (the server answers with a state payload of
 * reason {@code PANEL}). Built on the skills panel's frame: the same panel colours, padding, rows,
 * bars, headers and two-layer tooltips (short, details on Shift).
 *
 * <p>Tab "Hearth": the build (a track of the six stages; the next one's build, offering and the
 * Character it needs), the five pools (tier, bar to the next, the boon now and at the next tier, what
 * holds a pool back), Comfort n/12 with every kind's icon and the missing ones named, Character n/50
 * with the next stage's mark, the defence streak and whether sieges come, and "Show range for 30 s".
 * A stage sealed or a tier risen while it is open plays the talent tree's unlock sweep.
 *
 * <p>Tab "Hearth-keepers": who fed it, this week or over its life, all pools or one (clicking a pool
 * row in the first tab ranks that pool), each with a stacked bar of shares in the pool colours and
 * their top sources.
 */
public final class HearthScreen extends Screen {

    private static final int PANEL_WIDTH = 400;
    private static final int PADDING = 10;
    private static final int COLUMN_GAP = 14;
    /** The left column (build, pools) takes this share of two columns; the right (Comfort, Character, defence) the rest. */
    private static final float LEFT_SHARE = 0.56f;
    private static final int MIN_LEFT = 190;
    private static final int MIN_RIGHT = 150;
    private static final int LINE = 10;
    private static final int ROW = 13;
    /** A pool row: the name line, the bar, then up to two lines of its boon. */
    private static final int POOL_HEAD = 15;
    private static final int BOON_LINE = 9;
    private static final int KEEPER_ROW = 25;
    private static final int BAR = 3;
    private static final int NODE = 16;
    private static final int NODE_GAP = 8;
    private static final int TAB_H = 14;
    /** Most lines of the missing Comfort kinds and of the sieges line; a hover holds the rest. */
    private static final int MISSING_LINES = 3;
    private static final int SIEGES_LINES = 4;
    /** The Character note under its bar ("Character no longer holds the Hearth back") wraps to two lines. */
    private static final int CHARACTER_LINES = 2;
    private static final int HEADER_COLOUR = 0xFFE89A3C;
    private static final int OK = 0xFF8FD46A;

    private enum Tab { HEARTH, KEEPERS }

    private Tab tab = Tab.HEARTH;
    private LedgerView.Window window = LedgerView.Window.WEEK;
    /** -1 for all pools, else the pool the keepers are ranked by. */
    private int poolFilter = -1;
    /** The Hearth this opening of the panel asked the ledger of, when, and what the ledger was then. */
    private long askedLedger = Long.MIN_VALUE;
    private long askedAt;
    @Nullable
    private HearthLedgerPayload askedOver;
    private int asks;

    private int panelX;
    private int panelY;
    private int panelW = PANEL_WIDTH;
    private int panelH;
    private int columns = 2;
    private int columnW;
    /** In two columns the left one is wider: it holds the pools and their boon lines. */
    private int leftW;
    private int rightW;
    /** The Hearth tab's body height: the keepers tab uses the same viewport, so switching never resizes the panel. */
    private int hearthBodyH;
    private int bodyTop;
    private int viewportH;
    private int bodyH;
    private int scroll;

    private Button hearthTab;
    /** The tabs go under the title when both do not fit on one row (a narrow window, a long stage name). */
    private boolean tabsBelow;
    /** The stage the header was laid out for: a stage change can make the title longer. */
    private int headerStage = -1;
    private Button keepersTab;
    private Button rangeButton;
    /** The build guide's toggle and its lane (HearthGhost: ghost blocks of the next stage in the world). */
    private Button guideButton;
    private Button laneButton;
    private boolean guideShown;
    /** Footer rows: the three buttons share one row when they fit, else the range button takes a row of its own. */
    private int footerRows = 1;
    private Button weekButton;
    private Button lifetimeButton;
    private Button poolButton;

    /** Stage track and pool tiers animate like the talent tree's nodes. */
    private final UnlockFx stageFx = new UnlockFx(6, 0);
    private final UnlockFx tierFx = new UnlockFx(HearthPool.COUNT, 0);

    private record Hover(int x, int y, int w, int h, Supplier<List<Component>> lines, int pool) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hover> hovers = new ArrayList<>();

    public HearthScreen() {
        super(Component.translatable("proficiency.hearth.client.panel.title"));
    }

    /** Opens the panel, or keeps the open one (it reads the newest state every frame). */
    static void open() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen instanceof HearthScreen) {
            return;
        }
        if (minecraft.screen == null) {
            minecraft.setScreen(new HearthScreen());
        }
    }

    /** Opens it straight on the keepers tab ranked by {@code pool} (-1 for all); the layout demo uses it. */
    static HearthScreen keepers(int pool, boolean lifetime) {
        HearthScreen screen = new HearthScreen();
        screen.tab = Tab.KEEPERS;
        screen.poolFilter = pool;
        screen.window = lifetime ? LedgerView.Window.LIFETIME : LedgerView.Window.WEEK;
        return screen;
    }

    // ---- Layout -----------------------------------------------------------------------------------

    @Override
    protected void init() {
        if (HearthView.previewing()) {
            tab = Tab.HEARTH;
        }
        int avail = this.width - 8;
        int two = MIN_LEFT + MIN_RIGHT + COLUMN_GAP + PADDING * 2;
        columns = two <= avail ? 2 : 1;
        panelW = columns == 2 ? Math.min(avail, Math.max(PANEL_WIDTH, two))
                : Math.min(avail, Math.max(280, MIN_LEFT + PADDING * 2));
        columnW = columns == 2 ? (panelW - PADDING * 2 - COLUMN_GAP) / 2 : panelW - PADDING * 2 - 4;
        if (columns == 2) {
            int inner = panelW - PADDING * 2 - COLUMN_GAP;
            leftW = Math.max(MIN_LEFT, Math.round(inner * LEFT_SHARE));
            rightW = inner - leftW;
        } else {
            leftW = columnW;
            rightW = columnW;
            HudKit.note("hearth.panel.single_column");
        }
        int keepersW = this.font.width(Component.translatable("proficiency.hearth.client.panel.tab.keepers")) + 12;
        int hearthW = this.font.width(Component.translatable("proficiency.hearth.client.panel.tab.hearth")) + 12;
        HearthStatePayload headerState = HearthView.panelState();
        headerStage = headerState == null ? 0 : headerState.stage();
        int titleW = (HudKit.iconsOn() ? 10 : 0) + this.font.width(titleText(headerState));
        tabsBelow = !HearthView.previewing() && titleW + 6 + hearthW + 4 + keepersW > panelW - PADDING * 2;
        int headerH = PADDING + TAB_H + 6 + (tabsBelow ? LINE + 4 : 0);
        Component rangeLabel = Component.translatable("proficiency.hearth.client.panel.show_range");
        int rangeW = Math.min(panelW - PADDING * 2, this.font.width(rangeLabel) + 20);
        int guideW = Math.max(this.font.width(guideLabel(true)), this.font.width(guideLabel(false))) + 16;
        int laneW = widestLaneLabel() + 16;
        footerRows = rangeW + guideW + laneW + 8 <= panelW - PADDING * 2 ? 1 : 2;
        int footerH = 6 + 20 * footerRows + 4 * (footerRows - 1) + PADDING - 2;
        int room = this.height - 8 - headerH - footerH;
        hearthBodyH = Math.max(hearthHeight(), ROW * 3);
        bodyH = tab == Tab.HEARTH ? hearthBodyH : Math.max(keepersHeight(), ROW * 3);
        viewportH = Math.max(ROW * 4, Math.min(hearthBodyH, room));
        scroll = Math.max(0, Math.min(scroll, bodyH - viewportH));
        panelH = headerH + viewportH + footerH;
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(4, (this.height - panelH) / 2);
        bodyTop = panelY + headerH;

        // The tabs sit on the title row, right-aligned, as the journal's do; the active one is underlined.
        // When the title and the tabs do not fit side by side, the tabs take a row of their own under it.
        int tabRight = panelX + panelW - PADDING;
        int tabY = panelY + PADDING - 3 + (tabsBelow ? LINE + 4 : 0);
        // A plain campfire has no keepers: its panel shows the Hearth tab's page with no tabs.
        keepersTab = HearthView.previewing() ? null : addWidget(Button.builder(Component.translatable("proficiency.hearth.client.panel.tab.keepers"),
                b -> select(Tab.KEEPERS)).bounds(tabRight - keepersW, tabY, keepersW, TAB_H).build());
        hearthTab = HearthView.previewing() ? null : addWidget(Button.builder(Component.translatable("proficiency.hearth.client.panel.tab.hearth"),
                b -> select(Tab.HEARTH)).bounds(tabRight - keepersW - 4 - hearthW, tabY, hearthW, TAB_H)
                .build());

        int footerY = panelY + panelH - PADDING - 18;
        if (tab == Tab.HEARTH) {
            int inner = panelW - PADDING * 2;
            guideW = Math.min(guideW, inner);
            laneW = Math.min(laneW, inner);
            int rangeX;
            int guideX;
            int guideY;
            if (footerRows == 1) {
                int total = rangeW + guideW + laneW + 8;
                rangeX = panelX + (panelW - total) / 2;
                guideX = rangeX + rangeW + 4;
                guideY = footerY;
            } else {
                rangeX = panelX + (panelW - rangeW) / 2;
                guideX = panelX + (panelW - (guideW + laneW + 4)) / 2;
                guideY = footerY - 22;
            }
            rangeButton = addWidget(Button.builder(rangeLabel, b -> showRange())
                    .bounds(rangeX, footerY, rangeW, 18).build());
            HearthStatePayload shown = HearthView.panelState();
            guideShown = shown != null && HearthGhost.showing(shown.pos());
            guideButton = addWidget(Button.builder(guideLabel(guideShown),
                    b -> toggleGuide()).bounds(guideX, guideY, guideW, 18).build());
            laneButton = addWidget(Button.builder(laneLabel(HearthGhost.lane()), b -> cycleLane())
                    .bounds(guideX + guideW + 4, guideY, laneW, 18).build());
        } else {
            Component week = Component.translatable("proficiency.hearth.client.keepers.week");
            Component life = Component.translatable("proficiency.hearth.client.keepers.lifetime");
            int ww = this.font.width(week) + 12;
            int lw = this.font.width(life) + 12;
            int left = panelX + PADDING;
            int inner = panelW - PADDING * 2;
            int pw = Math.max(60, widestPoolLabel() + 12);
            if (ww + 4 + lw + 8 + pw <= inner) {
                weekButton = addWidget(Button.builder(week, b -> setWindow(LedgerView.Window.WEEK))
                        .bounds(left, footerY, ww, 18).build());
                lifetimeButton = addWidget(Button.builder(life, b -> setWindow(LedgerView.Window.LIFETIME))
                        .bounds(left + ww + 4, footerY, lw, 18).build());
            } else {
                // A narrow window: one button that switches between the two windows and names the one shown.
                int tw = Math.max(ww, lw);
                pw = Math.min(pw, inner - tw - 6);
                weekButton = addWidget(Button.builder(window == LedgerView.Window.WEEK ? week : life, b -> {
                    setWindow(window == LedgerView.Window.WEEK ? LedgerView.Window.LIFETIME : LedgerView.Window.WEEK);
                    b.setMessage(window == LedgerView.Window.WEEK ? week : life);
                }).bounds(left, footerY, tw, 18).build());
                lifetimeButton = null;
            }
            Component pool = poolLabel();
            poolButton = addWidget(Button.builder(pool, b -> cyclePool())
                    .bounds(panelX + panelW - PADDING - pw, footerY, Math.max(40, pw), 18).build());
        }
        // The keepers tab's one packet, asked as the panel opens so the tab is ready when clicked.
        askLedger();
    }

    private void select(Tab next) {
        if (tab != next) {
            tab = next;
            scroll = 0;
            rebuildWidgets();
        }
    }

    private void setWindow(LedgerView.Window next) {
        window = next;
        scroll = 0;
    }

    private void cyclePool() {
        poolFilter = poolFilter + 1 >= HearthPool.COUNT ? -1 : poolFilter + 1;
        if (poolButton != null) {
            poolButton.setMessage(poolLabel());
        }
        scroll = 0;
    }

    private Component poolLabel() {
        return poolFilter < 0 ? Component.translatable("proficiency.hearth.client.keepers.all") : HearthText.pool(poolFilter);
    }

    private int widestPoolLabel() {
        int widest = this.font.width(Component.translatable("proficiency.hearth.client.keepers.all"));
        for (HearthPool pool : HearthPool.VALUES) {
            widest = Math.max(widest, this.font.width(HearthText.pool(pool.ordinal())));
        }
        return widest;
    }

    /**
     * Asks the server for the ledger once per opening of the panel (the spec's "synced when the panel
     * opens"; the stages lane's panel open does not send it). A cached ledger of the same Hearth shows
     * until the answer comes.
     */
    private void askLedger() {
        HearthStatePayload state = HearthView.panelState();
        if (state == null || HearthView.previewing() || askedLedger == state.pos()
                || HearthDemo.fakeLedgerAt(state.pos())) {
            return;
        }
        askedLedger = state.pos();
        asks = 0;
        sendLedgerRequest(state.pos());
    }

    /** The server answers at most once a second: no answer in 2.5 s asks again, twice at most. */
    private void retryLedger(long now) {
        HearthStatePayload state = HearthView.panelState();
        if (state == null || askedLedger != state.pos() || HearthView.ledger() != askedOver || asks >= 3
                || now - askedAt < 2500) {
            return;
        }
        sendLedgerRequest(state.pos());
    }

    private void sendLedgerRequest(long pos) {
        askedAt = net.minecraft.Util.getMillis();
        askedOver = HearthView.ledger();
        asks++;
        try {
            ProficiencyNetwork.sendToServer(new HearthRequestPayload(HearthRequestPayload.LEDGER, pos));
        } catch (RuntimeException e) {
            // A server without the channel: the tab says it is still asking.
        }
    }

    private static Component guideLabel(boolean on) {
        return Component.translatable("proficiency.hearth.client.panel.guide." + (on ? "on" : "off"));
    }

    private static Component laneLabel(int lane) {
        var lanes = HearthGuide.lanes();
        return Component.translatable("proficiency.hearth.client.panel.guide.lane",
                HearthGuide.laneName(lanes.get(Math.floorMod(lane, lanes.size())).id()));
    }

    private int widestLaneLabel() {
        int widest = 0;
        for (int i = 0; i < HearthGuide.lanes().size(); i++) {
            widest = Math.max(widest, this.font.width(laneLabel(i)));
        }
        return widest;
    }

    private void toggleGuide() {
        HearthStatePayload state = HearthView.panelState();
        if (state != null && guideButton != null) {
            guideShown = HearthGhost.toggle(state);
            guideButton.setMessage(guideLabel(guideShown));
            laneButton.setMessage(laneLabel(HearthGhost.lane()));
        }
    }

    private void cycleLane() {
        HearthGhost.cycleLane();
        if (laneButton != null) {
            laneButton.setMessage(laneLabel(HearthGhost.lane()));
        }
    }

    private void showRange() {
        HearthStatePayload state = HearthView.panelState();
        if (state == null || this.minecraft == null) {
            return;
        }
        HearthRangeRing.showFor(state.pos(), state.dimension(), net.minecraft.Util.getMillis());
        this.minecraft.setScreen(null);
        this.minecraft.gui.setOverlayMessage(Component.translatable("proficiency.hearth.client.panel.range_shown")
                .withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)), false);
    }

    // ---- Heights of the two tabs ------------------------------------------------------------------

    private int stageHeight(int width) {
        HearthStatePayload state = HearthView.panelState();
        int h = LINE + 4 + NODE + 6;
        if (state == null || state.stage() >= 6) {
            return h + LINE + 4;
        }
        h += LINE;
        h += Math.min(3, HudKit.wrap(this.font, HearthText.stageBuild(state.stage() + 1).getString(), width).size()) * LINE;
        return h + 18 + 4;
    }

    private int poolsHeight(int width) {
        HearthStatePayload state = HearthView.panelState();
        int h = ROW + 2;
        for (HearthPool pool : HearthPool.VALUES) {
            h += poolRowHeight(boonLines(pool.ordinal(), state == null ? null : rowOf(state, pool.ordinal()), width));
        }
        return h;
    }

    private static int poolRowHeight(List<String> boon) {
        return POOL_HEAD + Math.max(1, boon.size()) * BOON_LINE + 5;
    }

    /** The boon a pool gives now, or what tier I would give, wrapped to at most two lines. */
    private List<String> boonLines(int pool, @Nullable PoolRow row, int width) {
        int tier = row == null ? 0 : row.tier();
        Component boon = HearthText.boonLine(pool, tier, false);
        HearthStatePayload state = HearthView.panelState();
        String text;
        if (state != null && state.smouldering()) {
            text = Component.translatable("proficiency.hearth.client.panel.off_smoulder").getString();
        } else if (boon != null) {
            text = boon.getString();
        } else {
            Component first = HearthText.boonLine(pool, 0, true);
            text = first == null ? Component.translatable("proficiency.hearth.client.boon.none").getString()
                    : Component.translatable("proficiency.hearth.client.panel.at_first", first).getString();
        }
        boolean[] cut = new boolean[1];
        List<String> lines = HudKit.wrap(this.font, text, width, 2, cut);
        if (cut[0]) {
            HudKit.note("hearth.panel.boon");
        }
        return lines;
    }

    private int comfortHeight(int width) {
        int perRow = Math.max(1, (width + 2) / (NODE + 2));
        int kinds = HearthView.comfortAll().size();
        int rows = (kinds + perRow - 1) / perRow;
        return ROW + rows * (NODE + 2) + 2 + LINE * Math.min(MISSING_LINES, missingLines(width).size()) + 6;
    }

    private int characterHeight(int width) {
        HearthStatePayload state = HearthView.panelState();
        int lines = state == null ? 1 : characterLines(state, width, null).size();
        return ROW + BAR + 4 + LINE * Math.max(1, lines) + 6;
    }

    /** The note under the Character bar: what the next stage needs, or that Character holds nothing back. */
    private String characterNote(HearthStatePayload state) {
        int need = state.characterNext();
        return need > 0
                ? Component.translatable("proficiency.hearth.client.panel.character_next",
                        HearthText.roman(Math.min(6, state.ceiling() + 1)), need).getString()
                : Component.translatable("proficiency.hearth.client.panel.character_done").getString();
    }

    private List<String> characterLines(HearthStatePayload state, int width, @Nullable boolean[] cut) {
        return HudKit.wrap(this.font, characterNote(state), width, CHARACTER_LINES, cut == null ? new boolean[1] : cut);
    }

    private int defenceHeight(int width) {
        return ROW + Math.min(SIEGES_LINES, HudKit.wrap(this.font, siegesLine().getString(), width).size()) * LINE + 4;
    }

    private int smoulderHeight(int width) {
        HearthStatePayload state = HearthView.panelState();
        if (state == null || !state.smouldering()) {
            return 0;
        }
        return Math.min(2, HudKit.wrap(this.font, Component.translatable("proficiency.hearth.client.panel.smouldering")
                .getString(), width).size()) * LINE + 4;
    }

    private int hearthHeight() {
        int full = columns == 2 ? leftW + rightW + COLUMN_GAP : columnW;
        int left = stageHeight(leftW) + poolsHeight(leftW);
        int right = comfortHeight(rightW) + characterHeight(rightW) + defenceHeight(rightW);
        return smoulderHeight(full) + (columns == 2 ? Math.max(left, right) : left + right);
    }

    private int keepersHeight() {
        HearthLedgerPayload ledger = currentLedger();
        int rows = ledger == null ? 1 : Math.max(1, LedgerView.rank(entries(ledger), window, poolFilter).size());
        return ROW + rows * KEEPER_ROW;
    }

    @Nullable
    private HearthLedgerPayload currentLedger() {
        HearthStatePayload state = HearthView.panelState();
        HearthLedgerPayload ledger = HearthView.ledger();
        return state != null && ledger != null && ledger.pos() == state.pos() ? ledger : null;
    }

    // ---- Rendering --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: it paints the widgets, and the panel goes under them. 1.20.1's Screen.render
        // does not draw the background itself (no menu blur): the dark gradient comes first.
        renderBackground(graphics);
        HearthStatePayload state = HearthView.panelState();
        int wanted = Math.max(tab == Tab.HEARTH ? hearthHeight() : keepersHeight(), ROW * 3);
        if (wanted != bodyH || Math.max(hearthHeight(), ROW * 3) != hearthBodyH
                || (state == null ? 0 : state.stage()) != headerStage) {
            rebuildWidgets();
        }
        hovers.clear();
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, SkillPalette.PANEL);
        border(graphics, panelX, panelY, panelW, panelH, SkillPalette.PANEL_BORDER);
        drawTitle(graphics, state);
        drawTab(graphics, hearthTab, tab == Tab.HEARTH, mouseX, mouseY, partialTick);
        drawTab(graphics, keepersTab, tab == Tab.KEEPERS, mouseX, mouseY, partialTick);

        long now = System.currentTimeMillis();
        graphics.enableScissor(panelX, bodyTop, panelX + panelW, bodyTop + viewportH);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        int my = mouseY + scroll;
        boolean inBody = mouseY >= bodyTop && mouseY < bodyTop + viewportH;
        if (state == null) {
            graphics.drawString(this.font, Component.translatable("proficiency.hearth.client.panel.none"),
                    panelX + PADDING, bodyTop, SkillPalette.TEXT_DIM, false);
        } else if (tab == Tab.HEARTH) {
            drawHearth(graphics, state, now);
        } else {
            drawKeepers(graphics, state);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        if (bodyH > viewportH) {
            int thumb = Math.max(8, viewportH * viewportH / bodyH);
            int travel = viewportH - thumb;
            int thumbTop = bodyTop + (int) (travel * (scroll / (float) (bodyH - viewportH)));
            graphics.fill(panelX + panelW - 5, bodyTop, panelX + panelW - 3, bodyTop + viewportH, SkillPalette.TRACK);
            graphics.fill(panelX + panelW - 5, thumbTop, panelX + panelW - 3, thumbTop + thumb, SkillPalette.TEXT);
        }
        // The footer: a rule, then the button(s).
        int footerY = panelY + panelH - PADDING - 18;
        int ruleY = footerY - 22 * (footerRows - 1) - 5;
        graphics.fill(panelX + PADDING, ruleY, panelX + panelW - PADDING, ruleY + 1, SkillPalette.PANEL_BORDER);
        if (tab == Tab.HEARTH && rangeButton != null) {
            rangeButton.active = state != null;
            rangeButton.render(graphics, mouseX, mouseY, partialTick);
            guideButton.active = state != null;
            laneButton.active = state != null;
            boolean guideOn = state != null && HearthGhost.showing(state.pos());
            if (guideOn != guideShown) {
                // The guide switched itself off (out of range, the Hearth gone): the text follows.
                guideShown = guideOn;
                guideButton.setMessage(guideLabel(guideOn));
            }
            guideButton.render(graphics, mouseX, mouseY, partialTick);
            laneButton.render(graphics, mouseX, mouseY, partialTick);
            if (state != null && HearthGhost.showing(state.pos())) {
                // On: an ember bar under the toggle, as the active tab has.
                graphics.fill(guideButton.getX(), guideButton.getY() + guideButton.getHeight(),
                        guideButton.getX() + guideButton.getWidth(), guideButton.getY() + guideButton.getHeight() + 2,
                        HearthPalette.EMBER);
            }
        } else if (tab == Tab.KEEPERS) {
            // One toggle in a narrow window: it always names the window shown, so it is always the active one.
            drawTab(graphics, weekButton, window == LedgerView.Window.WEEK || lifetimeButton == null, mouseX, mouseY,
                    partialTick);
            drawTab(graphics, lifetimeButton, window == LedgerView.Window.LIFETIME, mouseX, mouseY, partialTick);
            if (poolButton != null) {
                poolButton.render(graphics, mouseX, mouseY, partialTick);
                if (poolFilter >= 0) {
                    graphics.fill(poolButton.getX(), poolButton.getY() + poolButton.getHeight(),
                            poolButton.getX() + poolButton.getWidth(), poolButton.getY() + poolButton.getHeight() + 2,
                            HearthPalette.pool(poolFilter));
                }
            }
        }
        if (inBody) {
            for (int i = hovers.size() - 1; i >= 0; i--) {
                Hover hover = hovers.get(i);
                if (hover.contains(mouseX, my)) {
                    List<Component> lines = hover.lines().get();
                    if (!lines.isEmpty()) {
                        HudKit.tooltip(graphics, this.font, lines, mouseX, mouseY, Math.min(300, this.width - 16));
                    }
                    break;
                }
            }
        }
    }

    private void drawTab(GuiGraphics graphics, @Nullable Button button, boolean active, int mouseX, int mouseY,
            float partialTick) {
        if (button == null) {
            return;
        }
        button.render(graphics, mouseX, mouseY, partialTick);
        if (active) {
            graphics.fill(button.getX(), button.getY() + button.getHeight(), button.getX() + button.getWidth(),
                    button.getY() + button.getHeight() + 2, HearthPalette.EMBER);
        }
    }

    private void drawTitle(GuiGraphics graphics, @Nullable HearthStatePayload state) {
        int left = panelX + PADDING;
        int limit = (hearthTab == null || tabsBelow ? panelX + panelW - PADDING : hearthTab.getX() - 6) - left;
        int y = panelY + PADDING;
        int stage = state == null ? 0 : state.stage();
        int icon = 0;
        if (HudKit.iconsOn() && stage >= 1) {
            icon = 10;
            HudKit.icon(graphics, stageIcon(stage), left, y - 1, 8, 255);
        }
        String title = titleText(state);
        HudKit.draw(graphics, this.font, "hearth.panel.title", title, left + icon, y, limit - icon, SkillPalette.TEXT,
                false);
    }

    /** "Stage IV · Hall", or the panel's name before any state came. */
    private String titleText(@Nullable HearthStatePayload state) {
        if (state != null && state.reason() == HearthStatePayload.CAMPFIRE) {
            return Component.translatable("proficiency.hearth.client.panel.campfire").getString();
        }
        return state == null ? this.title.getString()
                : Component.translatable("proficiency.hearth.client.panel.heading", HearthText.roman(state.stage()),
                        HearthText.stageName(state.stage())).getString();
    }

    static ResourceLocation stageIcon(int stage) {
        return TrackerHud.icon("stage/" + Math.max(1, Math.min(6, stage)));
    }

    // ---- Tab "Hearth" -------------------------------------------------------------------------------

    private void drawHearth(GuiGraphics graphics, HearthStatePayload state, long now) {
        boolean fx = ProficiencyClientConfig.screensUnlockFx();
        for (int i = 0; i < 6; i++) {
            int sealed = state.stage() > i ? 1 : 0;
            if (fx) {
                stageFx.observe(i, sealed, 1, now);
            } else {
                stageFx.snap(i, sealed);
            }
        }
        for (PoolRow row : state.pools()) {
            if (row.pool() >= 0 && row.pool() < HearthPool.COUNT) {
                if (fx) {
                    tierFx.observe(row.pool(), row.tier(), 6, now);
                } else {
                    tierFx.snap(row.pool(), row.tier());
                }
            }
        }
        int full = columns == 2 ? leftW + rightW + COLUMN_GAP : columnW;
        int left = panelX + PADDING;
        int y = bodyTop;
        if (state.smouldering()) {
            for (String line : limit(HudKit.wrap(this.font,
                    Component.translatable("proficiency.hearth.client.panel.smouldering").getString(), full), 2)) {
                graphics.drawString(this.font, line, left, y, 0xFFE0584B, false);
                y += LINE;
            }
            y += 4;
        }
        int top = y;
        y = drawStage(graphics, state, left, y, leftW, now, fx);
        y = drawPools(graphics, state, left, y, leftW, now, fx);
        int rx = columns == 2 ? left + leftW + COLUMN_GAP : left;
        int ry = columns == 2 ? top : y;
        ry = drawComfort(graphics, state, rx, ry, rightW);
        ry = drawCharacter(graphics, state, rx, ry, rightW);
        drawDefence(graphics, state, rx, ry, rightW);
    }

    private static List<String> limit(List<String> lines, int max) {
        if (lines.size() <= max) {
            return lines;
        }
        List<String> out = new ArrayList<>(lines.subList(0, max));
        out.set(max - 1, out.get(max - 1) + "…");
        return out;
    }

    private void header(GuiGraphics graphics, String site, String text, int x, int y, int width) {
        HudKit.draw(graphics, this.font, site, text, x, y + 2, width, HEADER_COLOUR, false);
    }

    /** The build: a track of six stage nodes, then the next stage's build, offering and Character. */
    private int drawStage(GuiGraphics graphics, HearthStatePayload state, int x, int y, int width, long now, boolean fx) {
        int stage = state.stage();
        int ceiling = state.ceiling();
        String ceilingText = ceiling < stage
                ? Component.translatable("proficiency.hearth.client.panel.ceiling_held", HearthText.roman(ceiling)).getString()
                : Component.translatable("proficiency.hearth.client.panel.ceiling", HearthText.roman(ceiling)).getString();
        header(graphics, "hearth.panel.build", Component.translatable("proficiency.hearth.client.panel.build").getString(),
                x, y - 2, width - this.font.width(ceilingText) - 6);
        int cw = this.font.width(ceilingText);
        graphics.drawString(this.font, ceilingText, x + width - cw, y, ceiling < stage ? HearthPalette.LOCKED : SkillPalette.TEXT_DIM,
                false);
        hovers.add(new Hover(x + width - cw, y - 1, cw, LINE, () -> ceilingTip(state), -1));
        y += LINE + 4;
        // Six nodes, the talent tree's way: filled when sealed, a sweep and a travelling line when one seals.
        int gap = Math.max(2, Math.min(NODE_GAP, (width - 6 * NODE) / 5));
        for (int i = 0; i < 6; i++) {
            int nx = x + i * (NODE + gap);
            boolean shownSealed = fx ? stageFx.shownRank(i, now) >= 1 : stage > i;
            if (i < 5) {
                // The way on is open once this stage is sealed; the light runs along it as it seals.
                int lx = nx + NODE;
                int lw = gap;
                boolean pending = fx && stageFx.linesPending(i, now);
                boolean lit = stage > i && !pending;
                graphics.fill(lx, y + NODE / 2, lx + lw, y + NODE / 2 + 1, lit ? HearthPalette.EMBER : SkillPalette.TRACK);
                float travel = fx ? stageFx.travel(i, now) : -1f;
                if (travel >= 0f) {
                    int len = Math.round((float) XpGainDots.ease(travel) * lw);
                    graphics.fill(lx, y + NODE / 2, lx + len, y + NODE / 2 + 1, HearthPalette.EMBER);
                    graphics.fill(lx + len - 1, y + NODE / 2 - 1, lx + len + 2, y + NODE / 2 + 2, 0xFFFFFFFF);
                }
            }
            int fill = shownSealed ? (HearthPalette.EMBER & 0xFFFFFF) | 0x66000000 : 0xFF181B20;
            int edge = shownSealed ? (i + 1 == 6 ? HearthPalette.GOLD : HearthPalette.EMBER)
                    : i == stage ? SkillPalette.TEXT_DIM : SkillPalette.TRACK;
            graphics.fill(nx, y, nx + NODE, y + NODE, fill);
            border(graphics, nx - 1, y - 1, NODE + 2, NODE + 2, edge);
            if (i + 1 > ceiling && i < stage) {
                // Built but above the ceiling: a lock corner says Character holds it.
                graphics.fill(nx + NODE - 4, y + 1, nx + NODE - 1, y + 4, HearthPalette.LOCKED);
            }
            if (HudKit.iconsOn()) {
                HudKit.icon(graphics, stageIcon(i + 1), nx, y, NODE, shownSealed ? 255 : 90);
            } else {
                graphics.drawCenteredString(this.font, HearthText.roman(i + 1), nx + NODE / 2 + 1, y + 4,
                        shownSealed ? SkillPalette.MAXED : SkillPalette.TEXT_DIM);
            }
            float sweep = fx ? stageFx.sweep(i, now) : -1f;
            if (sweep >= 0f) {
                int height = Math.round((float) XpGainDots.ease(sweep) * NODE);
                graphics.fill(nx, y + NODE - height, nx + NODE, y + NODE, (HearthPalette.EMBER & 0xFFFFFF) | 0x90000000);
                if (height > 0 && height < NODE) {
                    graphics.fill(nx, y + NODE - height, nx + NODE, y + NODE - height + 1, 0xFFFFFFFF);
                }
            }
            final int stageIndex = i + 1;
            hovers.add(new Hover(nx - 1, y - 1, NODE + 2, NODE + 2, () -> stageTip(state, stageIndex), -1));
        }
        y += NODE + 6;
        if (stage >= 6) {
            graphics.drawString(this.font, Component.translatable("proficiency.hearth.client.panel.complete"), x, y,
                    SkillPalette.MAXED, false);
            return y + LINE + 4;
        }
        int next = stage + 1;
        String nextText = Component.translatable("proficiency.hearth.client.panel.next", HearthText.roman(next),
                HearthText.stageName(next)).getString();
        HudKit.draw(graphics, this.font, "hearth.panel.next", nextText, x, y, width, SkillPalette.TEXT, false);
        y += LINE;
        List<String> build = HudKit.wrap(this.font, HearthText.stageBuild(next).getString(), width);
        int buildTop = y;
        for (String line : limit(build, 3)) {
            graphics.drawString(this.font, line, x, y, SkillPalette.TEXT_DIM, false);
            y += LINE;
        }
        if (build.size() > 3) {
            HudKit.note("hearth.panel.build");
            hovers.add(new Hover(x, buildTop, width, y - buildTop, () -> List.of(HearthText.stageBuild(next).copy()
                    .withStyle(ChatFormatting.GRAY)), -1));
        }
        // The offering, as the item, then the Character the stage wants.
        ItemStack offering = offeringStack();
        int cursor = x;
        if (!offering.isEmpty()) {
            graphics.renderItem(offering, cursor, y);
            Component name = offering.getHoverName();
            hovers.add(new Hover(cursor, y, 16, 16, () -> List.of(
                    Component.translatable("proficiency.hearth.client.panel.offering", name)), -1));
            cursor += 18;
        }
        int need = state.characterNext();
        if (need > 0) {
            boolean met = state.character() >= need;
            String text = Component.translatable("proficiency.hearth.stages.character", state.character(), need)
                    .getString();
            int room = x + width - cursor;
            String shown = HudKit.clip(this.font, text, room);
            if (!shown.equals(text)) {
                HudKit.note("hearth.panel.character_need");
            }
            graphics.drawString(this.font, shown, cursor, y + 4, met ? OK : HearthPalette.LOCKED, false);
        }
        return y + 18 + 4;
    }

    private ItemStack offeringStack() {
        String id = HearthView.nextOffering();
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private List<Component> stageTip(HearthStatePayload state, int stage) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.hearth.client.panel.heading", HearthText.roman(stage),
                HearthText.stageName(stage)).withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)));
        lines.add(HearthText.stageBuild(stage).copy().withStyle(ChatFormatting.GRAY));
        if (state.stage() >= stage) {
            lines.add(Component.translatable("proficiency.hearth.client.panel.sealed").withStyle(ChatFormatting.GREEN));
            if (stage > state.ceiling()) {
                lines.add(Component.translatable("proficiency.hearth.client.panel.held_by_character")
                        .withStyle(style -> style.withColor(HearthPalette.LOCKED & 0xFFFFFF)));
            }
        }
        return lines;
    }

    private List<Component> ceilingTip(HearthStatePayload state) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.hearth.client.panel.ceiling", HearthText.roman(state.ceiling()))
                .withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)));
        lines.add(HearthText.dim("panel.ceiling.tip"));
        if (state.ceiling() < state.stage()) {
            lines.add(Component.translatable("proficiency.hearth.client.panel.held_by_character")
                    .withStyle(style -> style.withColor(HearthPalette.LOCKED & 0xFFFFFF)));
        }
        return lines;
    }

    private int drawPools(GuiGraphics graphics, HearthStatePayload state, int x, int y, int width, long now, boolean fx) {
        header(graphics, "hearth.panel.pools", Component.translatable("proficiency.hearth.client.panel.pools").getString(),
                x, y, width);
        y += ROW;
        double[] thresholds = HearthView.thresholds();
        int maxTier = thresholds.length;
        for (HearthPool pool : HearthPool.VALUES) {
            PoolRow row = rowOf(state, pool.ordinal());
            List<String> boon = boonLines(pool.ordinal(), row, width);
            drawPool(graphics, pool.ordinal(), row, boon, x, y, width, maxTier, thresholds, now, fx);
            y += poolRowHeight(boon);
        }
        return y + 2;
    }

    @Nullable
    private static PoolRow rowOf(HearthStatePayload state, int pool) {
        for (PoolRow row : state.pools()) {
            if (row.pool() == pool) {
                return row;
            }
        }
        return null;
    }

    private void drawPool(GuiGraphics graphics, int pool, @Nullable PoolRow row, List<String> boonLines, int x, int y,
            int width, int maxTier, double[] thresholds, long now, boolean fx) {
        int colour = HearthPalette.pool(pool);
        int tier = row == null ? 0 : row.tier();
        boolean active = row != null && row.charge() > 0;
        // The tier box sits right, a small node that sweeps when the tier rises.
        int boxW = Math.max(14, this.font.width("III") + 4);
        int boxX = x + width - boxW;
        int shownTier = fx ? Math.min(tier, tierFx.shownRank(pool, now)) : tier;
        int icon = HudKit.iconsOn() ? 10 : 0;
        if (icon > 0) {
            HudKit.icon(graphics, TrackerHud.poolIcon(pool), x, y - 1, 8, active ? 255 : 120);
        }
        // What holds it back, between the name and the box (nothing while it smoulders: everything is off).
        HearthStatePayload current = HearthView.panelState();
        boolean smoulders = current != null && current.smouldering();
        TrackerHud.Need need = row == null || smoulders ? null
                : TrackerHud.need(row.lock(), row.need(), current, HudKit.iconsOn());
        int needW = need == null ? 0 : need.width(this.font) + 4;
        String name = HearthText.pool(pool).getString();
        int nameRoom = boxX - 4 - needW - (x + icon);
        String shownName = HudKit.clip(this.font, name, Math.max(10, nameRoom));
        if (!shownName.equals(name)) {
            HudKit.note("hearth.panel.pool_name");
        }
        graphics.drawString(this.font, shownName, x + icon, y, active ? SkillPalette.TEXT : SkillPalette.TEXT_DIM, false);
        if (need != null) {
            int nx = boxX - 4 - needW;
            TrackerHud.drawNeed(graphics, this.font, need, nx, y, needW, 255, false, "hearth.panel.need");
        }
        boolean top = shownTier >= maxTier;
        int boxFill = shownTier > 0 ? (colour & 0xFFFFFF) | (top ? 0x88000000 : 0x44000000) : 0xFF181B20;
        graphics.fill(boxX, y - 2, boxX + boxW, y + 9, boxFill);
        border(graphics, boxX, y - 2, boxW, 11, top ? HearthPalette.GOLD : shownTier > 0 ? colour : SkillPalette.TRACK);
        float sweep = fx ? tierFx.sweep(pool, now) : -1f;
        if (sweep >= 0f) {
            int h = Math.round((float) XpGainDots.ease(sweep) * 11);
            graphics.fill(boxX + 1, y + 9 - h, boxX + boxW - 1, y + 8, (colour & 0xFFFFFF) | 0xB0000000);
            if (h > 0 && h < 11) {
                graphics.fill(boxX + 1, y + 9 - h, boxX + boxW - 1, y + 10 - h, 0xFFFFFFFF);
            }
        }
        graphics.drawCenteredString(this.font, HearthText.tierMark(shownTier), boxX + boxW / 2 + 1, y,
                top ? SkillPalette.MAXED : shownTier > 0 ? SkillPalette.TEXT : SkillPalette.TEXT_DIM);
        // The bar to the next charge tier, under the name, the skills panel's way.
        int barTop = y + 10;
        int barW = boxX - 4 - x;
        graphics.fill(x, barTop, x + barW, barTop + BAR, SkillPalette.TRACK);
        double value = row == null ? 0 : HearthTrackerModel.value(row.charge(), thresholds);
        double fill = value >= maxTier ? 1 : value - Math.floor(value);
        int filled = (int) Math.round(barW * fill);
        if (row != null && row.charge() > 0 && filled < 1) {
            filled = 1;
        }
        int barColour = value >= maxTier ? HearthPalette.GOLD : row != null && row.lock() != 0
                ? HudKit.lerp(colour & 0xFFFFFF, 0x707070, 0.45f) | 0xFF000000 : colour;
        if (filled > 0) {
            graphics.fill(x, barTop, x + filled, barTop + BAR, barColour);
        }
        // The boon it gives now, in its colour; or, dim, what the first tier would give.
        int boonY = barTop + BAR + 2;
        boolean giving = HearthText.boonLine(pool, tier, false) != null;
        for (String line : boonLines) {
            graphics.drawString(this.font, line, x, boonY, giving ? colour : SkillPalette.TEXT_DIM, false);
            boonY += BOON_LINE;
        }
        hovers.add(new Hover(x - 2, y - 2, width + 4, poolRowHeight(boonLines) - 1, () -> poolTip(pool, row, thresholds),
                pool));
    }

    private List<Component> poolTip(int pool, @Nullable PoolRow row, double[] thresholds) {
        List<Component> lines = new ArrayList<>();
        int tier = row == null ? 0 : row.tier();
        lines.add(HearthText.pool(pool).copy().withStyle(style -> style.withColor(HearthPalette.pool(pool) & 0xFFFFFF))
                .append(Component.literal(tier > 0 ? "  " + HearthText.roman(tier) : "").withStyle(ChatFormatting.WHITE)));
        double charge = row == null ? 0 : row.charge();
        int chargeTier = row == null ? 0 : row.chargeTier();
        if (chargeTier < thresholds.length) {
            lines.add(Component.translatable("proficiency.hearth.client.panel.charge", Math.round(charge),
                    Math.round(thresholds[chargeTier]), HearthText.roman(chargeTier + 1)).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("proficiency.hearth.client.panel.charge_top", Math.round(charge))
                    .withStyle(ChatFormatting.GOLD));
        }
        Component now = HearthText.boonLine(pool, tier, false);
        if (now != null) {
            lines.add(now);
        }
        HearthStatePayload current = HearthView.panelState();
        Component held = row == null || (current != null && current.smouldering()) ? null
                : HearthText.held(row.lock(), row.need(), tier);
        if (held != null) {
            lines.add(held.copy().withStyle(style -> style.withColor(HearthPalette.LOCKED & 0xFFFFFF)));
        }
        if (!TooltipDetail.detailed()) {
            return TooltipDetail.withHint(lines, true);
        }
        if (tier < thresholds.length) {
            Component next = HearthText.boonLine(pool, tier, true);
            if (next != null) {
                lines.add(Component.translatable("proficiency.hearth.client.boon.next", next).withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(HearthText.dim("panel.feeds." + (HearthPool.byIndex(pool) == null ? "hearty" : HearthPool.byIndex(pool).id())));
        lines.add(HearthText.dim("panel.pool.click"));
        return lines;
    }

    /** A Comfort kind's tooltip: its name, whether the home has it, what counts as it. */
    private static List<Component> kindTip(String kind, boolean has) {
        List<Component> lines = new ArrayList<>();
        lines.add(HearthText.kind(kind).copy().withStyle(has ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        lines.add(Component.translatable(has ? "proficiency.hearth.client.panel.kind.has"
                : "proficiency.hearth.client.panel.kind.missing").withStyle(has ? ChatFormatting.GREEN
                : ChatFormatting.DARK_GRAY));
        Component desc = HearthText.kindDesc(kind);
        if (desc != null) {
            lines.add(desc.copy().withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private List<String> missingLines(int width) {
        HearthStatePayload state = HearthView.panelState();
        if (state == null) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String kind : HearthView.comfortAll()) {
            if (!state.comfortKinds().contains(kind)) {
                missing.add(HearthText.kind(kind).getString());
            }
        }
        String text = missing.isEmpty() ? Component.translatable("proficiency.hearth.client.panel.comfort_all").getString()
                : Component.translatable("proficiency.hearth.client.panel.missing", String.join(", ", missing)).getString();
        return HudKit.wrap(this.font, text, width);
    }

    private int drawComfort(GuiGraphics graphics, HearthStatePayload state, int x, int y, int width) {
        List<String> all = HearthView.comfortAll();
        boolean full = state.comfort() >= all.size();
        int icon = HudKit.iconsOn() ? 10 : 0;
        if (icon > 0) {
            HudKit.icon(graphics, TrackerHud.COMFORT_ICON, x, y + 1, 8, 255);
        }
        String head = HearthText.comfort(state.comfort(), all.size()).getString();
        HudKit.draw(graphics, this.font, "hearth.panel.comfort", head, x + icon, y + 2, width - icon,
                full ? SkillPalette.MAXED : HEADER_COLOUR, false);
        hovers.add(new Hover(x, y, width, ROW, this::comfortTip, -1));
        y += ROW;
        int perRow = Math.max(1, (width + 2) / (NODE + 2));
        int i = 0;
        for (String kind : all) {
            int cx = x + (i % perRow) * (NODE + 2);
            int cy = y + (i / perRow) * (NODE + 2);
            boolean has = state.comfortKinds().contains(kind);
            graphics.fill(cx, cy, cx + NODE, cy + NODE, has ? 0x3326A69A : 0xFF181B20);
            if (HudKit.iconsOn()) {
                HudKit.icon(graphics, TrackerHud.icon("comfort/" + kind), cx, cy, NODE, has ? 255 : 55);
            } else {
                graphics.drawCenteredString(this.font, kind.substring(0, 1).toUpperCase(java.util.Locale.ROOT), cx + 8,
                        cy + 4, has ? SkillPalette.TEXT : SkillPalette.TEXT_DIM);
            }
            if (!has) {
                border(graphics, cx, cy, NODE, NODE, SkillPalette.TRACK);
            }
            hovers.add(new Hover(cx, cy, NODE, NODE, () -> kindTip(kind, has), -1));
            i++;
        }
        y += ((all.size() + perRow - 1) / perRow) * (NODE + 2) + 2;
        List<String> missing = missingLines(width);
        int missingTop = y;
        for (String line : limit(missing, MISSING_LINES)) {
            graphics.drawString(this.font, line, x, y, full ? SkillPalette.MAXED : SkillPalette.TEXT_DIM, false);
            y += LINE;
        }
        if (missing.size() > MISSING_LINES) {
            // Cut: the hover names every missing kind.
            HudKit.note("hearth.panel.missing");
            String whole = String.join(" ", missing);
            hovers.add(new Hover(x, missingTop, width, y - missingTop, () -> List.of(Component.literal(whole)
                    .withStyle(ChatFormatting.GRAY)), -1));
        }
        return y + 6;
    }

    private List<Component> comfortTip() {
        HearthStatePayload state = HearthView.panelState();
        List<Component> lines = new ArrayList<>();
        if (state == null) {
            return lines;
        }
        lines.add(HearthText.comfort(state.comfort(), HearthView.comfortAll().size()).copy()
                .withStyle(style -> style.withColor(HEADER_COLOUR & 0xFFFFFF)));
        lines.add(HearthText.dim("panel.comfort.tip"));
        if (TooltipDetail.detailed()) {
            lines.add(HearthText.dim("panel.comfort.gates"));
        } else {
            TooltipDetail.withHint(lines, true);
        }
        return lines;
    }

    private int drawCharacter(GuiGraphics graphics, HearthStatePayload state, int x, int y, int width) {
        int max = HearthView.characterMax();
        int character = state.character();
        boolean full = character >= max;
        int icon = HudKit.iconsOn() ? 10 : 0;
        if (icon > 0) {
            HudKit.icon(graphics, TrackerHud.CHARACTER_ICON, x, y + 1, 8, 255);
        }
        HudKit.draw(graphics, this.font, "hearth.panel.character", HearthText.character(character, max).getString(),
                x + icon, y + 2, width - icon, full ? SkillPalette.MAXED : HEADER_COLOUR, false);
        boolean[] cut = new boolean[1];
        List<String> note = characterLines(state, width, cut);
        boolean noteCut = cut[0];
        hovers.add(new Hover(x, y, width, ROW + BAR + 4 + LINE * note.size(), () -> {
            List<Component> tip = characterTip(state);
            if (noteCut) {
                // The note did not fit its two lines: the hover has it whole.
                tip.add(1, Component.literal(characterNote(state)).withStyle(ChatFormatting.GRAY));
            }
            return tip;
        }, -1));
        y += ROW;
        graphics.fill(x, y, x + width, y + BAR, SkillPalette.TRACK);
        int filled = (int) Math.round(width * Math.min(1.0, character / (double) Math.max(1, max)));
        if (character > 0 && filled < 1) {
            filled = 1;
        }
        int accent = SkillPalette.accent(dev.amman.proficiency.skill.SkillCategory.CONSTRUCTION);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR, full ? SkillPalette.MAXED : accent);
        }
        // The next stage's mark on the bar.
        int need = state.characterNext();
        if (need > 0 && need <= max) {
            int mx = x + (int) Math.round(width * need / (double) max);
            graphics.fill(Math.min(x + width - 1, mx), y - 2, Math.min(x + width, mx + 1), y + BAR + 2,
                    character >= need ? OK : HearthPalette.LOCKED);
        }
        y += BAR + 4;
        if (cut[0]) {
            HudKit.note("hearth.panel.character_line");
        }
        for (String line : note) {
            graphics.drawString(this.font, line, x, y, SkillPalette.TEXT_DIM, false);
            y += LINE;
        }
        return y + 6;
    }

    private List<Component> characterTip(HearthStatePayload state) {
        List<Component> lines = new ArrayList<>();
        lines.add(HearthText.character(state.character(), HearthView.characterMax()).copy()
                .withStyle(style -> style.withColor(HEADER_COLOUR & 0xFFFFFF)));
        lines.add(HearthText.dim("panel.character.tip"));
        if (TooltipDetail.detailed()) {
            lines.add(HearthText.dim("panel.character.gates"));
            return lines;
        }
        return TooltipDetail.withHint(lines, true);
    }

    private Component siegesLine() {
        HearthStatePayload state = HearthView.panelState();
        if (state == null) {
            return Component.empty();
        }
        SiegeRules.Phase phase = SiegeRules.Phase.byOrdinal(state.siegePhase());
        if (phase == SiegeRules.Phase.ACTIVE) {
            return Component.translatable("proficiency.hearth.client.panel.siege_active");
        }
        if (phase == SiegeRules.Phase.COUNTDOWN) {
            return Component.translatable("proficiency.hearth.client.panel.siege_due");
        }
        if (!state.siegesOn()) {
            return Component.translatable("proficiency.hearth.client.panel.sieges_off");
        }
        if (state.playHoursSinceSiege() < 0) {
            return Component.translatable("proficiency.hearth.client.panel.sieges_on_never");
        }
        if (state.playHoursSinceSiege() < 1f) {
            // "last one 0 h of play ago" read as a glitch right after a siege.
            return Component.translatable("proficiency.hearth.client.panel.sieges_on_recent");
        }
        return Component.translatable("proficiency.hearth.client.panel.sieges_on",
                Math.round(state.playHoursSinceSiege()));
    }

    private int drawDefence(GuiGraphics graphics, HearthStatePayload state, int x, int y, int width) {
        int icon = HudKit.iconsOn() ? 10 : 0;
        if (icon > 0) {
            HudKit.icon(graphics, TrackerHud.icon("trophy"), x, y + 1, 8, 255);
        }
        int streak = state.defenceStreak();
        HudKit.draw(graphics, this.font, "hearth.panel.defence",
                Component.translatable("proficiency.hearth.client.panel.defence", streak).getString(), x + icon, y + 2,
                width - icon, streak > 0 ? HEADER_COLOUR : SkillPalette.TEXT_DIM, false);
        hovers.add(new Hover(x, y, width, ROW, () -> List.of(
                Component.translatable("proficiency.hearth.client.panel.defence", streak)
                        .withStyle(style -> style.withColor(HEADER_COLOUR & 0xFFFFFF)),
                HearthText.dim("panel.defence.tip")), -1));
        y += ROW;
        SiegeRules.Phase phase = SiegeRules.Phase.byOrdinal(state.siegePhase());
        int colour = phase == SiegeRules.Phase.IDLE ? SkillPalette.TEXT_DIM : HearthPalette.SIEGE;
        List<String> siegeLines = HudKit.wrap(this.font, siegesLine().getString(), width);
        int linesTop = y;
        for (String line : limit(siegeLines, SIEGES_LINES)) {
            graphics.drawString(this.font, line, x, y, colour, false);
            y += LINE;
        }
        if (siegeLines.size() > SIEGES_LINES) {
            // Cut: the hover has it whole.
            HudKit.note("hearth.panel.sieges");
            hovers.add(new Hover(x, linesTop, width, y - linesTop, () -> List.of(siegesLine().copy()
                    .withStyle(ChatFormatting.GRAY)), -1));
        }
        return y + 4;
    }

    // ---- Tab "Hearth-keepers" ----------------------------------------------------------------------

    private static List<LedgerView.Entry> entries(HearthLedgerPayload ledger) {
        List<LedgerView.Entry> out = new ArrayList<>(ledger.rows().size());
        for (HearthLedgerPayload.Row row : ledger.rows()) {
            out.add(new LedgerView.Entry(row.name(), doubles(row.week()), doubles(row.lifetime())));
        }
        return out;
    }

    private static double[] doubles(List<Float> values) {
        double[] out = new double[HearthPool.COUNT];
        for (int i = 0; i < Math.min(out.length, values.size()); i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private void drawKeepers(GuiGraphics graphics, HearthStatePayload state) {
        retryLedger(net.minecraft.Util.getMillis());
        int x = panelX + PADDING;
        int width = panelW - PADDING * 2 - 6;
        int y = bodyTop;
        String head = poolFilter < 0
                ? Component.translatable(window == LedgerView.Window.WEEK ? "proficiency.hearth.client.keepers.head_week"
                        : "proficiency.hearth.client.keepers.head_lifetime").getString()
                : Component.translatable(window == LedgerView.Window.WEEK ? "proficiency.hearth.client.keepers.pool_week"
                        : "proficiency.hearth.client.keepers.pool_lifetime", HearthText.pool(poolFilter)).getString();
        header(graphics, "hearth.keepers.head", head, x, y, width);
        y += ROW;
        HearthLedgerPayload ledger = currentLedger();
        if (ledger == null) {
            graphics.drawString(this.font, Component.translatable("proficiency.hearth.client.keepers.loading"), x, y + 2,
                    SkillPalette.TEXT_DIM, false);
            return;
        }
        List<LedgerView.Entry> entries = entries(ledger);
        List<Integer> order = LedgerView.rank(entries, window, poolFilter);
        if (order.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("proficiency.hearth.client.keepers.empty"), x, y + 2,
                    SkillPalette.TEXT_DIM, false);
            return;
        }
        double best = entries.get(order.get(0)).total(window, poolFilter);
        int rank = 0;
        for (int index : order) {
            rank++;
            drawKeeper(graphics, ledger.rows().get(index), entries.get(index), rank, best, x, y, width);
            y += KEEPER_ROW;
        }
    }

    private void drawKeeper(GuiGraphics graphics, HearthLedgerPayload.Row row, LedgerView.Entry entry, int rank,
            double best, int x, int y, int width) {
        double total = entry.total(window, poolFilter);
        String rankText = rank + ".";
        int rankW = this.font.width("10.") + 2;
        graphics.drawString(this.font, rankText, x + rankW - 2 - this.font.width(rankText), y, rank == 1 ? SkillPalette.MAXED
                : SkillPalette.TEXT_DIM, false);
        String totalText = Long.toString(Math.round(total));
        int totalW = this.font.width(totalText);
        int kills = row.siegeKills();
        String killsText = kills > 0 ? Integer.toString(kills) : "";
        int killsW = kills > 0 ? 10 + this.font.width(killsText) + 6 : 0;
        int nameX = x + rankW;
        String name = HudKit.clip(this.font, row.name(), x + width - totalW - killsW - 6 - nameX);
        graphics.drawString(this.font, name, nameX, y, SkillPalette.TEXT, false);
        int totalColour = poolFilter >= 0 ? HearthPalette.pool(poolFilter) : SkillPalette.TEXT;
        graphics.drawString(this.font, totalText, x + width - totalW, y, totalColour, false);
        if (kills > 0) {
            int kx = x + width - totalW - killsW;
            if (HudKit.iconsOn()) {
                HudKit.icon(graphics, TrackerHud.icon("trophy"), kx, y - 1, 8, 255);
            }
            graphics.drawString(this.font, killsText, kx + 10, y, HearthPalette.SIEGE, false);
        }
        // The stacked bar of shares, as long as this keeper's total next to the best one.
        int barTop = y + 10;
        int full = width - rankW;
        int barW = best > 0 ? (int) Math.max(2, Math.round(full * total / best)) : 0;
        graphics.fill(nameX, barTop, nameX + full, barTop + BAR, SkillPalette.TRACK);
        double[] perPool = entry.in(window).clone();
        if (poolFilter >= 0) {
            for (int i = 0; i < perPool.length; i++) {
                if (i != poolFilter) {
                    perPool[i] = 0;
                }
            }
        }
        int[] segments = LedgerView.segments(perPool, barW);
        int cursor = nameX;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] > 0) {
                graphics.fill(cursor, barTop, cursor + segments[i], barTop + BAR, HearthPalette.pool(i));
                cursor += segments[i];
            }
        }
        // "mostly Mining, Masonry, Tales" under the bar.
        // Ranked by one pool: only the sources that feed it (the shipped mapping; the client has no other).
        List<HearthLedgerPayload.Source> top = window == LedgerView.Window.WEEK ? row.topWeek() : row.topLifetime();
        List<String> names = new ArrayList<>();
        for (HearthLedgerPayload.Source source : top) {
            if (names.size() < 3 && (poolFilter < 0
                    || LedgerView.feeds(source.source(), poolFilter, dev.amman.proficiency.hearth.HearthData.DEFAULTS))) {
                names.add(HearthText.source(source.source()).getString());
            }
        }
        if (!names.isEmpty()) {
            String mostly = Component.translatable("proficiency.hearth.client.keepers.mostly", String.join(", ", names))
                    .getString();
            HudKit.draw(graphics, this.font, "hearth.keepers.mostly", mostly, nameX, barTop + BAR + 2, full,
                    SkillPalette.TEXT_DIM, false);
        }
        hovers.add(new Hover(x, y - 1, width, KEEPER_ROW - 1, () -> keeperTip(row, entry), -1));
    }

    private List<Component> keeperTip(HearthLedgerPayload.Row row, LedgerView.Entry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(row.name()).withStyle(ChatFormatting.WHITE));
        double[] values = entry.in(window);
        for (HearthPool pool : HearthPool.VALUES) {
            double value = pool.ordinal() < values.length ? values[pool.ordinal()] : 0;
            if (value > 0) {
                lines.add(Component.translatable("proficiency.hearth.client.keepers.pool_line", HearthText.pool(pool.ordinal()),
                        Math.round(value)).withStyle(style -> style.withColor(HearthPalette.pool(pool.ordinal()) & 0xFFFFFF)));
            }
        }
        if (TooltipDetail.detailed()) {
            List<HearthLedgerPayload.Source> top = window == LedgerView.Window.WEEK ? row.topWeek() : row.topLifetime();
            for (HearthLedgerPayload.Source source : top) {
                lines.add(Component.translatable("proficiency.hearth.client.keepers.source_line",
                        HearthText.source(source.source()), Math.round(source.amount())).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("proficiency.hearth.client.keepers.sieges", row.siegeKills(),
                    row.siegesDefended()).withStyle(style -> style.withColor(HearthPalette.SIEGE & 0xFFFFFF)));
            return lines;
        }
        return TooltipDetail.withHint(lines, true);
    }

    // ---- Input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && tab == Tab.HEARTH && mouseY >= bodyTop && mouseY < bodyTop + viewportH) {
            int my = (int) mouseY + scroll;
            for (Hover hover : hovers) {
                if (hover.pool() >= 0 && hover.contains((int) mouseX, my)) {
                    // A pool row ranks the keepers for that pool.
                    poolFilter = hover.pool();
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    select(Tab.KEEPERS);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int max = Math.max(0, bodyH - viewportH);
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * ROW));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            select(tab == Tab.HEARTH ? Tab.KEEPERS : Tab.HEARTH);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // A campfire's stage-0 panel lives only while it is open (the build guide it started stays on).
        HearthView.clearPreview();
        super.removed();
    }

    /** Screen position of a pool row (the layout demo points at it). */
    int[] poolPoint(int pool) {
        int stageH = stageHeight(leftW);
        int smoulder = smoulderHeight(columns == 2 ? leftW + rightW + COLUMN_GAP : columnW);
        HearthStatePayload state = HearthView.panelState();
        int y = bodyTop + smoulder + stageH + ROW;
        for (int i = 0; i < pool && i < HearthPool.COUNT; i++) {
            y += poolRowHeight(boonLines(i, state == null ? null : rowOf(state, i), leftW));
        }
        return new int[] {panelX + PADDING + leftW / 2, y + 4 - scroll};
    }

    /** Screen position of the first keeper row (the layout demo points at it). */
    int[] keeperPoint() {
        return new int[] {panelX + panelW / 2, bodyTop + ROW + 4};
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y, x + 1, y + height, colour);
        graphics.fill(x + width - 1, y, x + width, y + height, colour);
    }
}
