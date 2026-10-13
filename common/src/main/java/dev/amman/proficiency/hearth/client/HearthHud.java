package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.client.TooltipDetail;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.skill.SkillCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The hearth glyph on the HUD (docs/HEARTH-SPEC.md, "What the player sees"). At home it is the
 * Warmth glyph, embers to a full flame, with a soft crackle and a glow when it reaches 100% (the
 * same cue when a bed fills it). Away it is the Provisioned badge, draining as its time runs out, and
 * the Tales scroll with its count.
 *
 * <p>It stands centred over the XP level, on the armour row's height ({@link #drawAlone}). With the chat
 * open, pointing at it lists the active boons (short, details on Shift).
 *
 * <p>The edge cues are here too, played for the edge the server put on the payload: entering range
 * plays a crackle and says "By the Hearth" on the action bar, leaving says "Left the Hearth's warmth",
 * and walking out at full Warmth "Set out from the Hearth" (with the pack sound when Provisioned came
 * with you).
 */
public final class HearthHud {

    private static final long FADE_IN_MS = 250;
    private static final long FADE_OUT_MS = 500;
    /** Two edge cues closer than this are one walk along the edge: the second is dropped. */
    private static final long CUE_GAP_MS = 2500;
    private static final int GAP = 2;
    /** Vanilla's armour row: 49 above the bottom of the screen (hotbar 22, XP bar, the health row). */
    private static final int ARMOUR_ROW = 49;
    private static final int TALES = SkillPalette.accent(SkillCategory.EXPEDITION);
    private static final int GREY = 0x8A8A8A;
    private static final int DIM = 0x5A5A5A;

    private static float homeA;
    private static float awayA;
    private static long lastFrame = Long.MIN_VALUE / 2;
    private static double lastWarmth = -1;
    private static long glowAt = Long.MIN_VALUE / 2;
    private static long lastCue = Long.MIN_VALUE / 2;

    /** The line drew the glyph this frame at this alpha (0..255). */
    /** Where the glyph was drawn last, for the chat-open tooltip: x, y, width. */
    private static int hitX = -1;
    private static int hitY;
    private static int hitW;

    // Cached text of the Tales count.
    private static int talesShown = -1;
    private static String talesText = "";

    // The away group as last shown away: Provisioned left (0..1) and the Tales. Coming home, the group
    // fades out with these instead of vanishing (the home packet carries neither).
    private static double awayLeft;
    private static int awayTales;

    // The home glyph as last shown at home: walking out, it fades with this frame instead of snapping to
    // cold embers (the away packet carries no Warmth).
    private static double homeWarmth;
    private static boolean homeSmoulders;

    private HearthHud() {
    }

    // ---- Packets ------------------------------------------------------------------------------------

