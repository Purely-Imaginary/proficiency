package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * The success treatment for advancement toasts (1.37.0): as the toast slides in, pixel sparks leave its
 * icon, a shine runs across it, a ring opens round the icon and a chime plays; a CHALLENGE adds the
 * capstone flare: a second ring, the bigger spark tier and a glow round the frame. Drawn from
 * {@code ToastInstanceMixin}, which wraps the call into every toast's own {@code render}, so it sits over
 * whatever the toast drew and works for vanilla's {@code AdvancementToast} and FTB Quests' own toasts
 * (matched by class name, so there is no dependency on that mod) without touching other mods' toasts.
 * Client only, closed-form in the toast's own clock (frame-rate independent), about 120 fills at the
 * busiest. Setting {@code ui.advancementBurst} (and {@code ui.juicyFeedback}); Distortion Effects 0% keeps
 * the flash and the chime.
 */
public final class AdvancementFx {

    /** The toast is still sliding in for its first part; the burst waits for the icon to be on screen. */
    public static final long DELAY_MS = 230;
    private static final int GOLD = 0xF2D98A;
    private static final int CHALLENGE = 0xC77DFF;
    /** The advancement icon's centre in the toast (its frame is 8 px in, the icon 16 px). */
    private static final int ICON_X = 16;

    private AdvancementFx() {
    }

    public static boolean on() {
        return ProficiencyClientConfig.uiAdvancementBurst() && ClickFx.on();
    }

    /** What kind of toast this is: 0 not ours to dress, 1 a task or goal or quest, 2 a challenge. */
    public static int tier(Toast toast, boolean challenge) {
        if (toast instanceof net.minecraft.client.gui.components.toasts.AdvancementToast) {
            return challenge ? 2 : 1;
        }
        return isQuestToast(toast) ? 1 : 0;
    }

    /** FTB Quests' completion, reward and custom toasts, by name: a soft match, never a hard dependency. */
    public static boolean isQuestToast(@Nullable Object toast) {
        return toast != null && toast.getClass().getName().startsWith("dev.ftb.mods.ftbquests.client.gui.");
    }

    /**
     * Draws the burst for one frame. {@code shownFor} is the toast's own clock; {@code chimed} says the
     * sound has played; returns the new value of it. Call inside the toast's pose (its top-left is 0, 0).
     */
    public static boolean draw(GuiGraphics g, Toast toast, int tier, long shownFor, boolean chimed) {
        if (tier <= 0 || !on()) {
            return chimed;
        }
        long t = shownFor - DELAY_MS;
        if (t < 0) {
            return chimed;
        }
        if (!chimed) {
            chime(tier);
            chimed = true;
        }
        int w = toast.width();
        int h = toast.height();
        int color = tier >= 2 ? CHALLENGE : GOLD;
        int cy = h / 2;
        // The icon flash: the same quick white as a node's pop.
        float flash = ClickFxMath.flash(t);
        if (flash > 0f) {
            g.fill(ICON_X - 9, cy - 9, ICON_X + 9, cy + 9, ClickFx.argb(0.55f * flash, 0xFFFFFF));
        }
        if (!ClickFx.motion() || t >= ClickFxMath.EDGE_MS) {
            return chimed;
        }
        int seed = toast.getClass().getName().hashCode();
        ClickFx.toastBurst(g, w, h, t, seed, tier, color);
        ring(g, t, ICON_X, cy, tier, color, 0xFFFFFF);
        if (tier >= 2) {
            ring(g, t - 110, ICON_X, cy, tier, 0xFFFFFF, 0xFFFFFF);
            glow(g, w, h, t, color);
        }
        return chimed;
    }

    private static void ring(GuiGraphics g, long t, int cx, int cy, int tier, int color, int white) {
        float a = ClickFxMath.ringAlpha(t);
        if (a <= 0f || t < 0) {
            return;
        }
        float r = ClickFxMath.ringRadius(t, 6f, 14f + 12f * tier);
        int rgb = StreakBadge.lerp(color, white, 0.5f);
        HudMath.ring(g, cx, cy, r, r, ClickFx.argb(a * 0.9f, rgb), tier >= 2 ? 2 : 1, 1, 64);
    }

    /** A halo round the toast's frame, four steps out, with the capstone's quick attack and long decay. */
    private static void glow(GuiGraphics g, int w, int h, long t, int color) {
        float e = ClickFxMath.edge(t);
        if (e <= 0f) {
            return;
        }
        for (int i = 1; i <= 4; i++) {
            float falloff = 1f - (i - 1) / 4f;
            ClickFx.frame(g, -i, -i, w + 2 * i, h + 2 * i, ClickFx.argb(0.65f * e * falloff * falloff, color));
        }
    }

    private static void chime(int tier) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        float pitch = tier >= 2 ? 1.25f : 1.1f;
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.AMETHYST_BLOCK_CHIME, pitch * 1.2f, 0.55f));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 0.5f));
        if (tier >= 2) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1f, 0.4f));
        }
    }
}
