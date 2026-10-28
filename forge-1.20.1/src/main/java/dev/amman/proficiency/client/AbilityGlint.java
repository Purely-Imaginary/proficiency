package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The ability-ready glint (1.37.0): the moment an ability comes off cooldown its HUD presence pops, a
 * diagonal glint sweeps across the icon and a soft chime plays. The detection is {@link ReadyGlint.Detector}
 * (cooling to ready only: what was ready at login stays silent) fed once a frame from {@code SkillHud};
 * the Waywheel belt token ({@code WaywheelBelt}) and the classic hotbar slot ({@link AbilityFx}) both read
 * {@link #age} to draw. The chime plays only for an ability that is on show (on 1.21.1 also pinned under MODERN; else the
 * one the held item triggers), at most once per {@link ReadyGlint#CHIME_GAP_MS}. Setting {@code ui.abilityGlint}
 * (and {@code ui.juicyFeedback}); Distortion Effects 0% drops the pop and the sweep, keeps the flash and the chime.
 */
public final class AbilityGlint {

    private static final ReadyGlint.Detector DETECTOR = new ReadyGlint.Detector(Skill.VALUES.length);
    private static final ReadyGlint.Gate GATE = new ReadyGlint.Gate();
    private static final long[] AT = new long[Skill.VALUES.length];
    private static final long NEVER = Long.MIN_VALUE / 2;

    static {
        java.util.Arrays.fill(AT, NEVER);
    }

    private AbilityGlint() {
    }

    public static boolean on() {
        return ProficiencyClientConfig.uiAbilityGlint() && ClickFx.on();
    }

    /** The one clock for the glint: the HUD passes System time, the belt the screen clock, so neither is used. */
    private static long clock() {
        return net.minecraft.Util.getMillis();
    }

    public static void reset() {
        DETECTOR.clear();
        GATE.clear();
        java.util.Arrays.fill(AT, NEVER);
    }

    /** Reads every skill once. {@code held} is the skill the held item triggers (the classic HUD's slot). */
    public static void observe(Player player, PlayerSkills skills, @Nullable Skill held) {
        long now = clock();
        long game = player.level().getGameTime();
        boolean on = on();
        for (Skill skill : Skill.VALUES) {
            boolean unlocked = ActiveService.unlocked(skills, skill);
            boolean cooling = unlocked && skills.cooldownRemaining(skill, game) > 0;
            if (!DETECTOR.observe(skill.ordinal(), unlocked, cooling) || !on) {
                continue;
            }
            AT[skill.ordinal()] = now;
            if (onShow(player, skill, held) && GATE.allow(now)) {
                chime(skill);
            }
        }
    }

    private static boolean onShow(Player player, Skill skill, @Nullable Skill held) {
        // The Forge line has the classic HUD only (no Waywheel belt): the held item's hotbar slot.
        return skill == held && !player.getMainHandItem().isEmpty();
    }

    /** Ms since {@code skill}'s ability came ready, or a huge number when it has not just done so. */
    public static long age(Skill skill) {
        long now = clock();
        if (!on()) {
            return Long.MAX_VALUE / 2;
        }
        long at = AT[skill.ordinal()];
        return at == NEVER ? Long.MAX_VALUE / 2 : now - at;
    }

    /** A small, soft two-note chime: glassy and short, well under the click kit's success chime. */
    private static void chime(Skill skill) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        float pitch = 1.45f + 0.05f * (skill.ordinal() % 5);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.5f));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_CHIME.value(), pitch * 1.2f, 0.22f));
    }

    /**
     * The diagonal band across a {@code w} by {@code h} box at (x, y), {@code t} ms into the glint. {@code round}
     * is the radius of a circular face to clip to (0 for the plain rectangle).
     */
    public static void sweep(GuiGraphics g, int x, int y, int w, int h, long t, int round) {
        sweep(g, net.minecraft.client.renderer.RenderType.gui(), x, y, w, h, t, round);
    }

    /** The same in {@code layer}: the hotbar slot passes {@code guiOverlay}, like vanilla's cooldown overlay. */
    public static void sweep(GuiGraphics g, net.minecraft.client.renderer.RenderType layer, int x, int y, int w, int h,
            long t, int round) {
        float k = ReadyGlint.sweep(t);
        if (k < 0f) {
            return;
        }
        float alpha = ReadyGlint.sweepAlpha(t);
        int slant = h / 2;
        float centre = -slant + k * (w + 2 * slant);
        int band = Math.max(3, w / 4);
        int cx = x + w / 2;
        for (int row = 0; row < h; row++) {
            int from = x + Math.round(centre - row / 2f);
            int to = from + band;
            int lo = x;
            int hi = x + w;
            if (round > 0) {
                int half = dev.amman.proficiency.client.WaywheelMath.chord(round, row - (h / 2 - round));
                if (half <= 0) {
                    continue;
                }
                lo = Math.max(lo, cx - half);
                hi = Math.min(hi, cx + half);
            }
            from = Math.max(from, lo);
            to = Math.min(to, hi);
            if (to > from) {
                g.fill(layer, from, y + row, to, y + row + 1, ClickFx.argb(alpha * 0.85f, 0xFFFFF0));
                // A faint wider shoulder either side, so the band reads as light and not as a stripe.
                int sl = Math.max(lo, from - 2);
                int sr = Math.min(hi, to + 2);
                g.fill(layer, sl, y + row, from, y + row + 1, ClickFx.argb(alpha * 0.30f, 0xFFFFF0));
                g.fill(layer, to, y + row, sr, y + row + 1, ClickFx.argb(alpha * 0.30f, 0xFFFFF0));
            }
        }
    }
}
