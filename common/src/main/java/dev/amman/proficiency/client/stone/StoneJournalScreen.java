package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.DiscoveryJournalScreen;
import dev.amman.proficiency.client.SkillPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Discovery Journal as a slab page (1.32.0): tabs Biomes, Structures, Dimensions and Skills on the rim, the
 * found count over a molten groove that pours in as the page lands, and the list in a sunk slate (mod headings in
 * the slab's warm caps colour with their count, found entries in ink, runs of unfound ones as one dim line). The
 * lists are the classic Journal's own ({@link DiscoveryJournalScreen#rows}), so both read the same data.
 */
public final class StoneJournalScreen extends StoneSheet {

    private static final long POUR_MS = 450;

    private final DiscoveryJournalScreen data = new DiscoveryJournalScreen(null);
    private List<DiscoveryJournalScreen.Row> rows = List.of();
    private int tab;
    private int scroll;
    private int viewTop;
    private int viewH;
    private int line;
    private long pourAt = Long.MIN_VALUE / 2;

    public StoneJournalScreen(Screen parent) {
        super(Component.translatable("proficiency.journal.title"), parent);
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.back");
    }

    @Override
    String[] tabs() {
        String[] out = new String[DiscoveryJournalScreen.tabCount()];
        for (int i = 0; i < out.length; i++) {
            out[i] = SkillstoneScreen.tr(DiscoveryJournalScreen.tabKey(i));
        }
        return out;
    }

    @Override
    int tab() {
        return tab;
    }

    @Override
    void selectTab(int index, long now) {
        tab = index;
        scroll = 0;
        load(now);
    }

    @Override
    int maxWidth() {
        return 480;
    }

    @Override
    void layout() {
        line = small ? 10 : 11;
        viewTop = iy0 + (small ? 26 : 34);
        viewH = Math.max(line * 3, iy1 - (small ? 8 : 14) - viewTop);
        load(SkillstoneScreen.clock());
    }

    private boolean dataReady;

    private void load(long now) {
        if (this.minecraft == null) {
            return;
        }
        if (!dataReady) {
            // Once, for the font; rows() rebuilds the lists itself on every later load.
            data.init(this.minecraft, this.width, this.height);
            dataReady = true;
        }
        int pad = small ? 8 : 14;
        rows = data.rows(tab, ix1 - ix0 - 2 * pad - 16);
        pourAt = now;
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    void body(GuiGraphics g, long now, int mx, int my) {
        int pad = small ? 8 : 14;
        int x0 = ix0 + pad;
        int x1 = ix1 - pad;
        int top = iy0 + (small ? 5 : 9);
        int found = data.foundCount();
        int total = data.totalCount();
        boolean skills = DiscoveryJournalScreen.skillsTab(tab);
        String summary = skills ? SkillstoneScreen.tr("proficiency.journal.skills.summary", found)
                : SkillstoneScreen.tr("proficiency.journal.found", found, total);
        boolean complete = !skills && total > 0 && found >= total;
        Stone.text(g, this.font, Stone.fit(this.font, summary, x1 - x0), x0, top, complete ? Stone.GOLD : Stone.INK);
        if (!skills) {
            float t = total <= 0 ? 0f : Math.min(1f, found / (float) total);
            float k = ClickFx.motion() ? Math.min(1f, Math.max(0f, (now - pourAt) / (float) POUR_MS)) : 1f;
            k = 1f - (1f - k) * (1f - k);
            Stone.groove(g, x0, x1, top + (small ? 14 : 17), Math.max(found > 0 ? 0.004f : 0f, t * k), 4, true);
            if (complete) {
                Stone.light(g, x1, top + 17, 18, 10, Stone.WARM, 0.8f);
            }
        }
        Stone.well(g, x0 - 4, viewTop, x1 - x0 + 8, viewH);
        int content = rows.size() * line + 10;
        scroll = Math.max(0, Math.min(scroll, content - viewH));
        Stone.scissor(g, x0 - 2, viewTop + 3, x1 + 4, viewTop + viewH - 3);
        int y = viewTop + 6 - scroll;
        int wx1 = x1 - 8;
        for (DiscoveryJournalScreen.Row row : rows) {
            if (y + line > viewTop && y < viewTop + viewH) {
                int rightW = row.right().isEmpty() ? 0 : this.font.width(row.right()) + 6;
                int indent = row.header() ? 0 : 6;
                String shown = Stone.fit(this.font, row.text(), wx1 - x0 - rightW - indent);
                int colour = row.header() ? Stone.HEAD : row.colour() == SkillPalette.TEXT_DIM ? Stone.DIM
                        : row.colour() == SkillPalette.MAXED ? Stone.GOLD : Stone.INK;
                Stone.text(g, this.font, shown, x0 + 2 + indent, y, colour);
                if (!row.right().isEmpty()) {
                    Stone.textRight(g, this.font, row.right(), wx1, y, Stone.DIM);
                }
                if (!shown.equals(row.text()) && mx >= x0 && mx < x1 && my >= y - 1 && my < y + line - 1
                        && my >= viewTop && my < viewTop + viewH) {
                    tooltip.add(Component.literal(row.text()));
                }
            }
            y += line;
        }
        g.disableScissor();
        scrollbar(g, x1 - 2, viewTop + 4, viewH - 8, scroll, content);
    }

    @Override
    void hit(int[] h, long now, boolean shift) {
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        int before = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(delta) * line * 3);
        return scroll != before;
    }

    /** For the layout demo: tab {@code index}. */
    public void showTab(int index) {
        selectTab(index, SkillstoneScreen.clock());
    }
}
