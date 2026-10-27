package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.SkillstoneMath;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * A further slab page of the Skillstone (1.32.0): the Hearth's plan, guide and keepers, and the Expertise,
 * Mastery, Titles and Journal pages. One carved slab that lands with a short drop and a stone thud, a crumb
 * back to the page it came from on the rim's top-left, the page's name on a plate, optional tabs on the
 * top-right (the same blocks as the Face / Tree / Hearth tabs), the overload refusal (wiggle, red flash,
 * thunk, a reason chip) and the click sparks of {@link ClickFx}.
 *
 * <p>Hits are {@code {kind, index, x, y, w, h}} in the slab's own frame; kind {@link #BACK} and {@link #TAB}
 * are handled here, every other kind goes to {@link #hit}. Shared verbatim with Forge 1.20.1: no
 * version-specific API (both scroll overloads, no {@code renderBackground}).
 */
public abstract class StoneSheet extends Screen {

    static final int BACK = -1;
    static final int TAB = -2;
    private static final long LAND_MS = 200;
    private static final long NOTE_MS = 2500;

    protected final Screen parent;
    final ClickFx click = new ClickFx();
    final List<int[]> hits = new ArrayList<>();
    final List<Component> tooltip = new ArrayList<>();
    int sx, sy, sw, sh, ix0, iy0, ix1, iy1;
    /** A narrow or short slab (GUI 3 at 720p, GUI 4): tighter padding, smaller parts. */
    boolean small;
    private final long openedAt = SkillstoneScreen.clock();
    private boolean thud;
    private long shakeAt = Long.MIN_VALUE / 2;
    private float shakeAmp;
    private long refuseAt = Long.MIN_VALUE / 2;
    private long riseAt = Long.MIN_VALUE / 2;
    private long edgeAt = Long.MIN_VALUE / 2;
    private String note;
    private long noteAt;
    private int[] noteHit;
    private int hitDx;
    private int hitDy;

    protected StoneSheet(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    // ---- What a page says -----------------------------------------------------------------------

    /** The crumb's label ("< Hearth"). */
    abstract String crumb();

    /** The tabs on the rim's top-right, or none. */
    String[] tabs() {
        return new String[0];
    }

    /** The shown tab's index. */
    int tab() {
        return 0;
    }

    /** A tab was clicked (never the shown one). */
    void selectTab(int index, long now) {
    }

    /** The slab's widest and tallest, before the window clamps it. */
    int maxWidth() {
        return SkillstoneMath.TREE_W;
    }

    int maxHeight() {
        return SkillstoneMath.HEARTH_H;
    }

    /** Lays the page out inside {@code ix0..ix1, iy0..iy1}. */
    void layout() {
    }

    abstract void body(GuiGraphics g, long now, int mx, int my);

    /** A click on one of the page's own hits; {@code shift} is held. */
    abstract void hit(int[] h, long now, boolean shift);

    /** The wheel over the page; true when it scrolled. */
    boolean scroll(double mx, double my, double delta) {
        return false;
    }

    // ---- Layout ---------------------------------------------------------------------------------

    @Override
    protected void init() {
        sw = Math.min(maxWidth(), SkillstoneMath.treeWidth(this.width));
        sh = Math.min(maxHeight(), SkillstoneMath.treeHeight(this.height - 8));
        sx = (this.width - sw) / 2;
        sy = (this.height - sh) / 2 + 4;
        int rim = SkillstoneMath.RIM;
        ix0 = sx + rim;
        iy0 = sy + rim;
        ix1 = sx + sw - rim;
        iy1 = sy + sh - rim;
        small = sw < 520 || sh < 300;
        layout();
    }

    // ---- Feedback -------------------------------------------------------------------------------

    /** A short decaying shake of the whole slab, {@code amp} px (a rank, a pick). */
    void shake(long now, float amp) {
        shakeAt = now;
        shakeAmp = amp;
    }

    /** The unlocking shake: grows and climbs, from {@code at}. */
    void riseShake(long at) {
        riseAt = at;
    }

    /** A capstone's ember glow round the slab's edge, after the click's charge. */
    void edgeGlow(long now) {
        edgeAt = now + dev.amman.proficiency.client.ClickFxMath.MIN_CHARGE_MS;
    }

    /** Overload refusal: the part wiggles hard, the slab jolts, a red flash, a thunk and the reason on a chip. */
    void refuse(Object key, int[] h, String why, long now) {
        click.no(key, h[2], h[3], h[4], h[5], now);
        refuseAt = now;
        if (!ClickFx.on()) {
            ClickFx.plainClick();
        }
        say(h, why, now);
    }

    /** The reason chip alone, for a charge whose refusal ClickFx already showed when it expired. */
    void say(int[] h, String why, long now) {
        note = why;
        noteAt = now;
        noteHit = h.clone();
    }

    // ---- Render ---------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = SkillstoneScreen.clock();
        g.fillGradient(0, 0, this.width, this.height, 0xA8000000, 0xC8000000);
        hits.clear();
        tooltip.clear();
        float dx = 0;
        float dy = 0;
        long open = now - openedAt;
        if (ClickFx.motion()) {
            dx += SkillstoneMath.shakeX(now - shakeAt, shakeAmp) + SkillstoneMath.refuseX(now - refuseAt, 2f);
            dy += SkillstoneMath.shakeY(now - shakeAt, shakeAmp);
            dx += 0.5f * SkillstoneMath.riseShake(now - riseAt, 2f);
            dy -= Math.abs(SkillstoneMath.riseShake(now - riseAt, 2.5f));
            if (open >= 0 && open < LAND_MS) {
                // The slab drops the last few pixels onto the table.
                float k = 1f - open / (float) LAND_MS;
                dy -= 14f * k * k;
            }
        }
        if (!thud && open >= LAND_MS - 40) {
            thud = true;
            SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 0.85f, 0.55f);
        }
        g.pose().pushPose();
        g.pose().translate(Math.round(dx), Math.round(dy), 0);
        int mx = mouseX - Math.round(dx);
        int my = mouseY - Math.round(dy);
        hitDx = Math.round(dx);
        hitDy = Math.round(dy);
        Stone.slab(g, sx, sy, sw, sh, SkillstoneMath.RIM);
        renderRim(g, now, mx, my);
        body(g, now, mx, my);
        renderNote(g, now);
        g.pose().popPose();
        float red = SkillstoneMath.refuseFlash(now - refuseAt);
        if (red > 0) {
            Stone.outlineRound(g, sx - 1, sy - 1, sw + 2, sh + 2, 12, Stone.argb(Math.round(200 * red), Stone.RED));
        }
        long edge = now - edgeAt;
        if (edge >= 0 && edge < 700 && ClickFx.on()) {
            float k = 1f - edge / 700f;
            for (int i = 0; i < 6; i++) {
                int a = Math.round(220 * k * (1f - i / 6f));
                Stone.outlineRound(g, sx - 1 - i, sy - 1 - i, sw + 2 + 2 * i, sh + 2 + 2 * i, 12 + i,
                        Stone.argb(a, Stone.ember(0.9f - i * 0.08f)));
            }
        }
        click.render(g, now, 1f);
        if (!tooltip.isEmpty()) {
            Stone.tooltip(g, this.font, tooltip, mouseX, mouseY, this.width);
        }
    }

    /** The crumb, the title plate and the tabs, standing on the rim's top edge. */
    private void renderRim(GuiGraphics g, long now, int mx, int my) {
        int y = iy0 - 15;
        String back = crumb();
        int bw = Math.max(small ? 56 : 70, this.font.width(back) + 14);
        boolean hov = mx >= ix0 + 4 && mx < ix0 + 4 + bw && my >= y && my < y + 18;
        click.begin(g, "crumb", ix0 + 4, y, bw, 18, now);
        Stone.block(g, ix0 + 4, y, bw, 18, 3, 2, false, Stone.shade(Stone.STONE, hov ? 1.15f : 0.95f));
        Stone.textCentred(g, this.font, back, ix0 + 4 + bw / 2, y + 5, hov ? Stone.INK : Stone.DIM);
        click.end(g, "crumb", ix0 + 4, y, bw, 18, now);
        hits.add(new int[] {BACK, 0, ix0 + 4, y, bw, 18});
        String[] names = tabs();
        int base = small ? 50 : 62;
        int gap = small ? 4 : 6;
        // Each tab as wide as its label when they all fit beside the crumb; else even tabs, the labels cut.
        int[] widths = new int[names.length];
        int total = 0;
        for (int i = 0; i < names.length; i++) {
            widths[i] = Math.max(base, this.font.width(names[i]) + 12);
            total += widths[i] + gap;
        }
        if (total > ix1 - (ix0 + 4 + bw + 8)) {
            java.util.Arrays.fill(widths, base);
            total = names.length * (base + gap);
        }
        int tabsX = ix1 - total;
        int x = tabsX;
        for (int i = 0; i < names.length; i++) {
            int tw = widths[i];
            boolean on = i == tab();
            boolean th = mx >= x && mx < x + tw && my >= y && my < y + 18;
            Object key = "tab" + i;
            click.begin(g, key, x, y, tw, 18, now);
            Stone.block(g, x, y, tw, 18, 3, 2, false, Stone.shade(Stone.STONE, on ? 1.3f : th ? 1.15f : 0.95f));
            Stone.textCentred(g, this.font, Stone.fit(this.font, names[i], tw - 6), x + tw / 2, y + 5,
                    on ? Stone.GOLD : th ? Stone.INK : Stone.DIM);
            click.end(g, key, x, y, tw, 18, now);
            hits.add(new int[] {TAB, i, x, y, tw, 18});
            x += tw + gap;
        }
        // The page's name, on a plate between the crumb and the tabs when there is room.
        String title = this.title.getString();
        int room = (names.length > 0 ? tabsX - 8 : ix1 - 4) - (ix0 + 4 + bw + 8);
        int pw = Math.min(room, this.font.width(title) + 24);
        if (pw >= 40) {
            int left = ix0 + 4 + bw + 8;
            int px = names.length > 0 ? left : Math.max(left, (sx + sx + sw) / 2 - pw / 2);
            Stone.plate(g, this.font, px, y, pw, 18, Stone.fit(this.font, title, pw - 10), Stone.GOLD, false);
        }
    }

    /** A refusal's reason on a dark chip under the refused part, kept on the slab. */
    private void renderNote(GuiGraphics g, long now) {
        if (note == null || noteHit == null || now - noteAt >= NOTE_MS) {
            return;
        }
        float k = 1f - (now - noteAt) / (float) NOTE_MS;
        int fade = Math.round(255 * Math.min(1f, k * 3));
        String shown = Stone.fit(this.font, note, ix1 - ix0 - 16);
        int w = this.font.width(shown);
        int tx = Math.max(ix0 + 6, Math.min(noteHit[2] + noteHit[4] / 2 - w / 2, ix1 - 6 - w));
        int ty = noteHit[3] + noteHit[5] + 5;
        if (ty + 10 > iy1) {
            ty = noteHit[3] - 14;
        }
        Stone.fillRound(g, tx - 4, ty - 3, w + 8, 13, 2, Stone.argb(Math.round(fade * 0.9f), 0x2A1210));
        Stone.outlineRound(g, tx - 4, ty - 3, w + 8, 13, 2, Stone.argb(fade, 0x8C2A1E));
        g.drawString(this.font, shown, tx, ty, Stone.argb(fade, 0xFF7864), true);
    }

    // ---- Shared parts ---------------------------------------------------------------------------

    /** A carved plate button; records its hit and returns its width. */
    int button(GuiGraphics g, long now, int mx, int my, int kind, int index, int x, int y, int w, int h, String label,
               boolean enabled, boolean lit) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        Object key = "b" + kind + ":" + index;
        if (lit) {
            Stone.light(g, x + w / 2, y + h / 2, w / 2 + 8, h, Stone.WARM, 0.7f);
        }
        click.begin(g, key, x, y, w, h, now);
        String shown = Stone.fit(this.font, label, w - 8);
        Stone.plate(g, this.font, x, y, w, h, shown, !enabled ? Stone.LOCK : hov || lit ? Stone.GOLD : Stone.INK,
                hov && enabled);
        click.end(g, key, x, y, w, h, now);
        hits.add(new int[] {kind, index, x, y, w, h});
        if (hov && !shown.equals(label)) {
            tooltip.add(Component.literal(label));
        }
        return w;
    }

    static Object key(int[] h) {
        return "b" + h[0] + ":" + h[1];
    }

    /** A stone scrollbar: a dark groove with a warm thumb. */
    static void scrollbar(GuiGraphics g, int x, int top, int height, int scroll, int content) {
        if (content <= height || height <= 8) {
            return;
        }
        g.fill(x, top, x + 3, top + height, Stone.argb(255, 0x161110));
        int thumb = Math.max(10, height * height / content);
        int ty = top + Math.round((height - thumb) * (scroll / (float) (content - height)));
        g.fill(x, ty, x + 3, ty + thumb, Stone.argb(255, Stone.ember(0.62f)));
        g.fill(x + 1, ty, x + 2, ty + thumb, Stone.argb(255, Stone.ember(0.86f)));
    }

    /** A section heading in the slab's warm caps colour, with the count on the right. */
    void head(GuiGraphics g, String left, String right, int x0, int x1, int y) {
        Stone.text(g, this.font, Stone.fit(this.font, left, x1 - x0 - this.font.width(right) - 8), x0, y, Stone.HEAD);
        if (!right.isEmpty()) {
            Stone.textRight(g, this.font, right, x1, y, Stone.DIM);
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || this.minecraft == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        long now = SkillstoneScreen.clock();
        double x = mouseX - hitDx;
        double y = mouseY - hitDy;
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (x < h[2] || x >= h[2] + h[4] || y < h[3] || y >= h[3] + h[5]) {
                continue;
            }
            if (h[0] == BACK) {
                ClickFx.plainClick();
                onClose();
            } else if (h[0] == TAB) {
                if (h[1] != tab()) {
                    click.press("tab" + h[1], h[2], h[3], h[4], h[5], Stone.ember(0.75f), now);
                    SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 0.9f, 0.6f);
                    selectTab(h[1], now);
                }
            } else {
                hit(h, now, hasShiftDown());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Where the last frame drew the slab: a click is moved into it the same way as the hover. */
    double slabX(double mouseX) {
        return mouseX - hitDx;
    }

    double slabY(double mouseY) {
        return mouseY - hitDy;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Backspace: back to where it came from, as on the Face.
        if (keyCode == 259) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scroll(mouseX - hitDx, mouseY - hitDy, scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scroll(mouseX - hitDx, mouseY - hitDy, delta);
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

    /** For the layout demo: the slab rect, x y w h. */
    public int[] slabRect() {
        return new int[] {sx, sy, sw, sh};
    }

    /** For the layout demo: the centre of the first hit of {@code kind} (and {@code index}, or any when -1). */
    public int[] point(int kind, int index) {
        for (int[] h : hits) {
            if (h[0] == kind && (index < 0 || h[1] == index)) {
                return new int[] {h[2] + h[4] / 2 + hitDx, h[3] + h[5] / 2 + hitDy};
            }
        }
        return null;
    }
}
