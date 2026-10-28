package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.client.UnlockSlamMath;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The unlock slam ({@code ui.unlockSlam}): filling a tree's root wakes the signature (and, past its level, the
 * ability), and the root's socket shows it. It flares, lifts off the tree and grows; the paid materials fly in
 * from the screen edges as their item icons and each one that lands charges the morph from socket to Face card;
 * under it the slab turns to the Face, the card's slot dark; then the card drops into its slot with a squash, a
 * dust burst, a slab jolt and shake, a thunk and a fire roar, an ember edge glow and "Lit!". The timeline is
 * {@link UnlockSlamMath}. A click or a key skips to the end: the Face, the card lit.
 *
 * <p>Distortion Effects at 0%: nothing moves; the slab turns to the Face at once and the card lights in its slot
 * with the glow and the sounds. Shared verbatim with Forge 1.20.1 (copySharedHearth).
 */
final class UnlockSlam {

    /** Face card index of the signature and of the ability. */
    static final int SIGNATURE = 1;
    static final int ABILITY = 2;

    private final SkillstoneScreen s;
    private boolean active;
    private long startAt;
    private final int[] targets = new int[2];
    private int cards;
    /** The socket on the tree when it filled: centre and half size, slab frame. */
    private float srcX;
    private float srcY;
    private float srcHalf;
    private final List<ItemStack> icons = new ArrayList<>();
    private boolean turned;
    private int ticked;
    private boolean flashed;
    private final boolean[] hit = new boolean[2];
    private boolean rose;
    /** The tree socket's flame texture size (its inner square), 0 for none. */
    private int flame;
    /** The target slots, recorded by the Face as it draws them: x y w h per card index. */
    private final int[][] slots = new int[4][];

    UnlockSlam(SkillstoneScreen s) {
        this.s = s;
    }

    static boolean enabled() {
        return ProficiencyClientConfig.uiUnlockSlam() && ClickFx.on();
    }

    boolean active() {
        return active;
    }

