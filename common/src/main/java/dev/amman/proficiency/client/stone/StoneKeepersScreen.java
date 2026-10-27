package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.TooltipDetail;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.client.HearthDemo;
import dev.amman.proficiency.hearth.client.HearthPalette;
import dev.amman.proficiency.hearth.client.HearthText;
import dev.amman.proficiency.hearth.client.HearthView;
import dev.amman.proficiency.hearth.client.LedgerView;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hearth-keepers as a slab page (1.32.0): who fed this Hearth, this week or over its life, all pools or
 * one. Each keeper is a row in a sunk slate: the rank in a socket (the first lit), the name, the total, the
 * siege trophies, and the shares as a molten groove poured in each pool's colour, as long as this keeper's
 * total next to the best one. The plates under it pick the window and the pool. The ledger is the server's
 * answer to one request as the page opens (asked again twice at most), as in the classic panel.
 */
public final class StoneKeepersScreen extends StoneSheet {

    private static final int WEEK = 1;
    private static final int LIFE = 2;
    private static final int POOL = 3;

    private LedgerView.Window window;
    private int pool;
    private int scroll;
    private int viewTop;
    private int viewH;
    private int rowH;
    private long askedFor;
    private long askedAt;
    private HearthLedgerPayload askedOver;
    private int asks;

    public StoneKeepersScreen(Screen parent, int pool, boolean lifetime) {
        super(Component.translatable("proficiency.hearth.client.panel.tab.keepers"), parent);
        this.pool = pool;
        this.window = lifetime ? LedgerView.Window.LIFETIME : LedgerView.Window.WEEK;
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.back_hearth");
    }

    @Override
    int maxWidth() {
        return 460;
    }

    @Override
    int maxHeight() {
        return 340;
    }

    @Override
    void layout() {
        rowH = small ? 30 : 40;
        viewTop = iy0 + (small ? 20 : 28);
        viewH = Math.max(rowH, iy1 - (small ? 28 : 40) - viewTop);
        ask(false);
    }

    // ---- The ledger -----------------------------------------------------------------------------

    private void ask(boolean retry) {
        HearthStatePayload state = HearthView.panelState();
        if (state == null || HearthView.previewing() || HearthDemo.fakeLedgerAt(state.pos())) {
            return;
        }
        long now = Util.getMillis();
        if (!retry) {
            if (askedFor == state.pos()) {
                return;
            }
            askedFor = state.pos();
            asks = 0;
        } else if (askedFor != state.pos() || HearthView.ledger() != askedOver || asks >= 3 || now - askedAt < 2500) {
            return;
        }
        askedAt = now;
        askedOver = HearthView.ledger();
        asks++;
        StoneNet.hearth(new HearthRequestPayload(HearthRequestPayload.LEDGER, state.pos()));
    }

    private static HearthLedgerPayload ledger() {
        HearthStatePayload state = HearthView.panelState();
        HearthLedgerPayload ledger = HearthView.ledger();
        return state != null && ledger != null && ledger.pos() == state.pos() ? ledger : null;
    }

