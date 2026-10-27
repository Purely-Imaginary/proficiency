package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.hearth.client.HearthGhost;
import dev.amman.proficiency.hearth.client.HearthGuide;
import dev.amman.proficiency.hearth.client.HearthPreview;
import dev.amman.proficiency.hearth.client.HearthView;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * The Hearth guide as a slab page (1.32.0). Build opens on a gallery of the designs: one sunk card per lane
 * with its stage turning in the GUI (the guide's own {@link HearthPreview}; only the hovered card turns, the
 * rest stand still at the same angle), the design closest to this Hearth ringed in ember. A card opens that
 * design's six stages in a sunk slate; arrows step to the next design in place. Boons and Comfort are the
 * guide's lists in the same slate. The list itself is {@link HearthGuide}, shared with the classic screen.
 */
public final class StoneGuideScreen extends StoneSheet {

    private static final int CARD = 1;
    private static final int GALLERY = 2;
    private static final int STEP = 3;
    private static final int SCROLL_STEP = 30;

    private final HearthGuide guide = new HearthGuide();
    private HearthGuide.Tab tab = HearthGuide.Tab.BUILD;
    /** The design shown on the Build tab, or -1 for the gallery. */
    private int laneView = -1;
    private int scroll;
    private int viewTop;
    private int viewH;
    private int listX;
    private int listW;
    private long hoverSince;
    private int hoverCard = -1;

    public StoneGuideScreen(Screen parent) {
        super(Component.translatable("proficiency.hearth.guide.title"), parent);
    }

    @Override
    String crumb() {
        return SkillstoneScreen.tr("proficiency.stone.back_hearth");
    }

