package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.coop.CoopBatch;
import dev.amman.proficiency.coop.CoopProjection;
import dev.amman.proficiency.coop.CoopTrickle;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Co-op XP streams (1.45.0), the client half. When a partner in company range earns XP, a short trickle of their
 * dots arcs from where they are on the screen (or from the screen edge toward them) into your own skill bar when
 * company pays you, or rises as a faint trail to their nameplate when it does not. A row of small partner pips
 * (their face, ringed in their colour) sits left of the skill bar while you are in company; a pip pulses when its
 * partner's dots set off. When your own grants carry company XP, a soft shared plink.
 *
 * <p>Bounded: one batch a second per partner from the server, at most six dots a second from one partner and 18 in
 * the air ({@link CoopTrickle}); a few {@code fill} calls per dot. {@code ui.coopStreams} off draws and plays
 * nothing. Without motion ({@code ui.juicyFeedback} off or Distortion Effects 0%) the dots do not fly: the pips
 * still pulse and the sounds still play.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's copySharedHearth); the faces are each
 * line's {@link CoopHeads}.
 */
public final class CoopStreams {

    /** Pip size (face) and its ring. */
    private static final int FACE = 8;
    private static final int PIP = FACE + 2;
    private static final int PIP_GAP = 2;
    /** Most pips drawn; more shows "+N". */
    private static final int PIPS_SHOWN = 4;
    private static final long PIP_IN_MS = 260;
    private static final long PIP_OUT_MS = 320;
    private static final long PULSE_MS = 450;
    private static final long PLINK_GAP_MS = 900;
    /** How far in from the screen edge an off-screen partner's dots start. */
    private static final int EDGE_INSET = 10;

    private record Pip(UUID id, String name, int colour, long bornAt, long goneAt) {
        Pip gone(long now) {
            return new Pip(id, name, colour, bornAt, now);
        }
    }

    private static final class Dot {
        final UUID id;
        final int colour;
        final long launch;
        final long flight;
        final float lane;
        final boolean pays;
        final double wx;
        final double wy;
        final double wz;
        /** GUI start (and, for a faint dot, end), fixed when the dot sets off. */
        float sx = Float.NaN;
        float sy;
        float ex;
        float ey;
        boolean visible = true;
        boolean first;

        Dot(UUID id, int colour, long launch, float lane, boolean pays, double wx, double wy, double wz) {
            this.id = id;
            this.colour = colour;
            this.launch = launch;
            this.flight = CoopTrickle.FLIGHT_MS + (long) (lane * 70);
            this.lane = lane;
            this.pays = pays;
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
        }
    }

    private static final CoopTrickle TRICKLE = new CoopTrickle();
    private static final List<Dot> DOTS = new ArrayList<>();
    private static List<Pip> pips = new ArrayList<>();
    private static final java.util.Map<UUID, Long> PULSE = new java.util.HashMap<>();
    private static long lastPlink = Long.MIN_VALUE / 2;
    private static long landFlashUntil;
    private static int landColour = 0xFFFFFF;
    private static long ghostUntil;
    private static int lanes;

    private CoopStreams() {
    }

    /** Logout: nothing seen on one server shows on the next. */
    public static void reset() {
        DOTS.clear();
        pips = new ArrayList<>();
        PULSE.clear();
        TRICKLE.clear();
        ghostUntil = 0;
        landFlashUntil = 0;
    }