    private HearthLedgerPayload cachedLedger;
    private LedgerView.Window cachedWindow;
    private int cachedPool = Integer.MIN_VALUE;
    private List<LedgerView.Entry> cachedEntries = List.of();
    private List<Integer> cachedOrder = List.of();

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

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    void body(GuiGraphics g, long now, int mx, int my) {
        ask(true);
        int pad = small ? 8 : 14;
        int x0 = ix0 + pad;
        int x1 = ix1 - pad;
        boolean week = window == LedgerView.Window.WEEK;
        String headText = pool < 0
                ? SkillstoneScreen.tr(week ? "proficiency.hearth.client.keepers.head_week" : "proficiency.hearth.client.keepers.head_lifetime")
                : SkillstoneScreen.tr(week ? "proficiency.hearth.client.keepers.pool_week" : "proficiency.hearth.client.keepers.pool_lifetime",
                        HearthText.pool(pool).getString());
        HearthLedgerPayload ledger = ledger();
        if (ledger != cachedLedger || window != cachedWindow || pool != cachedPool) {
            // Parsed and ranked once per answer, window or pool, not every frame.
            cachedLedger = ledger;
            cachedWindow = window;
            cachedPool = pool;
            cachedEntries = ledger == null ? List.of() : entries(ledger);
            cachedOrder = ledger == null ? List.of() : LedgerView.rank(cachedEntries, window, pool);
        }
        List<LedgerView.Entry> entries = cachedEntries;
        List<Integer> order = cachedOrder;
        head(g, headText.toUpperCase(java.util.Locale.ROOT), order.isEmpty() ? "" :
                SkillstoneScreen.tr("proficiency.stone.keepers_count", order.size()), x0, x1, iy0 + (small ? 6 : 10));
        Stone.well(g, x0 - 4, viewTop, x1 - x0 + 8, viewH);
        if (ledger == null || order.isEmpty()) {
            String why = SkillstoneScreen.tr(ledger == null ? "proficiency.hearth.client.keepers.loading"
                    : "proficiency.hearth.client.keepers.empty");
            Stone.textCentred(g, this.font, Stone.fit(this.font, why, x1 - x0 - 8), (x0 + x1) / 2, viewTop + viewH / 2 - 4,
                    Stone.DIM);
        } else {
            int content = order.size() * rowH;
            scroll = Math.max(0, Math.min(scroll, content - (viewH - 8)));
            Stone.scissor(g, x0 - 2, viewTop + 3, x1 + 4, viewTop + viewH - 3);
            double best = entries.get(order.get(0)).total(window, pool);
            int y = viewTop + 5 - scroll;
            int rank = 0;
            for (int index : order) {
                rank++;
                if (y + rowH > viewTop && y < viewTop + viewH) {
                    keeper(g, ledger.rows().get(index), entries.get(index), rank, best, x0 + 2, x1 - 8, y, mx, my);
                }
                y += rowH;
            }
            g.disableScissor();
            scrollbar(g, x1 - 2, viewTop + 4, viewH - 8, scroll, content);
        }
        // The plates: the window (the shown one lit) and the pool it ranks by.
        int ph = small ? 16 : 20;
        int py = iy1 - (small ? 6 : 12) - ph;
        String w1 = SkillstoneScreen.tr("proficiency.hearth.client.keepers.week");
        String w2 = SkillstoneScreen.tr("proficiency.hearth.client.keepers.lifetime");
        int gap = small ? 4 : 6;
        int x = x0;
        x += button(g, now, mx, my, WEEK, 0, x, py, this.font.width(w1) + 16, ph, w1, true, week) + gap;
        button(g, now, mx, my, LIFE, 0, x, py, this.font.width(w2) + 16, ph, w2, true, !week);
        String p = pool < 0 ? SkillstoneScreen.tr("proficiency.hearth.client.keepers.all") : HearthText.pool(pool).getString();
        int pw = Math.min(x1 - x - this.font.width(w2) - 16 - gap * 2, this.font.width(p) + 30);
        int poolX = x1 - pw;
        if (pool >= 0) {
            Stone.light(g, poolX + pw / 2, py + ph / 2, pw / 2 + 6, ph, HearthPalette.pool(pool) & 0xFFFFFF, 0.5f);
        }
        button(g, now, mx, my, POOL, 0, poolX, py, pw, ph, "◆ " + p, true, false);
    }