    @Override
    String[] tabs() {
        HearthGuide.Tab[] all = HearthGuide.Tab.values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].label().getString();
        }
        return out;
    }

    @Override
    int tab() {
        return tab.ordinal();
    }

    @Override
    void selectTab(int index, long now) {
        tab = HearthGuide.Tab.values()[index];
        scroll = 0;
        layout();
    }

    @Override
    void layout() {
        int pad = small ? 8 : 14;
        listX = ix0 + pad;
        listW = ix1 - ix0 - 2 * pad - 6;
        viewTop = iy0 + (small ? 22 : 30) + (laneView >= 0 && tab == HearthGuide.Tab.BUILD ? (small ? 20 : 24) : 0);
        viewH = Math.max(30, iy1 - (small ? 8 : 14) - viewTop);
        clamp();
    }

    private void clamp() {
        int max = Math.max(0, guide.contentHeight(tab, this.font, listW - 8) - (viewH - 8));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private static HearthStatePayload home() {
        return HearthView.home() ? HearthView.state() : null;
    }

    /** The stage each card shows: the one this Hearth builds next, or the third away from home. */
    private static int cardStage() {
        HearthStatePayload state = home();
        return state == null ? 3 : Math.max(1, Math.min(HearthDesigns.STAGES, state.stage() + 1));
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    void body(GuiGraphics g, long now, int mx, int my) {
        int pad = small ? 8 : 14;
        int top = iy0 + (small ? 6 : 10);
        String status = HearthGuide.status(false).getString();
        if (this.font.width(status) > ix1 - ix0 - 2 * pad) {
            status = HearthGuide.status(true).getString();
        }
        Stone.text(g, this.font, Stone.fit(this.font, status, ix1 - ix0 - 2 * pad), ix0 + pad, top,
                home() != null ? Stone.INK : Stone.DIM);
        if (tab == HearthGuide.Tab.BUILD && laneView < 0) {
            gallery(g, now, mx, my, pad, top + (small ? 14 : 18));
            return;
        }
        if (tab == HearthGuide.Tab.BUILD) {
            laneBar(g, now, mx, my, pad, top + (small ? 13 : 17));
        }
        Stone.well(g, listX - 4, viewTop, listW + 14, viewH);
        clamp();
        List<Component> tip = guide.render(g, this.font, tab, listX + 2, viewTop + 4, listW - 8, viewH - 8, scroll, mx, my);
        scrollbar(g, listX + listW + 3, viewTop + 4, viewH - 8, scroll, guide.contentHeight(tab, this.font, listW - 8));
        tooltip.addAll(tip);
    }

    /** The eight designs as cards: a turning stage, the name on a plate, the closest one ringed. */
    private void gallery(GuiGraphics g, long now, int mx, int my, int pad, int top) {
        List<HearthDesigns.Lane> lanes = HearthGuide.lanes();
        int n = lanes.size();
        if (n == 0) {
            return;
        }
        int cols = Math.min(4, n);
        int rows = (n + cols - 1) / cols;
        int gap = small ? 6 : 10;
        int x0 = ix0 + pad;
        int x1 = ix1 - pad;
        int y1 = iy1 - (small ? 8 : 14);
        int cw = (x1 - x0 - (cols - 1) * gap) / cols;
        int ch = (y1 - top - (rows - 1) * gap) / rows;
        int closest = HearthGhost.closestLane(home());
        int stage = cardStage();
        int over = -1;
        for (int i = 0; i < n; i++) {
            int x = x0 + (i % cols) * (cw + gap);
            int y = top + (i / cols) * (ch + gap);
            if (mx >= x && mx < x + cw && my >= y && my < y + ch) {
                over = i;
            }
        }
        if (over != hoverCard) {
            hoverCard = over;
            hoverSince = now;
            if (over >= 0) {
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, 1.4f, 0.25f);
            }
        }
        int nameH = small ? 14 : 18;
        for (int i = 0; i < n; i++) {
            int x = x0 + (i % cols) * (cw + gap);
            int y = top + (i / cols) * (ch + gap);
            boolean hov = i == over;
            Object key = "card" + i;
            if (hov) {
                Stone.light(g, x + cw / 2, y + ch / 2, cw * 3 / 4, ch * 3 / 4, Stone.WARM, 0.55f);
            }
            click.begin(g, key, x, y, cw, ch, now);
            Stone.well(g, x, y, cw, ch);
            int size = Math.max(16, Math.min(cw - 8, ch - nameH - 8));
            int px = x + (cw - size) / 2;
            int py = y + 4 + (ch - nameH - 6 - size) / 2;
            Stone.scissor(g, x + 2, y + 2, x + cw - 2, y + ch - nameH - 2);
            // Only the hovered card turns: the others hold the preview's resting angle.
            HearthPreview.draw(g, i, stage, px, py, size, hov ? now - hoverSince : 0L);
            g.disableScissor();
            String name = HearthGuide.laneName(lanes.get(i).id()).getString();
            Stone.plate(g, this.font, x + 3, y + ch - nameH - 2, cw - 6, nameH, Stone.fit(this.font, name, cw - 14),
                    i == closest ? Stone.GOLD : hov ? Stone.INK : Stone.DIM, hov);
            click.end(g, key, x, y, cw, ch, now);
            if (i == closest) {
                Stone.outlineRound(g, x - 2, y - 2, cw + 4, ch + 4, 5, Stone.argb(255, Stone.ember(0.85f)));
            }
            hits.add(new int[] {CARD, i, x, y, cw, ch});
            if (hov) {
                tooltip.add(HearthGuide.laneName(lanes.get(i).id()));
                if (i == closest && home() != null) {
                    tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.lane_yours")));
                }
                tooltip.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.lane_open")));
            }
        }
    }

    /** Above the design's list: back to the gallery, its name, and arrows to the next design. */
    private void laneBar(GuiGraphics g, long now, int mx, int my, int pad, int y) {
        List<HearthDesigns.Lane> lanes = HearthGuide.lanes();
        int h = small ? 14 : 18;
        String back = SkillstoneScreen.tr("proficiency.stone.designs");
        int bw = this.font.width(back) + 14;
        button(g, now, mx, my, GALLERY, 0, ix0 + pad, y, bw, h, back, true, false);
        int aw = h + 4;
        button(g, now, mx, my, STEP, 1, ix1 - pad - aw, y, aw, h, ">", true, false);
        button(g, now, mx, my, STEP, -1, ix1 - pad - 2 * aw - 4, y, aw, h, "<", true, false);
        String name = HearthGuide.laneName(lanes.get(laneView).id()).getString() + "  ·  "
                + SkillstoneScreen.tr("proficiency.stone.of", laneView + 1, lanes.size());
        int left = ix0 + pad + bw + 8;
        int right = ix1 - pad - 2 * aw - 12;
        Stone.textCentred(g, this.font, Stone.fit(this.font, name, right - left), (left + right) / 2, y + (h - 8) / 2,
                Stone.GOLD);
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    void hit(int[] h, long now, boolean shift) {
        switch (h[0]) {
            case CARD -> {
                click.success("card" + h[1], h[2], h[3], h[4], h[5], Stone.ember(0.75f), 0, 1f, now);
                shake(now, 1.5f);
                SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 1.0f, 0.6f);
                open(h[1]);
            }
            case GALLERY -> {
                ClickFx.plainClick();
                open(-1);
            }
            case STEP -> {
                click.press(key(h), h[2], h[3], h[4], h[5], Stone.ember(0.75f), now);
                SkillstoneScreen.sound(SoundEvents.STONE_HIT, h[1] > 0 ? 1.2f : 1.0f, 0.5f);
                open(Math.floorMod(laneView + h[1], HearthGuide.lanes().size()));
            }
            default -> {
            }
        }
    }

    private void open(int lane) {
        laneView = lane;
        if (lane >= 0) {
            guide.setLane(lane);
        }
        scroll = 0;
        layout();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Patchouli's Visualize eyes are drawn inside the list: the list hit-tests them.
        if (button == 0 && (tab != HearthGuide.Tab.BUILD || laneView >= 0)
                && guide.clickEye(slabX(mouseX), slabY(mouseY), viewTop + 4, viewH - 8)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (tab == HearthGuide.Tab.BUILD && laneView < 0) {
            return false;
        }
        int before = scroll;
        scroll -= (int) Math.signum(delta) * SCROLL_STEP;
        clamp();
        return scroll != before;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259 && tab == HearthGuide.Tab.BUILD && laneView >= 0) {
            open(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** For the layout demo: the Build tab on design {@code lane} (-1: the gallery), or another tab. */
    public void show(HearthGuide.Tab next, int lane, int to) {
        tab = next;
        open(lane);
        scroll = to;
        clamp();
    }
}