    /** Client thread: one second of company from the server. */
    public static void accept(CoopBatch batch) {
        long now = Util.getMillis();
        updatePips(batch.partners(), now);
        if (!ProficiencyClientConfig.uiCoopStreams()) {
            DOTS.clear();
            return;
        }
        for (CoopBatch.Stream stream : batch.streams()) {
            List<Long> times = TRICKLE.schedule(stream.id(), stream.dots(), now);
            if (times.isEmpty()) {
                continue;
            }
            int colour = CoopTrickle.colour(stream.id());
            boolean firstOfBatch = true;
            for (long at : times) {
                // Golden-ratio lanes: a trickle fans out instead of retracing one line.
                float lane = (float) (((lanes++ * 0.6180339887) % 1.0) * 2 - 1);
                Dot dot = new Dot(stream.id(), colour, at, lane, stream.pays(), stream.x(), stream.y(), stream.z());
                dot.first = firstOfBatch;
                firstOfBatch = false;
                DOTS.add(dot);
            }
            PULSE.put(stream.id(), now);
            if (stream.pays()) {
                ghostUntil = Math.max(ghostUntil, times.get(times.size() - 1) + CoopTrickle.FLIGHT_MS + 500);
            }
        }
        if (batch.ownBonus() > 0f && now - lastPlink >= PLINK_GAP_MS) {
            lastPlink = now;
            // The shared plink: company just added to your own bar. Two soft notes, a fifth apart.
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9f, 0.22f);
            play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.16f);
            for (Pip pip : pips) {
                if (pip.goneAt() == 0) {
                    PULSE.put(pip.id(), now);
                }
            }
            landFlashUntil = now + 220;
            landColour = 0xFFFFFF;
        }
    }

    private static void updatePips(List<CoopBatch.Partner> partners, long now) {
        List<Pip> next = new ArrayList<>();
        boolean joined = false;
        for (CoopBatch.Partner partner : partners) {
            Pip had = null;
            for (Pip pip : pips) {
                if (pip.id().equals(partner.id()) && pip.goneAt() == 0) {
                    had = pip;
                }
            }
            if (had == null) {
                joined = true;
                next.add(new Pip(partner.id(), partner.name(), CoopTrickle.colour(partner.id()), now, 0));
            } else {
                next.add(had);
            }
        }
        for (Pip pip : pips) {
            boolean stays = false;
            for (CoopBatch.Partner partner : partners) {
                stays |= partner.id().equals(pip.id());
            }
            if (!stays && (pip.goneAt() == 0 || now - pip.goneAt() < PIP_OUT_MS)) {
                next.add(pip.goneAt() == 0 ? pip.gone(now) : pip);
            }
        }
        pips = next;
        if (joined && ProficiencyClientConfig.uiCoopStreams()) {
            // Someone came into company: a low, warm note.
            play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 0.18f);
        }
    }

    /** Called from the skill HUD every frame the GUI is shown. */
    public static void render(GuiGraphics graphics, Minecraft mc) {
        if (!ProficiencyClientConfig.uiCoopStreams() || mc.player == null) {
            DOTS.clear();
            return;
        }
        long now = Util.getMillis();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int endX = SkillHud.dotAnchorX(width, now);
        int endY = SkillHud.dotAnchorY(height, now);
        boolean motion = ClickFx.motion();

        drawGhostBar(graphics, width, endY, now);
        drawPips(graphics, mc, width, endY, now, motion);
        if (DOTS.isEmpty()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        double fov = mc.options.fov().get();
        for (int i = DOTS.size() - 1; i >= 0; i--) {
            Dot dot = DOTS.get(i);
            double t = (now - dot.launch) / (double) dot.flight;
            if (t >= 1) {
                if (dot.pays && dot.visible) {
                    landFlashUntil = Math.max(landFlashUntil, now + 180);
                    landColour = dot.colour & 0xFFFFFF;
                    if (dot.first) {
                        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.65f + dot.lane * 0.1f, 0.10f);
                    }
                }
                DOTS.remove(i);
                continue;
            }
            if (t < 0) {
                continue;
            }
            if (Float.isNaN(dot.sx)) {
                start(dot, mc, camera, eye, fov, width, height);
            }
            if (!motion || !dot.visible) {
                continue;
            }
            if (dot.pays) {
                drawFlight(graphics, dot, t, endX, endY);
            } else {
                drawFaint(graphics, dot, t);
            }
        }
        float flash = landFlashUntil > now ? (landFlashUntil - now) / 180f : 0f;
        if (flash > 0) {
            int a = (int) (220 * Math.min(1f, flash));
            graphics.fill(endX - 3, endY - 1, endX + 3, endY + 1, ((a / 2) << 24) | landColour);
            graphics.fill(endX - 1, endY - 3, endX + 1, endY + 3, (a << 24) | landColour);
            graphics.fill(endX - 1, endY - 1, endX + 1, endY + 1, (a << 24) | 0xFFFFFF);
        }
    }

    /** Fixes where a dot sets off: the partner on screen (live if loaded, else the server's point) or the edge. */
    private static void start(Dot dot, Minecraft mc, Camera camera, Vec3 eye, double fov, int width, int height) {
        double x = dot.wx;
        double y = dot.wy;
        double z = dot.wz;
        double top = y + 1.2;
        Player live = mc.level == null ? null : mc.level.getPlayerByUUID(dot.id);
        if (live != null) {
            x = live.getX();
            y = live.getY() + live.getBbHeight() * 0.6;
            z = live.getZ();
            top = live.getY() + live.getBbHeight() + 0.5;
        }
        CoopProjection.Point from = CoopProjection.project(eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot(),
                fov, width, height, x, y, z, EDGE_INSET);
        dot.sx = from.x();
        dot.sy = from.y();
        if (!dot.pays) {
            // A faint trail only makes sense where you can see them.
            CoopProjection.Point head = CoopProjection.project(eye.x, eye.y, eye.z, camera.getYRot(),
                    camera.getXRot(), fov, width, height, x, top, z, EDGE_INSET);
            dot.visible = from.onScreen() && head.onScreen();
            dot.ex = head.x();
            dot.ey = head.y();
        }
    }

    /**
     * A paid dot: DotFlight's arc from the partner into the bar. A 3 px head in their colour with a white core and a
     * soft halo, and a fading four-step trail, so a trickle reads as theirs against grass, sky or stone.
     */
    private static void drawFlight(GuiGraphics graphics, Dot dot, double t, int endX, int endY) {
        float controlX = dot.sx + (endX - dot.sx) * 0.45f + dot.lane * 18f;
        float controlY = Math.min(dot.sy, endY) - 26f - dot.lane * 10f;
        int rgb = dot.colour & 0xFFFFFF;
        for (int step = 4; step >= 0; step--) {
            double e = XpGainDots.ease(t - step * 0.035);
            if (e <= 0) {
                continue;
            }
            double u = 1 - e;
            int x = (int) Math.round(u * u * dot.sx + 2 * u * e * controlX + e * e * endX);
            int y = (int) Math.round(u * u * dot.sy + 2 * u * e * controlY + e * e * endY);
            if (step == 0) {
                graphics.fill(x - 2, y - 2, x + 2, y + 2, 0x3C000000 | rgb);
                graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF000000 | rgb);
                graphics.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
            } else {
                int a = step == 1 ? 200 : step == 2 ? 140 : step == 3 ? 85 : 40;
                int size = step <= 2 ? 2 : 1;
                graphics.fill(x, y, x + size, y + size, (a << 24) | rgb);
            }
        }
    }

    /**
     * An unpaid dot: a mote rising from the partner's chest to their nameplate, swaying, fading in and out. Softer
     * than a paid dot (no core, no halo) so it reads as "they are earning", not "this is coming to you".
     */
    private static void drawFaint(GuiGraphics graphics, Dot dot, double t) {
        double e = 1 - Math.pow(1 - t, 2);
        float sway = (float) Math.sin(t * Math.PI * 2 + dot.lane * 3) * 3f;
        int x = Math.round(dot.sx + (dot.ex - dot.sx) * (float) e + sway + dot.lane * 5f);
        int y = Math.round(dot.sy + (dot.ey - dot.sy) * (float) e);
        double life = t < 0.15 ? t / 0.15 : 1 - (t - 0.15) / 0.85;
        int a = (int) (175 * Math.max(0, life));
        if (a < 6) {
            return;
        }
        int rgb = dot.colour & 0xFFFFFF;
        graphics.fill(x - 1, y - 1, x + 1, y + 1, (a << 24) | rgb);
        graphics.fill(x - 1, y + 2, x, y + 3, ((a / 2) << 24) | rgb);
        graphics.fill(x - 1, y + 4, x, y + 5, ((a / 4) << 24) | rgb);
    }

    /**
     * While paid dots are coming and the skill line is not up, a faint track where the bar sits, so they land in
     * something.
     */
    private static void drawGhostBar(GuiGraphics graphics, int width, int barY, long now) {
        if (ghostUntil <= now || SkillHud.lineUp(now)) {
            return;
        }
        long left = ghostUntil - now;
        float a = Math.min(1f, left / 400f) * 0.55f;
        int alpha = (int) (255 * a);
        if (alpha < 6) {
            return;
        }
        int x0 = SkillHud.barLeft(width);
        graphics.fill(x0, barY - 1, x0 + SkillHud.barWidth(), barY + 1, (alpha << 24) | 0x23282F);
    }

    /** The partner pips, right to left from the bar's left end, each popping in and fading out. */
    private static void drawPips(GuiGraphics graphics, Minecraft mc, int width, int barY, long now, boolean motion) {
        if (pips.isEmpty()) {
            return;
        }
        int right = SkillHud.barLeft(width) - 6;
        int y = barY - PIP / 2;
        int shown = 0;
        int hidden = 0;
        List<Pip> keep = new ArrayList<>();
        for (Pip pip : pips) {
            if (pip.goneAt() != 0 && now - pip.goneAt() >= PIP_OUT_MS) {
                continue;
            }
            keep.add(pip);
            if (shown >= PIPS_SHOWN) {
                hidden++;
                continue;
            }
            int x = right - (shown + 1) * PIP - shown * PIP_GAP;
            shown++;
            float in = Math.min(1f, (now - pip.bornAt()) / (float) PIP_IN_MS);
            float out = pip.goneAt() == 0 ? 1f : 1f - (now - pip.goneAt()) / (float) PIP_OUT_MS;
            float alpha = Math.max(0f, Math.min(in, out));
            float scale = motion ? (pip.goneAt() == 0 ? backOut(in) : 0.6f + 0.4f * out) : 1f;
            Long pulsedAt = PULSE.get(pip.id());
            float pulse = pulsedAt == null ? 0f : Math.max(0f, 1f - (now - pulsedAt) / (float) PULSE_MS);
            drawPip(graphics, mc, pip, x, y, alpha, scale, pulse);
        }
        pips = keep;
        if (hidden > 0) {
            String more = "+" + hidden;
            int x = right - shown * (PIP + PIP_GAP) - mc.font.width(more) - 1;
            graphics.drawString(mc.font, more, x, y + 1, 0xC0DDE2E8, true);
        }
    }

    private static void drawPip(GuiGraphics graphics, Minecraft mc, Pip pip, int x, int y, float alpha, float scale,
            float pulse) {
        if (alpha <= 0.02f) {
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        float cx = x + PIP / 2f;
        float cy = y + PIP / 2f;
        pose.translate(cx, cy, 0);
        float s = scale * (1f + 0.18f * pulse);
        pose.scale(s, s, 1f);
        pose.translate(-PIP / 2f, -PIP / 2f, 0);
        int rgb = pip.colour() & 0xFFFFFF;
        int ring = (int) (alpha * (170 + 85 * pulse));
        if (pulse > 0) {
            int glow = (int) (alpha * 90 * pulse);
            graphics.fill(-1, -1, PIP + 1, PIP + 1, (glow << 24) | rgb);
        }
        graphics.fill(0, 0, PIP, PIP, (ring << 24) | rgb);
        graphics.fill(1, 1, PIP - 1, PIP - 1, ((int) (alpha * 230) << 24) | 0x121418);
        if (!CoopHeads.draw(graphics, mc, pip.id(), 1, 1, FACE, alpha)) {
            String initial = pip.name().isEmpty() ? "?" : pip.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            pose.pushPose();
            pose.translate(PIP / 2f, 1.5f, 0);
            pose.scale(0.75f, 0.75f, 1f);
            int w = mc.font.width(initial);
            graphics.drawString(mc.font, initial, -w / 2, 0, ((int) (alpha * 255) << 24) | rgb, false);
            pose.popPose();
        }
        pose.popPose();
    }

    private static float backOut(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static void play(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        }
    }

    /** For the layout demo and the tests of the drawing: how many dots are in the air or waiting. */
    public static int dotsInAir() {
        return DOTS.size();
    }
}