    /**
     * The HUD packet (already stored by {@code HearthClients.hud}): play the edge cue it carries. The
     * server decides the edge ({@link HearthHudPayload#edge()}); a login, or a Hearth that smoulders,
     * breaks or is switched off under the player, carries none, so {@code home} flipping alone plays
     * nothing.
     */
    static void accept(HearthHudPayload payload, long now) {
        int edge = payload.edge();
        if (edge == 0 || now - lastCue < CUE_GAP_MS) {
            return;
        }
        lastCue = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (edge == HearthHudPayload.FLAG_EDGE_HOME) {
            minecraft.gui.setOverlayMessage(Component.translatable("proficiency.hearth.client.hud.home")
                    .withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)), false);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CAMPFIRE_CRACKLE, 1.0f, 0.9f));
            return;
        }
        boolean setOut = edge == HearthHudPayload.FLAG_EDGE_SET_OUT;
        minecraft.gui.setOverlayMessage(Component.translatable(setOut
                ? "proficiency.hearth.client.hud.set_out" : "proficiency.hearth.client.hud.left")
                .withStyle(setOut ? ChatFormatting.GOLD : ChatFormatting.GRAY), false);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CAMPFIRE_CRACKLE, 0.8f, 0.5f));
        if (setOut && provisionedLeft(payload) > 0) {
            // The pack goes on: Hearty came with you.
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.1f,
                    0.7f));
        }
    }

    static void reset() {
        homeA = 0f;
        awayA = 0f;
        lastWarmth = -1;
        glowAt = Long.MIN_VALUE / 2;
        lastCue = Long.MIN_VALUE / 2;
        hitX = -1;
        talesShown = -1;
        awayLeft = 0;
        awayTales = 0;
        homeWarmth = 0;
        homeSmoulders = false;
    }

    // ---- Numbers ------------------------------------------------------------------------------------

    /**
     * Standing at a Hearth: home, or at one that smoulders. The server sends {@code home} false at a
     * smouldering Hearth (no Warmth, no boons, not "at home" for Homebuilder), with the smouldering flag;
     * the glyph still shows there, as cold grey embers, so the player sees the fire is out.
     */
    private static boolean atHearth(@org.jetbrains.annotations.Nullable HearthHudPayload hud) {
        return hud != null && (hud.home() || hud.has(HearthHudPayload.FLAG_SMOULDERING));
    }

    /** Warmth now, moved on by its rate since the packet. */
    static double warmth(long now) {
        HearthHudPayload hud = HearthView.hud();
        if (hud == null || !hud.home()) {
            return 0;
        }
        return WarmthGlyph.at(hud.warmth(), hud.warmthPerSecond(), now - HearthView.hudAt());
    }

    /**
     * The Warmth glyph as the HUD shows it at {@code now} ({@code Util.getMillis}): the frame for
     * Warmth moved on by its rate, cold embers at a smouldering Hearth. The skills panel's streak
     * block draws this same glyph, so the two never disagree.
     */
    public static String glyph(long now) {
        HearthHudPayload hud = HearthView.hud();
        boolean smoulders = hud != null && hud.has(HearthHudPayload.FLAG_SMOULDERING);
        return smoulders ? WarmthGlyph.glyph(0) : WarmthGlyph.glyph(warmth(now));
    }

    private static double provisionedLeft(HearthHudPayload hud) {
        Minecraft minecraft = Minecraft.getInstance();
        long game = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        return WarmthGlyph.provisionedLeft(hud.provisioned(), hud.provisionedFrom(), hud.provisionedUntil(), game);
    }

    private static int tales() {
        HearthHudPayload hud = HearthView.hud();
        return hud == null || !(hud.tales() >= 1) ? 0 : Math.round(hud.tales());
    }

    // ---- Per frame ----------------------------------------------------------------------------------

    /**
     * Moves the fades on and fires the 100% cue; once per HUD pass from {@link HearthClientEvents}, drawn
     * or hidden.
     */
    static void frame(long now) {
        long dt = lastFrame == Long.MIN_VALUE / 2 ? 0 : Math.max(0, Math.min(250, now - lastFrame));
        lastFrame = now;
        HearthHudPayload hud = HearthView.hud();
        boolean home = atHearth(hud);
        boolean away = hud != null && !home && (provisionedLeft(hud) > 0 || tales() > 0);
        if (away) {
            awayLeft = provisionedLeft(hud);
            awayTales = tales();
        }
        homeA = approach(homeA, home, dt);
        awayA = approach(awayA, away, dt);
        double warmth = warmth(now);
        if (home) {
            homeSmoulders = hud.has(HearthHudPayload.FLAG_SMOULDERING);
            homeWarmth = homeSmoulders ? 0 : warmth;
        }
        if (home && lastWarmth >= 0 && WarmthGlyph.filled(lastWarmth, warmth)) {
            glowAt = now;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.CAMPFIRE_CRACKLE, 1.2f, 1.0f));
        }
        lastWarmth = home ? warmth : -1;
    }

    private static float approach(float value, boolean on, long dt) {
        if (on) {
            return Math.min(1f, value + dt / (float) FADE_IN_MS);
        }
        return Math.max(0f, value - dt / (float) FADE_OUT_MS);
    }

    /** Width of the glyph group as it stands this frame, 0 when there is nothing to show. */
    public static int lineWidth(Font font, long now) {
        int width = 0;
        if (homeA > 0.01f) {
            width = WarmthGlyph.WIDTH;
        }
        if (awayA > 0.01f) {
            width = Math.max(width, awayWidth(font));
        }
        return width;
    }

    /** Width of the away group as last shown away (it keeps it while it fades out at home). */
    private static int awayWidth(Font font) {
        int width = 0;
        if (awayLeft > 0) {
            width += WarmthGlyph.WIDTH;
        }
        int tales = awayTales;
        if (tales > 0) {
            if (width > 0) {
                width += GAP;
            }
            width += WarmthGlyph.WIDTH + font.width(talesText(tales));
        }
        return width;
    }

    private static String talesText(int tales) {
        if (tales != talesShown) {
            talesShown = tales;
            talesText = WarmthGlyph.talesCount(tales);
        }
        return talesText;
    }

    /**
     * The group over the XP level. Each group is centred on its own width, so neither moves while home and
     * away cross-fade.
     */
    static void drawAlone(GuiGraphics graphics, Font font, long now) {
        int width = lineWidth(font, now);
        if (width <= 0) {
            hitX = -1;
            return;
        }
        // Centred over the XP level, on the armour row: the middle column between the armour and the air
        // bubbles is free whatever the bars show (user, 2026-10-11; it used to ride the skill line).
        // The chat box sits higher in z and reaches this spot; lift the group over it.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        draw(graphics, font, graphics.guiWidth() / 2, graphics.guiHeight() - ARMOUR_ROW, 255, now, true);
        graphics.pose().popPose();
    }

    /** The group from {@code x} (on the line), or centred on {@code x} when {@code centred} (alone). */
    private static void draw(GuiGraphics graphics, Font font, int x, int y, int alpha, long now, boolean centred) {
        HearthHudPayload hud = HearthView.hud();
        if (hud == null) {
            return;
        }
        hitW = lineWidth(font, now);
        hitX = centred ? x - hitW / 2 : x;
        hitY = y;
        int home = Math.round(alpha * HudKit.smooth(homeA));
        if (home > 8) {
            int homeX = centred ? x - WarmthGlyph.WIDTH / 2 : x;
            // At home the live frame; fading out after leaving, the frame it last showed at home.
            boolean live = atHearth(hud);
            boolean smoulders = live ? hud.has(HearthHudPayload.FLAG_SMOULDERING) : homeSmoulders;
            String frame = live ? glyph(now) : WarmthGlyph.glyph(homeWarmth);
            graphics.drawString(font, frame, homeX, y, (home << 24) | (smoulders ? GREY : 0xFFFFFF), true);
            float glow = WarmthGlyph.glow(glowAt, now);
            if (glow > 0f) {
                int a = Math.round(home * glow);
                if (a > 8) {
                    float radius = 4 + (1f - glow) * 9;
                    HudKit.ring(graphics, homeX + 3, y + 3, radius, radius, (a << 24) | 0xFFE9A0, 1, 5, 64);
                }
            }
        }
        int away = Math.round(alpha * HudKit.smooth(awayA));
        if (away > 8) {
            int cursor = centred ? x - awayWidth(font) / 2 : x;
            double left = awayLeft;
            if (left > 0) {
                drawDraining(graphics, font, cursor, y, left, away);
                cursor += WarmthGlyph.WIDTH + GAP;
            }
            int tales = awayTales;
            if (tales > 0) {
                String scroll = String.valueOf(WarmthGlyph.TALES);
                graphics.drawString(font, scroll, cursor, y, (away << 24) | 0xFFFFFF, true);
                graphics.drawString(font, talesText(tales), cursor + WarmthGlyph.WIDTH, y, (away << 24) | (TALES & 0xFFFFFF),
                        true);
            }
        }
    }

    /** The Provisioned pack: drawn dim, then its remaining part bright from the bottom up. */
    private static void drawDraining(GuiGraphics graphics, Font font, int x, int y, double left, int alpha) {
        String glyph = String.valueOf(WarmthGlyph.PROVISIONED);
        graphics.drawString(font, glyph, x, y, (alpha << 24) | DIM, true);
        int rows = Math.max(1, (int) Math.round(left * 8));
        graphics.enableScissor(x - 1, y + 8 - rows, x + WarmthGlyph.WIDTH, y + 9);
        graphics.drawString(font, glyph, x, y, (alpha << 24) | 0xFFFFFF, true);
        graphics.disableScissor();
    }

    /** Middle of the glyph as last drawn (the demo points at it), or null when none was drawn. */
    static int[] hitPoint() {
        return hitX < 0 ? null : new int[] {hitX + Math.max(4, hitW / 2), hitY + 3};
    }

    // ---- The chat-open tooltip ----------------------------------------------------------------------

    /** With the chat open and the pointer on the glyph, the active boons. */
    static void tooltip(GuiGraphics graphics, Minecraft minecraft, long now) {
        if (!(minecraft.screen instanceof ChatScreen) || hitX < 0 || HearthView.hud() == null) {
            return;
        }
        double scale = minecraft.getWindow().getGuiScale();
        int mx = (int) (minecraft.mouseHandler.xpos() / scale);
        int my = (int) (minecraft.mouseHandler.ypos() / scale);
        if (mx < hitX - 1 || mx > hitX + hitW + 1 || my < hitY - 2 || my > hitY + 10) {
            return;
        }
        // The vanilla HUD layers each sit higher than the last; lift the tooltip over all of them.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 3000);
        HudKit.tooltip(graphics, minecraft.font, lines(now), mx, my, Math.min(260, graphics.guiWidth() - 16));
        graphics.pose().popPose();
    }

    /** The glyph's tooltip: where you stand, the boons that work and how strongly, then the details. */
    static List<Component> lines(long now) {
        List<Component> lines = new ArrayList<>();
        HearthHudPayload hud = HearthView.hud();
        if (hud == null) {
            return lines;
        }
        boolean detailed = TooltipDetail.detailed();
        if (!atHearth(hud)) {
            lines.add(Component.translatable("proficiency.hearth.client.hud.tip.away")
                    .withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)));
            double left = provisionedLeft(hud);
            if (left > 0) {
                Minecraft minecraft = Minecraft.getInstance();
                long game = minecraft.level == null ? 0 : minecraft.level.getGameTime();
                long minutes = Math.max(1, (hud.provisionedUntil() - game + 1199) / 1200);
                lines.add(Component.translatable("proficiency.hearth.client.hud.tip.provisioned", minutes)
                        .withStyle(ChatFormatting.GOLD));
            }
            if (tales() > 0) {
                lines.add(Component.translatable("proficiency.hearth.client.hud.tip.tales", tales())
                        .withStyle(style -> style.withColor(TALES & 0xFFFFFF)));
            }
            return lines;
        }
        int warmth = (int) Math.floor(warmth(now) * 100 + 1e-6);
        lines.add(Component.translatable("proficiency.hearth.client.hud.tip.title", warmth)
                .withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)));
        if (hud.has(HearthHudPayload.FLAG_SMOULDERING)) {
            lines.add(Component.translatable("proficiency.hearth.client.panel.smouldering").withStyle(ChatFormatting.RED));
            return lines;
        }
        HearthStatePayload state = HearthView.state();
        boolean any = false;
        if (state != null) {
            for (HearthPool pool : HearthPool.VALUES) {
                Component boon = HearthText.boonLine(pool.ordinal(), tierOf(state, pool.ordinal()), false);
                if (boon != null) {
                    lines.add(boon);
                    any = true;
                }
            }
        }
        if (!any) {
            lines.add(Component.translatable("proficiency.hearth.client.boon.none").withStyle(ChatFormatting.GRAY));
        }
        if (!detailed) {
            return TooltipDetail.withHint(lines, true);
        }
        lines.add(Component.translatable("proficiency.hearth.client.hud.tip.warmth", warmth).withStyle(ChatFormatting.GRAY));
        if (HearthView.streakSafe()) {
            lines.add(Component.translatable("proficiency.hearth.effects.streak.safe").withStyle(ChatFormatting.GOLD));
        }
        if (hud.has(HearthHudPayload.FLAG_TEMP_HP)) {
            lines.add(Component.translatable("proficiency.hearth.client.hud.tip.temp_hp").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static int tierOf(HearthStatePayload state, int pool) {
        for (var row : state.pools()) {
            if (row.pool() == pool) {
                return row.tier();
            }
        }
        return 0;
    }
}
