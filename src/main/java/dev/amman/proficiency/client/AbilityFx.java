package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What a running ability and a cooling one look like: a thin glow on the screen edge in the
 * skill's colour while it runs, a ring round the crosshair that drains with the time left, and a
 * vanilla-style sweep over the held item's hotbar slot while it cools down.
 *
 * <p>The sweep is drawn here rather than through the vanilla item cooldown. That one blocks using
 * the item on both sides (a bow will not draw, a block will not place), and an ability's cooldown
 * must never stop you swinging the thing that triggers it.
 */
public final class AbilityFx {

    private static final int EDGE = 14;
    private static final int EDGE_STEP = 2;
    /** Peak alpha of the glow at the very edge, out of 255. Low on purpose. */
    private static final int GLOW_ALPHA = 78;
    private static final int RING_RADIUS = 12;
    private static final float TICKS_PER_SECOND = 20f;
    /** A third of a second. */
    private static final float FADE_IN_TICKS = 7f;

    private static final RemainTrack FRENZY = new RemainTrack(Skill.VALUES.length);
    private static final RemainTrack COOLDOWN = new RemainTrack(Skill.VALUES.length);

    private AbilityFx() {
    }

    public static void reset() {
        FRENZY.clear();
        COOLDOWN.clear();
    }

    /**
     * Draws all three. {@code held} is the skill the held item triggers, or null. {@code gameTime}
     * plus {@code partial} is the client's smooth game clock. Both timers are tracked for every
     * skill every frame, so a total never goes stale while another item is in hand.
     */
    public static void render(GuiGraphics graphics, Player player, PlayerSkills skills,
            @Nullable Skill held, float partial, long now) {
        if (!ProficiencyClientConfig.hudAbilityFx()) {
            return;
        }
        long gameTime = player.level().getGameTime();
        // The one that ends last gets the glow and the ring; two at once would be a mess.
        Skill running = null;
        float runningLeft = 0f;
        float heldCooldown = 0f;
        for (Skill skill : Skill.VALUES) {
            int slot = skill.ordinal();
            float left = skills.frenzyRemaining(skill, gameTime) - partial;
            if (FRENZY.fraction(slot, left) > 0f && left > runningLeft) {
                running = skill;
                runningLeft = left;
            }
            float cooling = skills.cooldownRemaining(skill, gameTime) - partial;
            float fraction = COOLDOWN.fraction(slot, cooling);
            if (skill == held) {
                heldCooldown = fraction;
            }
        }
        if (running != null) {
            int accent = SkillPalette.accent(running.category());
            int slot = running.ordinal();
            float fraction = FRENZY.fraction(slot, runningLeft);
            // Eased in over the first third of a second, out over the last second.
            float elapsed = FRENZY.total(slot) - runningLeft;
            float fade = Math.min(Math.min(1f, runningLeft / TICKS_PER_SECOND),
                    Math.max(0f, Math.min(1f, elapsed / FADE_IN_TICKS)));
            edgeGlow(graphics, accent, fade, now);
            ring(graphics, accent, fraction, fade, runningLeft / TICKS_PER_SECOND);
        }
        // Only over a stack that is really in the hand: an empty hand means the skill came from
        // what you are doing (swimming, sneaking), not from an item to put a sweep on.
        if (held != null && heldCooldown > 0f && !player.getMainHandItem().isEmpty()) {
            int slot = player.getInventory().selected;
            sweep(graphics, graphics.guiWidth() / 2 - 90 + slot * 20 + 2,
                    graphics.guiHeight() - 16 - 3, heldCooldown);
        }
    }

    /**
     * Slow pulse (one beat every 2.4 s). Eight stripes a few pixels wide on each side fake a
     * gradient with plain fills. {@code fade} is 0 to 1 from the in and out easing.
     */
    private static void edgeGlow(GuiGraphics graphics, int accent, float fade, long now) {
        float pulse = 0.78f + 0.22f * (float) Math.sin(now * (2 * Math.PI / 2400.0));
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int rgb = accent & 0x00FFFFFF;
        for (int i = 0; i < EDGE; i += EDGE_STEP) {
            float falloff = 1f - i / (float) EDGE;
            int alpha = Math.round(GLOW_ALPHA * pulse * fade * falloff * (0.4f + 0.6f * falloff));
            if (alpha <= 0) {
                continue;
            }
            int argb = (alpha << 24) | rgb;
            graphics.fill(0, i, w, i + EDGE_STEP, argb);
            graphics.fill(0, h - i - EDGE_STEP, w, h - i, argb);
            graphics.fill(i, i + EDGE_STEP, i + EDGE_STEP, h - i - EDGE_STEP, argb);
            graphics.fill(w - i - EDGE_STEP, i + EDGE_STEP, w - i, h - i - EDGE_STEP, argb);
        }
    }

    /**
     * A ring of 64 pixels round the crosshair. A faint full circle is the track; the lit arc runs
     * clockwise from the top and loses its far end as time runs out. It brightens in the last
     * three seconds so the end does not come as a surprise.
     */
    private static void ring(GuiGraphics graphics, int accent, float fraction, float fade, float secondsLeft) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int rgb = accent & 0x00FFFFFF;
        int lit = Math.round(fraction * 64);
        int track = (Math.round(52 * fade) << 24) | rgb;
        int bright = (Math.round((secondsLeft < 3f ? 255 : 225) * fade) << 24)
                | StreakBadge.lerp(rgb, 0xFFFFFF, 0.35f);
        HudMath.ring(graphics, cx, cy, RING_RADIUS, RING_RADIUS, track, 1, 1, 64);
        HudMath.ring(graphics, cx, cy, RING_RADIUS, RING_RADIUS, bright, 1, 1, lit);
    }

    /** Vanilla's own cooldown sweep: a pale block over the lower part of the slot that shrinks. */
    private static void sweep(GuiGraphics graphics, int x, int y, float fraction) {
        int top = y + (int) Math.floor(16.0f * (1.0f - fraction));
        graphics.fill(x, top, x + 16, top + (int) Math.ceil(16.0f * fraction), 0x7FFFFFFF);
    }
}
