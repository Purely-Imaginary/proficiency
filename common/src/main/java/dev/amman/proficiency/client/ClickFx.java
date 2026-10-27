package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * The click feedback kit: one small layer each screen owns. A click that can succeed or fail asks
 * the layer for one of three answers and the layer does the rest.
 *
 * <ul>
 *   <li>{@link #no}: cannot. The icon wiggles left and right (damped, a quarter second), its frame
 *       flashes red, and a dull thunk plays.</li>
 *   <li>{@link #charge}: the click went out and the answer is on its way. The icon shakes harder
 *       the longer it waits, a tick rises in pitch every tenth of a second, and when no answer
 *       arrives within {@link ClickFxMath#PENDING_MS} it turns into a {@link #no}. {@link #success}
 *       ends it.</li>
 *   <li>{@link #success}: the icon pops past its size and settles, a white flash and a ring leave
 *       it, pixel sparks burst out, a chime plays, and {@link #ripple} lights its neighbours in
 *       turn. A bigger tier (a capstone, a milestone) throws more sparks and also glows at the
 *       screen edge.</li>
 * </ul>
 *
 * <p>Drawing: wrap what you draw for an icon in {@link #begin} and {@link #end}, which move and
 * scale it and lay the frame effects over it; call {@link #render} once, last, outside the
 * screen's own scale. All curves are closed-form in time ({@link ClickFxMath}), so nothing depends
 * on the frame rate; the layer holds at most {@link #MAX_FX} effects and
 * {@link ClickFxMath#MAX_LIVE_SPARKS} sparks, and draws plain fills only. GUI only: no world
 * particles. {@code ui.juicyFeedback} turns the lot off (the old plain click sound stays), and a
 * Distortion Effects setting of 0% keeps the red flash, the white flash and the sounds but stops
 * every movement.
 */
public final class ClickFx {

    public static final int RED = 0xE0503C;
    public static final int MAX_FX = 48;

    private static final int NO = 0;
    private static final int CHARGE = 1;
    private static final int POP = 2;
    private static final int BURST = 3;
    private static final int RING = 4;
    private static final int FLASH = 5;
    private static final int EDGE = 6;
    private static final int GLOW = 7;
    private static final int SOUND = 8;

    private static final class Fx {
        int kind;
        Object key;
        long start;
        int cx;
        int cy;
        int w;
        int h;
        int color;
        int tier;
        int seed;
        float strength;
        long nextTick;
        boolean failOnExpire;
        /** When set, the effect ends here (ms since start) instead of after its usual length. */
        long endAfter;
        boolean played;

        long length() {
            if (endAfter > 0) {
                return endAfter;
            }
            return switch (kind) {
                case NO -> ClickFxMath.RED_MS;
                case CHARGE -> ClickFxMath.PENDING_MS;
                case POP -> ClickFxMath.POP_MS;
                case BURST -> ClickFxMath.BURST_MS;
                case RING -> ClickFxMath.RING_MS;
                case FLASH -> ClickFxMath.FLASH_MS;
                case EDGE -> ClickFxMath.EDGE_MS;
                case SOUND -> 60L;
                default -> 520L;
            };
        }
    }

    private final List<Fx> list = new ArrayList<>();
    private int seeds;
    /** Pose depth {@link #begin} pushed, so {@link #end} always pops what it pushed. */
    private boolean open;

    /** Whether the kit plays at all ({@code ui.juicyFeedback}). */
    public static boolean on() {
        return ProficiencyClientConfig.uiJuicyFeedback();
    }

    /** Whether things may move: on, and the Distortion Effects video setting above 0%. */
    public static boolean motion() {
        if (!on()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.options == null || mc.options.screenEffectScale().get() > 0.0;
    }

    public boolean idle() {
        return list.isEmpty();
    }

    public void clear() {
        for (Fx fx : list) {
            if (fx.kind == SOUND && !fx.played) {
                chime(fx.strength, fx.tier);
            }
        }
        list.clear();
    }

    // ---- Triggers -----------------------------------------------------------------------------

    /** Cannot: wiggle, red frame, dull thunk. {@code x, y, w, h} is the icon's frame. */
    public void no(Object key, int x, int y, int w, int h, long now) {
        if (!on()) {
            return;
        }
        remove(key, CHARGE);
        remove(key, NO);
        Fx fx = add(NO, key, x, y, w, h, RED, now);
        fx.strength = 4.5f;
        thunk();
    }

    /**
     * The click is out and the answer is pending: shake with rising intensity and pitch. When
     * nothing resolves it within {@link ClickFxMath#PENDING_MS}, the key gets a {@link #no}.
     */
    public void charge(Object key, int x, int y, int w, int h, int color, long now) {
        if (!on()) {
            return;
        }
        remove(key, CHARGE);
        Fx fx = add(CHARGE, key, x, y, w, h, color, now);
        fx.failOnExpire = true;
        fx.nextTick = now;
    }

    /** A charge that needs no answer: it shakes for {@link ClickFxMath#PENDING_MS} and ends without a refusal (a confirm). */
    public void arm(Object key, int x, int y, int w, int h, int color, long now) {
        if (!on()) {
            return;
        }
        remove(key, CHARGE);
        Fx fx = add(CHARGE, key, x, y, w, h, color, now);
        fx.failOnExpire = false;
        fx.nextTick = now;
    }

    public boolean charging(Object key) {
        for (Fx fx : list) {
            if (fx.kind == CHARGE && fx.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Ends a charge without a verdict. */
    public void settle(Object key) {
        remove(key, CHARGE);
    }

    /**
     * Success. Tier 0 is a small win (one rank of several), 1 a finished node or a pick, 2 a
     * capstone or a milestone. {@code filled} is 0..1 of the node now filled, for the chime pitch.
     */
    public void success(Object key, int x, int y, int w, int h, int color, int tier, float filled, long now) {
        if (!on()) {
            return;
        }
        // The answer came back faster than the eye can read the charge (a local server answers in a
        // tick): let the shake run for its minimum, then pop.
        long at = now;
        for (Fx fx : list) {
            if (fx.kind == CHARGE && fx.key.equals(key)) {
                at = Math.max(now, fx.start + ClickFxMath.MIN_CHARGE_MS);
                fx.failOnExpire = false;
                fx.endAfter = Math.max(1L, at - fx.start);
            }
        }
        remove(key, NO);
        remove(key, POP);
        remove(key, FLASH);
        Fx pop = add(POP, key, x, y, w, h, color, at);
        pop.strength = 0.12f + 0.05f * tier;
        add(FLASH, key, x, y, w, h, color, at).tier = tier;
        if (motion()) {
            Fx ring = add(RING, key, x, y, w, h, color, at);
            ring.tier = tier;
            burst(key, x + w / 2, y + h / 2, Math.max(w, h), color, tier, at);
            if (tier >= 2) {
                add(EDGE, key, x, y, w, h, color, at);
                Fx second = add(RING, key, x, y, w, h, 0xFFFFFF, at + 110);
                second.tier = tier;
            }
        }
        if (at <= now) {
            chime(filled, tier);
        } else {
            Fx sound = add(SOUND, key, x, y, w, h, color, at);
            sound.tier = tier;
            sound.strength = filled;
        }
    }

    /** Sparks only, at a point: for toasts and milestones that have no icon to pop. */
    public void burst(Object key, int cx, int cy, int size, int color, int tier, long now) {
        if (!motion()) {
            return;
        }
        int live = 0;
        for (Fx fx : list) {
            if (fx.kind == BURST && now - fx.start < ClickFxMath.BURST_MS) {
                live += ClickFxMath.sparkCount(fx.tier);
            }
        }
        int tierUsed = tier;
        while (tierUsed > 0 && live + ClickFxMath.sparkCount(tierUsed) > ClickFxMath.MAX_LIVE_SPARKS) {
            tierUsed--;
        }
        if (live + ClickFxMath.sparkCount(tierUsed) > ClickFxMath.MAX_LIVE_SPARKS) {
            return;
        }
        Fx fx = add(BURST, key, cx - size / 2, cy - size / 2, size, size, color, now);
        fx.tier = tierUsed;
        fx.strength = 16f + size * 0.6f + 14f * tierUsed;
    }

    /** A neighbour lights up {@code delay} ms from now, in the success colour: the ripple. */
    public void ripple(Object key, int x, int y, int w, int h, int color, long now, long delay) {
        if (!motion()) {
            return;
        }
        remove(key, GLOW);
        add(GLOW, key, x, y, w, h, color, now + delay);
    }

    // ---- Drawing ------------------------------------------------------------------------------

    /**
     * Starts drawing the icon in {@code (x, y, w, h)}: moves and scales it by what is running on
     * {@code key}. Always pair with {@link #end}.
     */
    public void begin(GuiGraphics graphics, Object key, int x, int y, int w, int h, long now) {
        graphics.pose().pushPose();
        open = true;
        if (list.isEmpty() || !motion()) {
            return;
        }
        float dx = 0f;
        float dy = 0f;
        float scale = 1f;
        for (Fx fx : list) {
            if (!fx.key.equals(key)) {
                continue;
            }
            long t = now - fx.start;
            if (t < 0) {
                continue;
            }
            switch (fx.kind) {
                case NO -> dx += ClickFxMath.wiggle(t, fx.strength);
                case CHARGE -> {
                    float level = ClickFxMath.chargeLevel(t);
                    dx += ClickFxMath.shakeX(t, level);
                    dy += ClickFxMath.shakeY(t, level);
                }
                case POP -> scale *= ClickFxMath.pop(t, fx.strength);
                default -> {
                }
            }
        }
        if (dx == 0f && dy == 0f && scale == 1f) {
            return;
        }
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        graphics.pose().translate(cx + dx, cy + dy, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-cx, -cy, 0f);
    }

    /** Lays the frame effects (red flash, white flash, charge glow, ripple) over the icon and closes {@link #begin}. */
    public void end(GuiGraphics graphics, Object key, int x, int y, int w, int h, long now) {
        if (!list.isEmpty()) {
            for (Fx fx : list) {
                if (!fx.key.equals(key)) {
                    continue;
                }
                long t = now - fx.start;
                if (t < 0) {
                    continue;
                }
                switch (fx.kind) {
                    case NO -> {
                        float a = ClickFxMath.red(t);
                        if (a > 0f) {
                            graphics.fill(x, y, x + w, y + h, argb(a * 0.45f, RED));
                            frame(graphics, x - 1, y - 1, w + 2, h + 2, argb(a, RED));
                        }
                    }
                    case CHARGE -> {
                        float level = ClickFxMath.chargeLevel(t);
                        graphics.fill(x, y, x + w, y + h, argb(0.10f + 0.30f * level, 0xFFFFFF));
                        frame(graphics, x - 1, y - 1, w + 2, h + 2, argb(0.4f + 0.6f * level, fx.color));
                    }
                    case FLASH -> {
                        float a = ClickFxMath.flash(t);
                        if (a > 0f) {
                            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(a * 0.85f, 0xFFFFFF));
                        }
                    }
                    case GLOW -> {
                        float f = t / (float) fx.length();
                        if (f < 1f) {
                            float a = (float) Math.sin(f * Math.PI);
                            graphics.fill(x, y, x + w, y + h, argb(a * 0.28f, fx.color));
                            frame(graphics, x - 1, y - 1, w + 2, h + 2, argb(a, StreakBadge.lerp(fx.color, 0xFFFFFF, 0.4f)));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        if (open) {
            graphics.pose().popPose();
            open = false;
        }
    }

    /**
     * Draws the sparks, rings and the screen-edge glow, and runs the timers: the charge ticks, the
     * refusal of a charge nobody answered, and the removal of what has finished. Call it once per
     * frame, last, outside the screen's own scale; {@code uiScale} is that scale.
     */
    public void render(GuiGraphics graphics, long now, float uiScale) {
        if (list.isEmpty()) {
            return;
        }
        List<Fx> expired = null;
        float edge = 0f;
        int edgeColor = 0xFFFFFF;
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1f);
        // A new element can be added while iterating (a charge turning into a refusal), so index.
        for (int n = 0; n < list.size(); n++) {
            Fx fx = list.get(n);
            long t = now - fx.start;
            if (t < 0) {
                continue;
            }
            if (t >= fx.length()) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(fx);
                if (fx.kind == SOUND && !fx.played) {
                    // A frame hitch skipped its window: the chime still plays.
                    fx.played = true;
                    chime(fx.strength, fx.tier);
                }
                continue;
            }
            switch (fx.kind) {
                case CHARGE -> {
                    if (now >= fx.nextTick) {
                        tick(ClickFxMath.chargeLevel(t));
                        fx.nextTick = now + ClickFxMath.TICK_MS;
                    }
                }
                case SOUND -> {
                    if (!fx.played) {
                        fx.played = true;
                        chime(fx.strength, fx.tier);
                    }
                }
                case BURST -> sparks(graphics, fx, t);
                case RING -> ring(graphics, fx, t);
                case EDGE -> {
                    float e = ClickFxMath.edge(t);
                    if (e > edge) {
                        edge = e;
                        edgeColor = fx.color;
                    }
                }
                default -> {
                }
            }
        }
        graphics.pose().popPose();
        if (edge > 0f) {
            // The ability glow is meant to be faint; a capstone's is not, so the fade is boosted (alpha is clamped by the fill).
            AbilityFx.edgeGlow(graphics, edgeColor, Math.min(2f, edge * 1.8f), 0L);
        }
        if (expired != null) {
            for (Fx fx : expired) {
                list.remove(fx);
                if (fx.kind == CHARGE && fx.failOnExpire) {
                    no(fx.key, fx.cx - fx.w / 2, fx.cy - fx.h / 2, fx.w, fx.h, now);
                }
            }
        }
    }

    private void sparks(GuiGraphics graphics, Fx fx, long t) {
        drawSparks(graphics, fx.cx, fx.cy, t, fx.seed, fx.tier, fx.strength, fx.color);
    }

    /** Pixel sparks round {@code (cx, cy)} {@code t} ms after the burst started. Stateless, so a toast can call it too. */
    static void drawSparks(GuiGraphics graphics, int cx, int cy, long t, int seed, int tier, float radius,
            int color) {
        int count = ClickFxMath.sparkCount(tier);
        boolean big = tier >= 2;
        for (int i = 0; i < count; i++) {
            float a = ClickFxMath.sparkAlpha(i, seed, t);
            if (a <= 0f) {
                continue;
            }
            int px = cx + Math.round(ClickFxMath.burstX(i, seed, t, radius));
            int py = cy + Math.round(ClickFxMath.burstY(i, seed, t, radius));
            int size = ClickFxMath.sparkSize(i, seed, big);
            // White-hot at birth, the accent colour as it cools; every third spark stays gold.
            float cool = ClickFxMath.clamp01(t / 260f);
            int base = i % 3 == 0 ? 0xFFD96A : color;
            int rgb = StreakBadge.lerp(0xFFFFFF, base, cool);
            graphics.fill(px, py, px + size, py + size, argb(a, rgb));
            if (t > 50) {
                // A dim streak behind it, where it was a few frames ago.
                int qx = cx + Math.round(ClickFxMath.burstX(i, seed, t - 50, radius));
                int qy = cy + Math.round(ClickFxMath.burstY(i, seed, t - 50, radius));
                graphics.fill(qx, qy, qx + Math.max(1, size - 1), qy + Math.max(1, size - 1), argb(a * 0.4f, rgb));
            }
        }
    }

    /**
     * The burst a toast plays as it slides in: sparks leaving its icon corner, a shine across it.
     * {@code shownFor} is the toast's own clock; call it inside the toast's {@code render}.
     */
    public static void toastBurst(GuiGraphics graphics, int w, int h, long shownFor, int seed, int tier, int color) {
        if (!motion() || shownFor < 0 || shownFor >= ClickFxMath.BURST_MS) {
            return;
        }
        drawSparks(graphics, 18, h / 2, shownFor, seed, tier, 26f + 14f * tier, color);
        float f = ClickFxMath.clamp01(shownFor / 420f);
        if (f < 1f) {
            int x = Math.round(f * (w + 30)) - 15;
            for (int row = 0; row < h; row++) {
                int from = Math.max(0, x - row / 2);
                int to = Math.min(w, x - row / 2 + 7);
                if (to > from) {
                    graphics.fill(from, row, to, row + 1, argb((1f - f) * 0.30f, 0xFFFFFF));
                }
            }
        }
    }

    private void ring(GuiGraphics graphics, Fx fx, long t) {
        float a = ClickFxMath.ringAlpha(t);
        if (a <= 0f) {
            return;
        }
        float to = Math.max(fx.w, fx.h) * (0.9f + 0.45f * fx.tier) + 6f;
        float r = ClickFxMath.ringRadius(t, Math.max(fx.w, fx.h) * 0.45f, to);
        int size = fx.tier >= 2 ? 2 : 1;
        int rgb = StreakBadge.lerp(fx.color, 0xFFFFFF, 0.5f);
        HudMath.ring(graphics, fx.cx, fx.cy, r, r, argb(a * 0.9f, rgb), size, 1, 64);
        if (fx.tier >= 1) {
            // A four-ray flare, the rays shortening as they fade: the "boom" of a finished node.
            float f = ClickFxMath.clamp01(t / 300f);
            if (f < 1f) {
                int len = Math.round((0.5f + 0.5f * (1f - (1f - f) * (1f - f))) * to * 1.15f);
                int thick = fx.tier >= 2 ? 2 : 1;
                int col = argb((1f - f) * 0.95f, 0xFFFFFF);
                graphics.fill(fx.cx - len, fx.cy - thick / 2, fx.cx + len, fx.cy - thick / 2 + thick, col);
                graphics.fill(fx.cx - thick / 2, fx.cy - len, fx.cx - thick / 2 + thick, fx.cy + len, col);
            }
        }
    }

    // ---- Sound --------------------------------------------------------------------------------

    private static void play(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        }
    }

    /** The dull thunk of a refusal. */
    public static void thunk() {
        play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7f, 0.9f);
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.55f, 0.35f);
    }

    private static void tick(float level) {
        play(SoundEvents.UI_BUTTON_CLICK.value(), ClickFxMath.chargePitch(level), 0.55f);
    }

    private static void chime(float filled, int tier) {
        float pitch = ClickFxMath.chimePitch(filled, tier);
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 0.8f);
        play(SoundEvents.AMETHYST_BLOCK_CHIME, Math.min(2f, pitch * 1.25f), 0.55f);
        if (tier >= 1) {
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f + 0.15f * tier, 0.5f);
        }
        if (tier >= 2) {
            play(SoundEvents.PLAYER_LEVELUP, 1.25f, 0.35f);
        }
    }

    /** The plain click, for the toggle being off. */
    public static void plainClick() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
    }

    /** Plain click when the kit is off, nothing when it is on (the kit answers the click itself). */
    public static void clickIfOff() {
        if (!on()) {
            plainClick();
        }
    }


    // ---- Vanilla buttons ----------------------------------------------------------------------

    public static Object buttonKey(Button b) {
        // By place, not by object: a screen that rebuilds its widgets keeps its effect running.
        return "btn:" + b.getX() + ":" + b.getY();
    }

    /** Draws a vanilla button through {@link #begin} and {@link #end}, so it can wiggle and pop. */
    public void renderButton(GuiGraphics graphics, Button b, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        Object key = buttonKey(b);
        begin(graphics, key, b.getX(), b.getY(), b.getWidth(), b.getHeight(), now);
        b.render(graphics, mouseX, mouseY, partialTick);
        end(graphics, key, b.getX(), b.getY(), b.getWidth(), b.getHeight(), now);
    }

    /**
     * Call before the screen's own click handling: a click on a disabled button wiggles it "no"; a
     * click on a live one pops it with a few sparks ({@link #press}). Returns whether a button was hit.
     */
    public boolean onClick(Screen screen, double mouseX, double mouseY, int color, Button... skip) {
        if (!on()) {
            return false;
        }
        for (var child : screen.children()) {
            if (child instanceof Button b && b.visible && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                    && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight()) {
                long now = System.currentTimeMillis();
                if (b.active && java.util.Arrays.asList(skip).contains(b)) {
                    return true;
                }
                if (b.active) {
                    press(buttonKey(b), b.getX(), b.getY(), b.getWidth(), b.getHeight(), color, now);
                } else {
                    no(buttonKey(b), b.getX(), b.getY(), b.getWidth(), b.getHeight(), now);
                }
                return true;
            }
        }
        return false;
    }

    /** A small success for an ordinary button: a pop, a flash and a few sparks, with a soft chirp. */
    public void press(Object key, int x, int y, int w, int h, int color, long now) {
        if (!on()) {
            return;
        }
        remove(key, POP);
        remove(key, FLASH);
        add(POP, key, x, y, w, h, color, now).strength = 0.05f;
        add(FLASH, key, x, y, w, h, color, now).tier = 0;
        burst(key, x + w / 2, y + h / 2, Math.min(w, 40), color, 0, now);
        // Vanilla's Button.mouseClicked plays the click itself; only the soft chirp is ours.
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.7f, 0.3f);
    }

    public static boolean inside(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private Fx add(int kind, Object key, int x, int y, int w, int h, int color, long start) {
        if (list.size() >= MAX_FX) {
            list.remove(0);
        }
        Fx fx = new Fx();
        fx.kind = kind;
        fx.key = key;
        fx.start = start;
        fx.cx = x + w / 2;
        fx.cy = y + h / 2;
        fx.w = w;
        fx.h = h;
        fx.color = color & 0xFFFFFF;
        fx.seed = seeds++;
        list.add(fx);
        return fx;
    }

    private void remove(Object key, int kind) {
        list.removeIf(fx -> fx.kind == kind && fx.key.equals(key));
    }

    static void frame(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + 1, argb);
        graphics.fill(x, y + h - 1, x + w, y + h, argb);
        graphics.fill(x, y + 1, x + 1, y + h - 1, argb);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