    /**
     * Starts the slam from {@code node}'s socket at ({@code x}, {@code y}), half size {@code half}. {@code paid}:
     * the node's materials were taken now, so they fly in.
     */
    void start(Talent node, int x, int y, int half, int flame, boolean signature, boolean ability, boolean paid,
               long now) {
        this.flame = flame;
        if (!enabled() || (!signature && !ability)) {
            return;
        }
        cards = 0;
        if (signature) {
            targets[cards++] = SIGNATURE;
        }
        if (ability) {
            targets[cards++] = ABILITY;
        }
        srcX = x;
        srcY = y;
        srcHalf = half;
        icons.clear();
        if (paid) {
            List<Requirement> wanted = NodeMaterials.wanted(node);
            int[] counts = new int[wanted.size()];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = wanted.get(i).count();
            }
            int[] shown = UnlockSlamMath.icons(counts);
            for (int i = 0; i < shown.length; i++) {
                for (int k = 0; k < shown[i]; k++) {
                    ItemStack stack = NodeMaterials.icon(wanted.get(i), k * 900L);
                    if (!stack.isEmpty()) {
                        icons.add(stack);
                    }
                }
            }
        }
        // Spread the piles round the edges: interleave them, so one pile does not come from one side.
        java.util.Collections.shuffle(icons, new java.util.Random(node.key().hashCode()));
        java.util.Arrays.fill(slots, null);
        java.util.Arrays.fill(hit, false);
        turned = false;
        ticked = 0;
        flashed = false;
        rose = false;
        active = true;
        startAt = now;
        if (!ClickFx.motion()) {
            // Nothing moves: straight to the Face, the card lit in its slot.
            startAt = now - UnlockSlamMath.impactAt(0);
            turned = true;
            ticked = icons.size();
            flashed = true;
            rose = true;
            s.slamTurn(targets[0], now);
        }
    }

    /** A click or a key: the end state at once, the Face open, the card lit. */
    void skip(long now) {
        if (!active) {
            return;
        }
        if (!turned) {
            s.slamTurn(targets[0], now);
        }
        active = false;
    }

    /** Ends without a trace (the skill or the page changed under it). */
    void cancel() {
        active = false;
    }

    private long t(long now) {
        return now - startAt;
    }

    /** Whether the Face draws card {@code index} as an empty dark slot: it is still in flight. */
    boolean hides(int index, long now) {
        int order = order(index);
        return active && order >= 0 && t(now) < UnlockSlamMath.impactAt(order) + UnlockSlamMath.SETTLE_MS;
    }

    private int order(int index) {
        for (int j = 0; j < cards; j++) {
            if (targets[j] == index) {
                return j;
            }
        }
        return -1;
    }

    /** The Face tells where card {@code index} sits this frame, slab frame. */
    void slot(int index, int x, int y, int w, int h) {
        if (active && index >= 0 && index < slots.length) {
            if (slots[index] == null) {
                slots[index] = new int[4];
            }
            slots[index][0] = x;
            slots[index][1] = y;
            slots[index][2] = w;
            slots[index][3] = h;
        }
    }

    /** The slab's downward jolt at impact, px; 0 when nothing may move. */
    float jolt(long now) {
        if (!active || !ClickFx.motion()) {
            return 0f;
        }
        float out = 0f;
        for (int j = 0; j < cards; j++) {
            out = Math.max(out, UnlockSlamMath.jolt(t(now), j));
        }
        return out;
    }

    /** Inside the slab's pose: the dark over the page while the socket flies, the empty slots lit from inside. */
    void renderUnder(GuiGraphics g, long now) {
        if (!active) {
            return;
        }
        long t = t(now);
        // The page's text is batched and sits a hair in front of its fills: flush it, then draw in front of it.
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_UNDER);
        float dim = UnlockSlamMath.dim(t, cards);
        if (dim > 0.01f) {
            Stone.fillRound(g, s.sx, s.sy, s.sw, s.sh, 12, Stone.argb(Math.round(255 * dim), 0x0A0706));
        }
        for (int j = 0; j < cards; j++) {
            int[] r = slots[targets[j]];
            if (r != null && t < UnlockSlamMath.impactAt(j)) {
                // The empty slot waits: a dark socket with a faint ember rim, brighter as its card nears.
                Stone.fillRound(g, r[0], r[1], r[2], r[3], 4, Stone.argb(235, 0x0C0908));
                float near = UnlockSlamMath.drop(t, j);
                Stone.outlineRound(g, r[0], r[1], r[2], r[3], 4,
                        Stone.argb(Math.round(90 + 140 * near), Stone.ember(0.55f + 0.3f * near)));
            }
        }
        g.flush();
        g.pose().popPose();
    }

    /** Depths: the dark over the page, then the flying parts, each layer a step in front of the last. */
    private static final float Z_UNDER = 40f;
    private static final float Z_OVER = 120f;
    private static final float Z_STEP = 3f;

    /** The next layer: what was drawn so far is flushed, and what comes next sits in front of it. */
    private static void layer(GuiGraphics g) {
        g.flush();
        g.pose().translate(0, 0, Z_STEP);
    }

    /** Over everything: the rising socket, the flying materials, the morphing card, the slam and its dust. */
    void render(GuiGraphics g, PlayerSkills skills, long now, int dx, int dy) {
        if (!active) {
            return;
        }
        long t = t(now);
        int n = icons.size();
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_OVER);
        renderOver(g, skills, now, dx, dy, t, n);
        g.flush();
        g.pose().popPose();
        if (t >= UnlockSlamMath.endAt(cards)) {
            active = false;
        }
    }

    private void renderOver(GuiGraphics g, PlayerSkills skills, long now, int dx, int dy, long t, int n) {
        if (!turned && t >= UnlockSlamMath.TURN_AT) {
            turned = true;
            s.slamTurn(targets[0], now);
        }
        if (!rose && t >= UnlockSlamMath.RISE_AT) {
            rose = true;
            SkillstoneScreen.sound(SoundEvents.GRINDSTONE_USE, 0.6f, 0.35f);
            SkillstoneScreen.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 0.6f);
        }
        while (ticked < n && t >= UnlockSlamMath.arrivalAt(ticked, n)) {
            SkillstoneScreen.sound(SoundEvents.AMETHYST_BLOCK_CHIME, UnlockSlamMath.tickPitch(ticked, n), 0.7f);
            SkillstoneScreen.sound(SoundEvents.NOTE_BLOCK_HAT.value(), UnlockSlamMath.tickPitch(ticked, n), 0.5f);
            ticked++;
        }
        if (!flashed && UnlockSlamMath.fullFlash(t, n) > 0f) {
            flashed = true;
            SkillstoneScreen.sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 0.7f);
            SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 1.4f, 0.3f);
        }
        int[] first = slots[targets[0]];
        int w = first != null ? first[2] : 0;
        int h = first != null ? first[3] : 0;
        float hx = s.width / 2f;
        float hy = s.height * 0.42f;
        // Hovering, the card is a size larger than its slot, but never wider than the screen (the one-column Face).
        float hs = w <= 0 ? UnlockSlamMath.DROP_SCALE : Math.min(UnlockSlamMath.DROP_SCALE, (s.width - 24f) / w);
        long slamAt = UnlockSlamMath.SLAM_AT;
        if (t < slamAt || first == null) {
            float rise = UnlockSlamMath.rise(t);
            float cx = UnlockSlamMath.lerp(srcX, hx, rise);
            float cy = UnlockSlamMath.lerp(srcY, hy, rise);
            float side = 2f * srcHalf * UnlockSlamMath.lerp(1f, UnlockSlamMath.RISE_SCALE, rise);
            float k = first == null ? 0f : UnlockSlamMath.morph(t, n);
            float rw = UnlockSlamMath.lerp(side, w * hs, k);
            float rh = UnlockSlamMath.lerp(side, h * hs, k);
            if (cards > 1 && k >= 0.999f) {
                // The second card peels off the first, downwards, as the charge completes.
                float peel = UnlockSlamMath.peel(t) * (rh + 6);
                drawCard(g, skills, targets[1], cx - rw / 2f, cy - rh / 2f + peel, rw, rh, now, 1f);
                layer(g);
            }
            drawMorph(g, skills, cx, cy, rw, rh, k, t, n, now);
            layer(g);
            drawIcons(g, t, n, cx, cy, rw, rh);
        } else {
            for (int j = cards - 1; j >= 0; j--) {
                int[] r = slots[targets[j]];
                if (r == null) {
                    continue;
                }
                long impact = UnlockSlamMath.impactAt(j);
                if (t >= impact && !hit[j]) {
                    hit[j] = true;
                    impact(r, now, j);
                }
                if (t >= impact + UnlockSlamMath.SETTLE_MS) {
                    continue;
                }
                float hw = r[2] * hs;
                float hh = r[3] * hs;
                float fromX = hx - hw / 2f;
                float fromY = hy - hh / 2f + (j == 1 ? UnlockSlamMath.peel(t) * (hh + 6) : 0);
                float d = UnlockSlamMath.drop(t, j);
                float scale = t < impact ? UnlockSlamMath.lerp(hs, 1f, d) : UnlockSlamMath.impactScale(t, j);
                float ox = t >= impact ? dx : 0;
                float oy = t >= impact ? dy : 0;
                float cx = UnlockSlamMath.lerp(fromX + hw / 2f, r[0] + r[2] / 2f + ox, d);
                float cy = UnlockSlamMath.lerp(fromY + hh / 2f, r[1] + r[3] / 2f + oy, d);
                float rw = r[2] * scale;
                float rh = r[3] * scale;
                drawCard(g, skills, targets[j], cx - rw / 2f, cy - rh / 2f, rw, rh, now, 1f);
                layer(g);
                if (t < impact) {
                    // Motion lines over the slot: the card falls fast.
                    Stone.light(g, Math.round(cx), Math.round(cy), Math.round(rw * 0.7f), Math.round(rh * 0.9f),
                            0xC86E1E, 0.5f * d);
                }
            }
        }
        for (int j = 0; j < cards; j++) {
            int[] r = slots[targets[j]];
            if (r != null && hit[j]) {
                layer(g);
                renderAfter(g, r, t, j, dx, dy);
            }
        }
    }

    private void impact(int[] r, long now, int j) {
        SkillstoneScreen.sound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.5f, 1.0f);
        SkillstoneScreen.sound(SoundEvents.STONE_PLACE, 0.5f, 1.0f);
        SkillstoneScreen.sound(SoundEvents.ANVIL_LAND, 0.6f, 0.25f);
        SkillstoneScreen.sound(SoundEvents.FIRECHARGE_USE, 0.6f, 0.7f);
        SkillstoneScreen.sound(SoundEvents.FLINTANDSTEEL_USE, 0.9f, 0.6f);
        if (ClickFx.motion()) {
            s.shake(now, UnlockSlamMath.SHAKE_PX);
            s.click.burst("slam" + j, r[0] + r[2] / 2, r[1] + r[3] / 2, Math.max(r[2], r[3]) / 3, Stone.ember(0.8f), 2, now);
        }
        if (j == 0) {
            s.edgeGlow(now - ClickFxMath.MIN_CHARGE_MS);
        }
    }

    /** After the hit: the dust, the ember edge round the card, "Lit!". */
    private void renderAfter(GuiGraphics g, int[] r, long t, int j, int dx, int dy) {
        long dt = t - UnlockSlamMath.impactAt(j);
        int x = r[0] + dx;
        int y = r[1] + dy;
        float glow = UnlockSlamMath.glow(t, j);
        if (glow > 0.01f) {
            Stone.light(g, x + r[2] / 2, y + r[3] / 2, Math.round(r[2] * 0.7f), Math.round(r[3] * 1.1f), 0xC8641E,
                    0.35f * glow);
            for (int i = 0; i < 4; i++) {
                int a = Math.round(230 * glow * (1f - i / 4f));
                Stone.outlineRound(g, x - 1 - i, y - 1 - i, r[2] + 2 + 2 * i, r[3] + 2 + 2 * i, 4 + i,
                        Stone.argb(a, Stone.ember(0.92f - i * 0.1f)));
            }
        }
        if (ClickFx.motion()) {
            float bx = x + r[2] / 2f;
            float by = y + r[3];
            for (int k = 0; k < UnlockSlamMath.DUST; k++) {
                float a = UnlockSlamMath.dustAlpha(k, dt);
                if (a <= 0.02f) {
                    continue;
                }
                int px = Math.round(bx + UnlockSlamMath.dustX(k, dt, r[2] / 2f));
                int py = Math.round(by - 2 + UnlockSlamMath.dustY(k, dt) - (k % 3) * 2);
                int c = k % 3 == 0 ? 0x8C8076 : k % 3 == 1 ? 0x6E645C : 0xA89A8C;
                int size = k % 4 == 0 ? 3 : 2;
                g.fill(px, py, px + size, py + size, Stone.argb(Math.round(230 * a), c));
            }
        }
        float lit = UnlockSlamMath.litAlpha(t, j);
        if (lit > 0.02f) {
            String mark = SkillstoneScreen.tr("proficiency.stone.slam_lit");
            int rise = Math.round(UnlockSlamMath.litRise(t, j));
            // In the card's header row, between the header and the ready dot or the stars, rising out of it.
            int mx = x + r[2] / 2 - s.font().width(mark) / 2;
            int my = y + 5 - rise;
            g.drawString(s.font(), mark, mx, my, Stone.argb(Math.round(255 * lit), 0xFFECAA), true);
        }
    }

    /**
     * The socket becoming the card: the card's real face on the left of a glowing seam, the molten socket on its
     * right, the seam sweeping across as the charge fills; flares on each landing and on the last one.
     */
    private void drawMorph(GuiGraphics g, PlayerSkills skills, float cx, float cy, float rw, float rh, float k, long t,
                           int n, long now) {
        int x0 = Math.round(cx - rw / 2f);
        int y0 = Math.round(cy - rh / 2f);
        int x1 = Math.round(cx + rw / 2f);
        int y1 = Math.round(cy + rh / 2f);
        float pulse = UnlockSlamMath.pulse(t, n);
        float flash = UnlockSlamMath.fullFlash(t, n);
        int glowR = Math.round(Math.max(rw, rh) * (0.6f + 0.15f * pulse + 0.15f * flash));
        Stone.light(g, Math.round(cx), Math.round(cy), glowR, Math.round(glowR * Math.min(1f, rh / Math.max(1f, rw) + 0.4f)),
                Stone.WARM, 0.45f + 0.3f * pulse + 0.4f * flash);
        int seam = Math.round(x0 + (x1 - x0) * k);
        if (k > 0.001f) {
            Stone.scissor(g, x0 - 2, y0 - 2, seam, y1 + 2);
            drawCard(g, skills, targets[0], x0, y0, rw, rh, now, 1f);
            g.disableScissor();
        }
        layer(g);
        if (k < 0.999f) {
            Stone.scissor(g, seam, y0 - 2, x1 + 2, y1 + 2);
            Stone.socket(g, x0, y0, x1 - x0, y1 - y0, 1.3f, true, true);
            // The molten fill inside the socket, glowing hotter as it charges.
            int in = Math.max(3, Math.round(Math.min(rw, rh) * 0.14f));
            g.fill(x0 + in, y0 + in, x1 - in, y1 - in, Stone.argb(255, Stone.ember(0.42f + 0.2f * k + 0.2f * pulse)));
            // Molten streaks, brighter towards the bottom where the metal pooled.
            for (int yy = y0 + in; yy < y1 - in; yy += 3) {
                float depth = (yy - y0 - in) / (float) Math.max(1, y1 - y0 - 2 * in);
                g.fill(x0 + in, yy, x1 - in, yy + 1, Stone.argb(Math.round(60 + 120 * depth), Stone.ember(0.75f + 0.2f * depth)));
            }
            if (flame > 0) {
                // The full node's own flame, scaled up pixel for pixel.
                int frame = (int) ((now / 100L) % 8);
                g.pose().pushPose();
                g.pose().translate(x0 + in, y0 + in, 1f);
                g.pose().scale((x1 - x0 - 2f * in) / flame, (y1 - y0 - 2f * in) / flame, 1f);
                Stone.blitAlpha(g, Stone.tex("flame_" + flame), 0, 0, flame, flame, 0f, frame * flame, flame, flame * 8, 1f);
                g.pose().popPose();
            }
            g.disableScissor();
        }
        layer(g);
        if (k > 0.001f && k < 0.999f) {
            // The glow seam along the morph.
            Stone.light(g, seam, Math.round(cy), 8, Math.round(rh * 0.7f), 0xFFB050, 1.1f);
            g.fill(seam - 1, y0 - 2, seam + 1, y1 + 2, Stone.argb(255, 0xFFE8B0));
        }
        if (flash > 0.01f) {
            Stone.fillRound(g, x0, y0, x1 - x0, y1 - y0, 4, Stone.argb(Math.round(150 * flash), 0xFFE0A0));
        }
    }

    /** Card {@code index} drawn into the rect, scaled from its slot's size. */
    private void drawCard(GuiGraphics g, PlayerSkills skills, int index, float x, float y, float w, float h, long now,
                          float alpha) {
        int[] r = slots[index];
        if (r == null || r[2] <= 0 || r[3] <= 0) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(w / r[2], h / r[3], 1f);
        s.paintCard(g, skills, index, r[2], r[3], now);
        g.pose().popPose();
    }

    /** The paid materials, in flight from the screen edges to the morphing socket. */
    private void drawIcons(GuiGraphics g, long t, int n, float cx, float cy, float rw, float rh) {
        for (int i = 0; i < n; i++) {
            float f = UnlockSlamMath.flight(t, i, n);
            if (f < 0f || f >= 1f) {
                continue;
            }
            float sx = UnlockSlamMath.startX(i, s.width);
            float sy = UnlockSlamMath.startY(i, s.height);
            for (int step = 2; step >= 1; step--) {
                float back = f - step * 0.05f;
                if (back <= 0f) {
                    continue;
                }
                int tx = Math.round(UnlockSlamMath.curve(i, back, sx, sy, cx, cy, false));
                int ty = Math.round(UnlockSlamMath.curve(i, back, sx, sy, cx, cy, true));
                int a = step == 1 ? 170 : 90;
                g.fill(tx - 1, ty - 1, tx + 2, ty + 2, Stone.argb(a, Stone.ember(0.85f)));
            }
            float px = UnlockSlamMath.curve(i, f, sx, sy, cx, cy, false);
            float py = UnlockSlamMath.curve(i, f, sx, sy, cx, cy, true);
            float size = UnlockSlamMath.lerp(1.6f, 0.8f, f * f);
            g.pose().pushPose();
            g.pose().translate(px, py, 0);
            g.pose().scale(size, size, 1f);
            g.renderItem(icons.get(i), -8, -8);
            g.pose().popPose();
        }
    }
}