    /** One keeper: rank socket, name, total and trophies on the first line, the poured shares under it. */
    private void keeper(GuiGraphics g, HearthLedgerPayload.Row row, LedgerView.Entry entry, int rank, double best,
                        int x0, int x1, int y, int mx, int my) {
        int ss = small ? 16 : 20;
        boolean first = rank == 1;
        boolean hov = mx >= x0 && mx < x1 && my >= y && my < y + rowH - 2 && my >= viewTop && my < viewTop + viewH;
        if (hov) {
            Stone.fillRound(g, x0 - 2, y - 2, x1 - x0 + 4, rowH - 2, 3, Stone.argb(30, 0xFFF0DC));
        }
        Stone.socket(g, x0, y, ss, ss, first ? 0.9f : 0f, false, hov);
        Stone.textCentred(g, this.font, Integer.toString(rank), x0 + ss / 2, y + (ss - 8) / 2, first ? Stone.GOLD : Stone.DIM);
        int nx = x0 + ss + 6;
        double total = entry.total(window, pool);
        String totalText = Long.toString(Math.round(total));
        int kills = row.siegeKills();
        String killsText = kills > 0 ? Integer.toString(kills) : "";
        int killsW = kills > 0 ? 12 + this.font.width(killsText) + 8 : 0;
        int totalColour = pool >= 0 ? HearthPalette.pool(pool) & 0xFFFFFF : Stone.INK;
        Stone.textRight(g, this.font, totalText, x1, y + 1, totalColour);
        if (kills > 0) {
            int kx = x1 - this.font.width(totalText) - killsW;
            Stone.icon(g, dev.amman.proficiency.hearth.client.TrackerHud.icon("trophy"), kx, y, 8, 1f);
            Stone.text(g, this.font, killsText, kx + 10, y + 1, HearthPalette.SIEGE & 0xFFFFFF);
        }
        Stone.text(g, this.font, Stone.fit(this.font, row.name(), x1 - nx - this.font.width(totalText) - killsW - 8), nx,
                y + 1, first ? Stone.GOLD : Stone.INK);
        // The shares: a dark groove, poured pool by pool in the pool's colour.
        int gy = y + 12;
        int gw = small ? 4 : 5;
        int full = x1 - nx;
        g.fill(nx + 1, gy + 1, nx + full + 1, gy + gw + 1, Stone.argb(120, 0x968278));
        g.fill(nx, gy, nx + full, gy + gw, Stone.argb(255, 0x161110));
        int barW = best > 0 ? (int) Math.max(2, Math.round(full * total / best)) : 0;
        double[] perPool = entry.in(window).clone();
        if (pool >= 0) {
            for (int i = 0; i < perPool.length; i++) {
                if (i != pool) {
                    perPool[i] = 0;
                }
            }
        }
        int[] seg = LedgerView.segments(perPool, barW);
        int cx = nx;
        for (int i = 0; i < seg.length; i++) {
            if (seg[i] <= 0) {
                continue;
            }
            int c = HearthPalette.pool(i) & 0xFFFFFF;
            g.fill(cx, gy + 1, cx + seg[i], gy + gw - 1, Stone.argb(255, c));
            g.fill(cx, gy + 1, cx + seg[i], gy + 2, Stone.argb(255, Stone.shade(c, 1.35f)));
            cx += seg[i];
        }
        if (first && cx > nx) {
            Stone.light(g, cx, gy + gw / 2, 10, 8, Stone.WARM, 0.8f);
        }
        if (!small || rowH >= 26) {
            List<HearthLedgerPayload.Source> top = window == LedgerView.Window.WEEK ? row.topWeek() : row.topLifetime();
            List<String> names = new ArrayList<>();
            for (HearthLedgerPayload.Source source : top) {
                if (names.size() < 3 && (pool < 0
                        || LedgerView.feeds(source.source(), pool, dev.amman.proficiency.hearth.HearthData.DEFAULTS))) {
                    names.add(HearthText.source(source.source()).getString());
                }
            }
            if (!names.isEmpty() && gy + gw + 10 <= y + rowH) {
                String mostly = SkillstoneScreen.tr("proficiency.hearth.client.keepers.mostly", String.join(", ", names));
                Stone.text(g, this.font, Stone.fit(this.font, mostly, full), nx, gy + gw + 3, Stone.DIM);
            }
        }
        if (hov) {
            tip(row, entry);
        }
    }

    private void tip(HearthLedgerPayload.Row row, LedgerView.Entry entry) {
        tooltip.add(Component.literal(row.name()).withStyle(ChatFormatting.WHITE));
        double[] values = entry.in(window);
        for (HearthPool p : HearthPool.VALUES) {
            double value = p.ordinal() < values.length ? values[p.ordinal()] : 0;
            if (value > 0) {
                tooltip.add(Component.translatable("proficiency.hearth.client.keepers.pool_line", HearthText.pool(p.ordinal()),
                        Math.round(value)).withStyle(style -> style.withColor(HearthPalette.pool(p.ordinal()) & 0xFFFFFF)));
            }
        }
        tooltip.add(Component.translatable("proficiency.hearth.client.keepers.sieges", row.siegeKills(),
                row.siegesDefended()).withStyle(style -> style.withColor(HearthPalette.SIEGE & 0xFFFFFF)));
        if (TooltipDetail.detailed()) {
            List<HearthLedgerPayload.Source> top = window == LedgerView.Window.WEEK ? row.topWeek() : row.topLifetime();
            for (HearthLedgerPayload.Source source : top) {
                tooltip.add(Component.translatable("proficiency.hearth.client.keepers.source_line",
                        HearthText.source(source.source()), Math.round(source.amount())).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    void hit(int[] h, long now, boolean shift) {
        switch (h[0]) {
            case WEEK, LIFE -> {
                LedgerView.Window next = h[0] == WEEK ? LedgerView.Window.WEEK : LedgerView.Window.LIFETIME;
                if (next == window) {
                    ClickFx.plainClick();
                    return;
                }
                window = next;
                scroll = 0;
                click.success(key(h), h[2], h[3], h[4], h[5], Stone.ember(0.75f), 0, 1f, now);
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, 1.1f, 0.5f);
            }
            case POOL -> {
                pool = pool + 1 >= HearthPool.COUNT ? -1 : pool + 1;
                scroll = 0;
                int colour = pool < 0 ? Stone.ember(0.75f) : HearthPalette.pool(pool) & 0xFFFFFF;
                click.success(key(h), h[2], h[3], h[4], h[5], colour, 0, 1f, now);
                SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 1.0f + 0.08f * Math.max(0, pool), 0.4f);
            }
            default -> {
            }
        }
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        int before = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(delta) * rowH);
        return scroll != before;
    }

    /** For the layout demo: where the first keeper row sits. */
    public int[] keeperPoint() {
        return new int[] {(ix0 + ix1) / 2, viewTop + 12};
    }
}
