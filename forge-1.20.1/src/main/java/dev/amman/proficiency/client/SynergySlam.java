package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The synergy discovery slam (1.45.0). When a synergy wakes up (the client notices it in the skills the server
 * syncs, so every line and every server that has Proficiency shows it, with no packet of its own), the skill
 * icons it needs fly in from the screen sides and slam together at the centre: a flash, a tier-2 spark burst, a
 * ring and a 3 px shake of the HUD. The synergy's chip forms from the impact, its name and effect appear on a
 * carved stone plate (MODERN) or in the banner style (CLASSIC), then the chip flies to the bottom-right corner
 * and a "+1 synergy" counter ticks. About 1.8 seconds; several queue. It is a HUD overlay (drawn over open
 * screens too, so a click in the talent tree shows it) and never takes input. It replaces the chat lines the
 * server sends ({@link #swallow}).
 *
 * <p>All timing is closed-form ({@link SynergySlamMath}, {@link SynergySlamQueue}, {@link SynergyTally}, unit
 * tested in core). Setting {@code ui.synergySlam} (and {@code ui.juicyFeedback}); Distortion Effects 0% keeps
 * the flashes and the sounds and drops the flying, the sparks, the ring and the shake.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class SynergySlam {

    private static final SynergySlamQueue<String> QUEUE = new SynergySlamQueue<>();
    private static final SynergyTally TALLY = new SynergyTally();
    private static final SynergyWatch WATCH = new SynergyWatch();

    /** Developer tool: the layout demo takes a frame sheet of the slam through this. Null in play. */
    static Consumer<Minecraft> frameSink;
    /** With a frame sheet running, the slam's clock steps this many ms per rendered frame (0 = real time). */
    static long demoStep;
    static long demoNow;

    private static final int GOLD = 0xF2D98A;
    private static final int RIM = 0xE8B85A;
    private static final int STONE = 0x4C403C;
    private static final int STONE_LIGHT = 0x6E5E56;
    private static final int STONE_DARK = 0x2E2624;
    private static final int OUTLINE = 0x181212;
    private static final int HEAD = 0xDEA06E;
    private static final int PLATE_GOLD = 0xFFD696;
    private static final int INK = 0xEEE2D2;
    private static final int WARM = 0x964614;
    private static final int KICKER = 0xC9B27C;
    private static final int BANNER_TITLE = 0xFFD100;
    private static final int ICON_PX = 32;
    private static final int CHIP_R = 14;

    private static String playing;
    private static int sounds;
    private static Skill[] icons = new Skill[0];
    /** The desc line the next chat message may be, after the awakened line was swallowed. */
    private static String expectDesc;
    private static long expectUntil;

    private SynergySlam() {
    }

    public static boolean on() {
        return ProficiencyClientConfig.uiSynergySlam() && ClickFx.on();
    }

    /** The skills synced from the server: queues a slam for every synergy that woke since the last look. */
    public static void observe(PlayerSkills skills) {
        List<String> awake = new ArrayList<>();
        for (Synergy synergy : Synergies.active(skills)) {
            awake.add(synergy.id());
        }
        List<String> woke = WATCH.observe(awake);
        if (!on()) {
            return;
        }
        for (String id : woke) {
            QUEUE.add(id);
        }
    }

    public static void observeNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            observe(ProficiencyAttachments.of(mc.player));
        }
    }

    /** Leaving a world: no slam carries over, and the next sync is a baseline again. */
    public static void reset() {
        QUEUE.clear();
        TALLY.clear();
        WATCH.reset();
        playing = null;
        expectDesc = null;
    }

    /** Pixels the HUD shifts this frame (the mixin on {@code Gui.render} asks): 0 unless the impact shake runs. */
    public static int shakeX() {
        return QUEUE.current() != null && ClickFx.motion() && on()
                ? SynergySlamMath.shakeX(QUEUE.timeline(), SynergySlamMath.SHAKE_PX) : 0;
    }

    public static int shakeY() {
        return QUEUE.current() != null && ClickFx.motion() && on()
                ? SynergySlamMath.shakeY(QUEUE.timeline(), SynergySlamMath.SHAKE_PX) : 0;
    }

    /**
     * Whether a chat line is the server's old "Synergy awakened" announcement (and the effect line that follows
     * it), which the slam replaces. The effect line is only swallowed right after the announcement, so
     * {@code /skills synergies} still prints its descriptions.
     */
    public static boolean swallow(Component message) {
        if (!on() || !(message.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = contents.getKey();
        if ("proficiency.synergy.awakened".equals(key)) {
            Object[] args = contents.getArgs();
            if (args.length > 0 && args[0] instanceof Component name
                    && name.getContents() instanceof TranslatableContents named) {
                expectDesc = named.getKey() + ".desc";
                expectUntil = now + 1000;
            }
            return true;
        }
        if (expectDesc != null && now < expectUntil && expectDesc.equals(key)) {
            expectDesc = null;
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            frame(event.getGuiGraphics(), mc);
        }
    }

    /** Called by the mixin on {@code Screen.renderWithTooltip}: the slam over an open screen. */
    public static void overScreen(GuiGraphics graphics) {
        frame(graphics, Minecraft.getInstance());
    }

    private static void frame(GuiGraphics g, Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (!on()) {
            if (!QUEUE.idle()) {
                QUEUE.clear();
            }
            return;
        }
        long now = demoStep > 0 ? (demoNow += demoStep) : net.minecraft.Util.getMillis();
        String finished = QUEUE.tick(now);
        if (finished != null) {
            TALLY.land(now);
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35f + 0.1f * Math.min(4, TALLY.count(now) - 1), 0.5f);
        }
        if (!mc.options.hideGui) {
            String id = QUEUE.current();
            if (id != null) {
                Synergy synergy = Synergies.byId(id);
                if (synergy != null) {
                    if (!id.equals(playing)) {
                        playing = id;
                        sounds = 0;
                        icons = iconsOf(synergy);
                    }
                    drawSlam(g, mc, synergy, QUEUE.timeline());
                }
            }
            drawTally(g, mc, now);
        }
        if (frameSink != null) {
            frameSink.accept(mc);
        }
    }

    private static Skill[] iconsOf(Synergy synergy) {
        List<Skill> list = new ArrayList<>();
        for (Synergy.Need need : synergy.requires()) {
            if (!list.contains(need.skill()) && list.size() < SynergySlamMath.MAX_ICONS) {
                list.add(need.skill());
            }
        }
        return list.toArray(new Skill[0]);
    }

    // ---- Drawing ------------------------------------------------------------------------------

    private static void drawSlam(GuiGraphics g, Minecraft mc, Synergy synergy, long t) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int cx = w / 2;
        int cy = Math.round(h * 0.34f);
        boolean motion = ClickFx.motion();
        cues(t);

        // The icons come in from the sides.
        float alpha = SynergySlamMath.iconAlpha(t);
        if (alpha > 0f) {
            float p = motion ? SynergySlamMath.approach(t) : 1f;
            if (!motion) {
                alpha *= Math.min(1f, t / 250f);
            }
            for (int i = 0; i < icons.length; i++) {
                int dx = SynergySlamMath.fromX(i, icons.length);
                int dy = SynergySlamMath.fromY(i, icons.length);
                // Ghosts first: the faster it moves the longer the smear behind it.
                if (motion && t < SynergySlamMath.IMPACT_MS) {
                    for (int ghost = 3; ghost >= 1; ghost--) {
                        float gp = SynergySlamMath.approach(t - ghost * 22L);
                        if (gp > 0f && gp < p - 0.004f) {
                            drawIcon(g, icons[i], cx, cy, dx, dy, gp, alpha * (0.34f - ghost * 0.08f), w, h);
                        }
                    }
                }
                drawIcon(g, icons[i], cx, cy, dx, dy, p, alpha, w, h);
            }
        }

        long since = t - SynergySlamMath.IMPACT_MS;
        if (since >= 0) {
            impact(g, w, h, cx, cy, since, synergy, motion);
        }

        float scale = SynergySlamMath.chipScale(t);
        if (scale > 0.02f) {
            float flight = motion ? SynergySlamMath.flight(t) : 0f;
            float[] pos = chipPosition(cx, cy, w, h, flight);
            if (motion && flight > 0f && flight < 1f) {
                trail(g, cx, cy, w, h, flight);
            }
            float fade = motion ? 1f : 1f - SynergySlamMath.clamp01((t - SynergySlamMath.FLY_START) / 450f);
            drawChip(g, pos[0], pos[1], scale, fade, since);
        }

        float plate = SynergySlamMath.plateAlpha(t);
        if (plate > 0.02f) {
            drawPlate(g, mc, synergy, cx, cy + 27, plate, SynergySlamMath.plateSettle(t));
        }
    }

    /** One-shot sounds at their moments, so a frame never plays one twice. */
    private static void cues(long t) {
        if ((sounds & 1) == 0 && t >= 0) {
            sounds |= 1;
            play(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.55f);
        }
        if ((sounds & 2) == 0 && t >= SynergySlamMath.IMPACT_MS) {
            sounds |= 2;
            play(SoundEvents.ANVIL_PLACE, 1.45f, 0.5f);
            play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
        }
        if ((sounds & 4) == 0 && t >= SynergySlamMath.PLATE_IN_START) {
            sounds |= 4;
            play(CHIME, 1.5f, 0.5f);
        }
    }

    private static final net.minecraft.sounds.SoundEvent CHIME = SoundEvents.NOTE_BLOCK_CHIME.get();

    private static void play(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private static void drawIcon(GuiGraphics g, Skill skill, int cx, int cy, int dx, int dy, float p, float alpha,
            int w, int h) {
        int sep = ICON_PX / 2 - 3;
        float x = cx + dx * (sep + (1f - p) * (cx + ICON_PX));
        float y = cy + dy * (sep + (1f - p) * (cy + ICON_PX));
        g.pose().pushPose();
        g.pose().translate(x - ICON_PX / 2f, y - ICON_PX / 2f, 0);
        g.pose().scale(2f, 2f, 1f);
        if (SkillIcons.enabled()) {
            SkillIcons.draw(g, skill, 0, 0, 16, Math.round(alpha * 255));
        } else {
            int c = SkillPalette.accent(skill.category());
            g.fill(2, 2, 14, 14, ClickFx.argb(alpha, OUTLINE));
            g.fill(3, 3, 13, 13, ClickFx.argb(alpha, c));
        }
        g.pose().popPose();
    }

    private static void impact(GuiGraphics g, int w, int h, int cx, int cy, long since, Synergy synergy,
            boolean motion) {
        float flash = SynergySlamMath.flash(SynergySlamMath.IMPACT_MS + since);
        if (flash > 0f) {
            // A tint over the whole screen, a bar through the impact, a hot square at its heart.
            g.fill(0, 0, w, h, ClickFx.argb(0.10f * flash, 0xFFFFFF));
            g.fill(0, cy - 2, w, cy + 2, ClickFx.argb(0.55f * flash, 0xFFF4D0));
            g.fill(cx - 22, cy - 22, cx + 22, cy + 22, ClickFx.argb(0.7f * flash, 0xFFFFFF));
        }
        if (!motion) {
            return;
        }
        int seed = synergy.id().hashCode();
        if (since < ClickFxMath.BURST_MS) {
            ClickFx.drawSparks(g, cx, cy, since, seed, 2, 84f, GOLD);
        }
        float ringAlpha = ClickFxMath.ringAlpha(since);
        if (ringAlpha > 0f) {
            float r = ClickFxMath.ringRadius(since, 8f, 78f);
            HudMath.ring(g, cx, cy, r, r, ClickFx.argb(ringAlpha * 0.9f, 0xFFE9A8), 2, 1, 64);
            float r2 = ClickFxMath.ringRadius(Math.max(0, since - 90), 6f, 52f);
            HudMath.ring(g, cx, cy, r2, r2, ClickFx.argb(ringAlpha * 0.6f, 0xFFFFFF), 1, 1, 64);
        }
    }

    /** The chip's centre: on the impact, then along a curve that rises first and drops into the corner. */
    private static float[] chipPosition(int cx, int cy, int w, int h, float flight) {
        float tx = w - 14;
        float ty = h - 14;
        float qx = cx + (tx - cx) * 0.25f;
        float qy = cy - 34;
        float u = 1f - flight;
        return new float[] {u * u * cx + 2 * u * flight * qx + flight * flight * tx,
                u * u * cy + 2 * u * flight * qy + flight * flight * ty};
    }

    private static void trail(GuiGraphics g, int cx, int cy, int w, int h, float flight) {
        for (int i = 1; i <= 5; i++) {
            float back = flight - i * 0.035f;
            if (back <= 0f) {
                break;
            }
            float[] p = chipPosition(cx, cy, w, h, back);
            float a = 0.55f - i * 0.1f;
            int s = i < 3 ? 2 : 1;
            int x = Math.round(p[0]);
            int y = Math.round(p[1]);
            g.fill(x - s, y - s, x + s, y + s, ClickFx.argb(a, i == 1 ? 0xFFFFFF : GOLD));
        }
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy + 0.5);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
        }
    }

    /** The synergy's chip: a gold-rimmed medallion with the skills it joins inside and a glint. */
    private static void drawChip(GuiGraphics g, float x, float y, float scale, float fade, long since) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        // A soft light round it while it settles.
        disc(g, 0, 0, CHIP_R + 10, ClickFx.argb(0.07f * fade, GOLD));
        disc(g, 0, 0, CHIP_R + 6, ClickFx.argb(0.10f * fade, GOLD));
        disc(g, 0, 0, CHIP_R + 1, ClickFx.argb(fade, OUTLINE));
        disc(g, 0, 0, CHIP_R, ClickFx.argb(fade, RIM));
        disc(g, 0, 0, CHIP_R - 2, ClickFx.argb(fade, 0x2A2220));
        disc(g, 0, 1, CHIP_R - 3, ClickFx.argb(fade, 0x3C302C));
        int n = icons.length;
        int size = n >= 3 ? 10 : 12;
        for (int i = 0; i < n; i++) {
            int ix;
            int iy;
            if (n == 1) {
                ix = -size / 2;
                iy = -size / 2;
            } else if (n == 2) {
                ix = i == 0 ? -size : 0;
                iy = -size / 2;
            } else {
                ix = i == 0 ? -size : i == 1 ? 0 : -size / 2;
                iy = i == 2 ? -size - 1 : 0;
            }
            if (SkillIcons.enabled()) {
                SkillIcons.draw(g, icons[i], ix, iy, size, Math.round(fade * 255));
            } else {
                int c = SkillPalette.accent(icons[i].category());
                g.fill(ix + 1, iy + 1, ix + size - 1, iy + size - 1, ClickFx.argb(fade, c));
            }
        }
        if (n == 0) {
            // No skill to carry (Renaissance joins every tree): a four-point star.
            g.fill(-1, -7, 1, 7, ClickFx.argb(fade, 0xFFE9A8));
            g.fill(-7, -1, 7, 1, ClickFx.argb(fade, 0xFFE9A8));
            g.fill(-3, -3, 3, 3, ClickFx.argb(fade, 0xFFF4D0));
        }
        // A glint that sweeps once as the chip settles.
        float sweep = SynergySlamMath.clamp01((since - 150) / 260f);
        if (sweep > 0f && sweep < 1f) {
            int gx = Math.round(-CHIP_R + sweep * 2 * CHIP_R);
            g.fill(gx, -CHIP_R + 4, gx + 2, -CHIP_R + 9, ClickFx.argb(0.8f * (1f - sweep) * fade, 0xFFFFFF));
            g.fill(gx - 1, -CHIP_R + 6, gx + 3, -CHIP_R + 7, ClickFx.argb(0.8f * (1f - sweep) * fade, 0xFFFFFF));
        }
        g.pose().popPose();
    }

    private static void drawPlate(GuiGraphics g, Minecraft mc, Synergy synergy, int cx, int top, float alpha,
            float settle) {
        Font font = mc.font;
        String kicker = Component.translatable("proficiency.synergy.slam.kicker").getString();
        String name = Component.translatable(synergy.nameKey()).getString();
        String desc = Component.translatable(synergy.descriptionKey()).getString();
        boolean modern = ProficiencyClientConfig.uiStyle() == UiStyle.MODERN;
        int screenW = g.guiWidth();
        float nameScale = 1.5f;
        int widest = Math.max(Math.round(font.width(name) * nameScale), font.width(kicker));
        // As wide as its longest line wants, up to 250: a long effect wraps to two lines, never a screen-wide bar.
        int plateW = Math.max(150, Math.min(Math.min(250, screenW - 12), Math.max(widest, Math.min(font.width(desc), 230)) + 30));
        float fit = Math.min(nameScale, (plateW - 22) / (float) Math.max(1, font.width(name)));
        nameScale = Math.max(0.8f, fit);
        List<String> descLines = TextFit.wrapLimited(font, desc, plateW - 20, 2, new boolean[1]);
        int lines = Math.max(1, Math.min(2, descLines.size()));
        int descTop = 15 + Math.round(9 * nameScale) + 5;
        int plateH = descTop + lines * 10 + 6;
        int lift = Math.round((1f - settle) * 10f);
        int a = Math.round(alpha * 255);
        int y = top + lift;
        int x = cx - plateW / 2;
        if (modern) {
            drawStone(g, x, y, plateW, plateH, alpha, synergy.id().hashCode());
        } else if (a >= 8) {
            // The classic banner has no panel: rules either side of the kicker.
            int kw = font.width(kicker);
            int ky = y + 5;
            g.fill(cx - kw / 2 - 52, ky + 4, cx - kw / 2 - 6, ky + 5, ClickFx.argb(alpha * 0.8f, KICKER));
            g.fill(cx + kw / 2 + 6, ky + 4, cx + kw / 2 + 52, ky + 5, ClickFx.argb(alpha * 0.8f, KICKER));
        }
        if (a < 8) {
            return;
        }
        int kickerColour = modern ? HEAD : KICKER;
        int nameColour = modern ? PLATE_GOLD : BANNER_TITLE;
        int descColour = modern ? INK : 0xE6E6E6;
        String kickerText = modern ? kicker.toUpperCase(Locale.ROOT) : kicker;
        String clippedKicker = TextFit.clip(font, kickerText, plateW - 14);
        g.drawString(font, clippedKicker, cx - font.width(clippedKicker) / 2, y + 5, (a << 24) | kickerColour, modern);
        g.pose().pushPose();
        g.pose().translate(cx, y + 15, 0);
        g.pose().scale(nameScale, nameScale, 1f);
        String clippedName = TextFit.clip(font, name, Math.round((plateW - 14) / nameScale));
        g.drawString(font, clippedName, -font.width(clippedName) / 2, 0, (a << 24) | nameColour, true);
        g.pose().popPose();
        for (int i = 0; i < lines && i < descLines.size(); i++) {
            String line = descLines.get(i);
            g.drawString(font, line, cx - font.width(line) / 2, y + descTop + i * 10, (a << 24) | descColour, true);
        }
    }

    /** The carved plate: the Skillstone's material, drawn from fills (a slab, bevel, grain, rivets, an ember glow). */
    private static void drawStone(GuiGraphics g, int x, int y, int w, int h, float alpha, int seed) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ClickFx.argb(alpha, OUTLINE));
        g.fill(x, y, x + w, y + h, ClickFx.argb(alpha, STONE));
        // Grain: a fixed scatter of lighter and darker flecks.
        int state = seed * 0x9E3779B1 + 12345;
        for (int i = 0; i < 46; i++) {
            state = state * 1103515245 + 12345;
            int px = x + 3 + ((state >>> 8) & 0xFFFF) % Math.max(1, w - 6);
            state = state * 1103515245 + 12345;
            int py = y + 3 + ((state >>> 8) & 0xFFFF) % Math.max(1, h - 6);
            g.fill(px, py, px + 2, py + 1, ClickFx.argb(alpha * 0.5f, (i & 1) == 0 ? STONE_LIGHT : STONE_DARK));
        }
        // Bevel: lit from the top left.
        g.fill(x, y, x + w, y + 1, ClickFx.argb(alpha, STONE_LIGHT));
        g.fill(x, y, x + 1, y + h, ClickFx.argb(alpha, STONE_LIGHT));
        g.fill(x, y + h - 1, x + w, y + h, ClickFx.argb(alpha, STONE_DARK));
        g.fill(x + w - 1, y, x + w, y + h, ClickFx.argb(alpha, STONE_DARK));
        // An inset groove and a rune bar under the kicker.
        ClickFx.frame(g, x + 4, y + 4, w - 8, h - 8, ClickFx.argb(alpha * 0.8f, STONE_DARK));
        g.fill(x + 12, y + 13, x + w - 12, y + 14, ClickFx.argb(alpha * 0.55f, STONE_DARK));
        g.fill(x + 12, y + 14, x + w - 12, y + 15, ClickFx.argb(alpha * 0.35f, STONE_LIGHT));
        // Rivets in the corners.
        for (int i = 0; i < 4; i++) {
            int rx = i % 2 == 0 ? x + 6 : x + w - 8;
            int ry = i < 2 ? y + 6 : y + h - 8;
            g.fill(rx, ry, rx + 2, ry + 2, ClickFx.argb(alpha, 0x8C7E76));
            g.fill(rx + 1, ry + 1, rx + 2, ry + 2, ClickFx.argb(alpha, STONE_DARK));
        }
        // The fire well's warmth, spilling under the plate.
        for (int i = 0; i < 3; i++) {
            g.fill(x + 6 + i * 2, y + h + 1 + i, x + w - 6 - i * 2, y + h + 2 + i,
                    ClickFx.argb(alpha * (0.40f - i * 0.12f), WARM));
        }
    }

    private static void drawTally(GuiGraphics g, Minecraft mc, long now) {
        int count = TALLY.count(now);
        float alpha = TALLY.alpha(now);
        if (count <= 0 || alpha <= 0.02f) {
            return;
        }
        Font font = mc.font;
        String text = Component.translatable(count == 1 ? "proficiency.synergy.slam.tally"
                : "proficiency.synergy.slam.tally.many", count).getString();
        int a = Math.round(alpha * 255);
        float pop = TALLY.pop(now);
        int right = g.guiWidth() - 4;
        int cy = g.guiHeight() - 14;
        int textW = font.width(text);
        int pillW = textW + 28;
        int left = right - pillW;
        g.fill(left - 1, cy - 8, right + 1, cy + 8, ClickFx.argb(alpha * 0.9f, OUTLINE));
        g.fill(left, cy - 7, right, cy + 7, ClickFx.argb(alpha * 0.85f, 0x2A2220));
        ClickFx.frame(g, left, cy - 7, pillW, 14, ClickFx.argb(alpha, RIM));
        // The little chip at the right end: where the big one lands.
        int ix = g.guiWidth() - 14;
        disc(g, ix, cy, 5, ClickFx.argb(alpha, RIM));
        disc(g, ix, cy, 3, ClickFx.argb(alpha, 0x3C302C));
        g.fill(ix - 1, cy - 1, ix + 1, cy + 1, ClickFx.argb(alpha, 0xFFF4D0));
        if (pop > 0f && ClickFx.motion()) {
            float r = 6f + 10f * (1f - pop);
            HudMath.ring(g, ix, cy, r, r, ClickFx.argb(pop * alpha, 0xFFE9A8), 1, 1, 48);
        }
        if (a >= 8) {
            g.pose().pushPose();
            g.pose().translate(left + 8 + textW / 2f, cy - 3.5f, 0);
            float s = 1f + 0.3f * pop;
            g.pose().scale(s, s, 1f);
            g.drawString(font, text, -textW / 2, -Math.round(3.5f * (s - 1f)), (a << 24) | GOLD, true);
            g.pose().popPose();
        }
    }
}
