package dev.amman.proficiency.hearth.client;

import com.mojang.math.Axis;
import dev.amman.proficiency.client.DiscoveryBanner;
import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.hearth.BellRules;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The siege panel at the top left (docs/HEARTH-SPEC.md, "Warning: a day ahead, a countdown on the
 * left"; client switch {@code hearth.siegePanel}) and the bell's arrow home.
 *
 * <ul>
 * <li>Countdown: the siege glyph, "Siege in 18:20" and a bar draining to zero, "Warm n/m", and an
 * arrow to the Hearth with its distance while you are away from it. The client counts down from the
 * due time it was sent once.</li>
 * <li>Waves: "Wave 2/3, 7 left" and a bar of what is left of the wave.</li>
 * <li>Outcome: "Defended!" or "The Hearth smoulders" (or a quieter dawn, abandon or fizzle) for
 * 10 s, then it fades, like the death recap.</li>
 * <li>A bell's call ({@link HearthPingPayload}) shows the same arrow for its time under a small bell
 * line, and a held compass bound to the Hearth's lodestone pings.</li>
 * </ul>
 * It sits at y 0.06 of the screen, clear of the debug XP feed, which starts at 0.25.
 */
public final class SiegePanelHud {

    private static final int MARGIN = 4;
    private static final int PAD = 5;
    private static final int MIN_WIDTH = 118;
    private static final int LINE = 10;
    private static final int BAR_H = 3;
    private static final int GOLD = 0xFFD24A;
    private static final int ALARM_FORCE_MS = 10_000;

    @Nullable
    private static SiegePanelPayload siege;
    private static long shownAt = Long.MIN_VALUE / 2;
    private static long endsAt = Long.MAX_VALUE;
    private static int lastWave = -1;
    private static int waveMax;
    /**
     * The countdown last announced with a banner: its Hearth and expected start ({@link SiegeClock#sameSiege}).
     * Closed by that Hearth's waves, outcome or clear; kept when the world is left, so a relog during the
     * countdown gets the panel only, as a new arrival in range does.
     */
    private static long announcedStart = Long.MIN_VALUE;
    private static long announcedPos = Long.MIN_VALUE;
    /** The ticks the announced countdown's bar drains over ({@link SiegeClock#span}). */
    private static long announcedSpan;

    @Nullable
    private static HearthPingPayload ping;
    private static long pingAt = Long.MIN_VALUE / 2;
    private static long pingUntilGame;
    private static long forcedUntil = Long.MIN_VALUE / 2;
    /** When the call panel last came on screen: it fades in when it takes over from the siege panel too. */
    private static long callShownAt = Long.MIN_VALUE / 2;
    private static boolean callDrawn;

    private SiegePanelHud() {
    }

    static void reset() {
        siege = null;
        ping = null;
        callDrawn = false;
        shownAt = Long.MIN_VALUE / 2;
        endsAt = Long.MAX_VALUE;
        lastWave = -1;
        waveMax = 0;
        forcedUntil = Long.MIN_VALUE / 2;
    }

    /**
     * Whether the panel and the banner that announces a siege may show now: {@code hearth.siegePanel}, or
     * the bell's alarm, which pops the panel up for everyone in the siege whatever the setting.
     */
    private static boolean allowed(long now) {
        return ProficiencyClientConfig.hearthSiegePanel() || now < forcedUntil;
    }

    // ---- Packets ------------------------------------------------------------------------------------

    static void accept(SiegePanelPayload payload, long now) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean wasShowing = siege != null && now < endsAt;
        // The switch is for what the siege puts on the screen: the panel and the announcement's banner (the
        // sieges lane's chat line still tells). The horn per wave and the outcome's sound are the siege's
        // own audio, which the sieges lane leaves to this client, so they play either way: a player who hides
        // the panel still hears a wave come.
        boolean shows = allowed(now);
        boolean sound = minecraft.player != null;
        switch (payload.phase()) {
            case SiegePanelPayload.CLEAR -> {
                // The next siege starts its wave count afresh (its first wave may have this one's number).
                lastWave = -1;
                closeAnnouncement(payload.pos());
                endAlarm(payload.pos());
                if (siege != null && now < endsAt) {
                    endsAt = Math.min(endsAt, now + SiegeClock.FADE_OUT_MS);
                }
                return;
            }
            case SiegePanelPayload.COUNTDOWN -> {
                // The siege is announced: a banner, once per siege (re-sends for Warm n/m, a slept night, a
                // postponement, a relog and a new arrival in range get the panel only; the sieges lane's chat
                // line tells a postponement). Marked as told even when the panel is off, so switching it on
                // later does not bring an old announcement.
                boolean same = SiegeClock.sameSiege(payload.pos(), payload.due(), announcedPos, announcedStart);
                if (!same && shows) {
                    DiscoveryBanner.showSiege(
                            Component.translatable("proficiency.hearth.client.banner.siege.kicker"),
                            Component.translatable("proficiency.hearth.client.banner.siege.title"));
                }
                long game = minecraft.level == null ? 0 : minecraft.level.getGameTime();
                announcedSpan = SiegeClock.span(same ? announcedSpan : 0L, SiegeClock.ticksLeft(payload.due(), game),
                        HearthView.warningTicks());
                announcedPos = payload.pos();
                announcedStart = payload.due();
                lastWave = -1;
                endsAt = Long.MAX_VALUE;
            }
            case SiegePanelPayload.WAVES -> {
                closeAnnouncement(payload.pos());
                if (payload.wave() != lastWave) {
                    lastWave = payload.wave();
                    waveMax = payload.left();
                    if (sound) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RAID_HORN.value(), 1.0f,
                                0.35f));
                    }
                } else {
                    waveMax = Math.max(waveMax, payload.left());
                }
                endsAt = Long.MAX_VALUE;
            }
            case SiegePanelPayload.OUTCOME -> {
                lastWave = -1;
                closeAnnouncement(payload.pos());
                endAlarm(payload.pos());
                endsAt = now + SiegeClock.OUTCOME_MS;
                SiegeRules.Outcome outcome = outcome(payload.outcome());
                if (sound && outcome == SiegeRules.Outcome.DEFENDED) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                            1.0f, 0.7f));
                } else if (sound && outcome == SiegeRules.Outcome.DOUSED) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.8f));
                }
            }
            default -> {
                return;
            }
        }
        if (!wasShowing) {
            shownAt = now;
        }
        siege = payload;
    }

    /** The countdown at {@code pos} is over (it began, ended or was called off): its next one is news. */
    private static void closeAnnouncement(long pos) {
        if (pos == announcedPos) {
            announcedPos = Long.MIN_VALUE;
            announcedStart = Long.MIN_VALUE;
            announcedSpan = 0L;
        }
    }

    /**
     * The siege at {@code pos} is over: an alarm rung for it has done its job. Its call (30 s, the alarm's
     * cooldown) would otherwise come back under "The alarm bell rings" when the outcome fades.
     */
    private static void endAlarm(long pos) {
        if (ping != null && ping.call() == BellRules.Call.ALARM.ordinal() && ping.pos() == pos) {
            ping = null;
        }
    }

    static void acceptPing(HearthPingPayload payload, long now) {
        Minecraft minecraft = Minecraft.getInstance();
        long game = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        ping = payload;
        pingAt = now;
        pingUntilGame = game + Math.max(20, payload.ticks());
        if (payload.call() == BellRules.Call.ALARM.ordinal()) {
            forcedUntil = now + ALARM_FORCE_MS;
            if (siege != null && now >= endsAt) {
                endsAt = Long.MAX_VALUE;
                shownAt = now;
            }
        }
    }

    @Nullable
    private static SiegeRules.Outcome outcome(int ordinal) {
        SiegeRules.Outcome[] values = SiegeRules.Outcome.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static boolean pingActive(Minecraft minecraft) {
        return ping != null && minecraft.level != null && minecraft.level.getGameTime() < pingUntilGame;
    }

    // ---- Drawing ------------------------------------------------------------------------------------

    static void render(GuiGraphics graphics, Minecraft minecraft, long now) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        boolean siegeUp = siege != null && now < endsAt && allowed(now);
        if (siege != null && endsAt != Long.MAX_VALUE && now >= endsAt + 100) {
            siege = null;
        }
        boolean pinged = pingActive(minecraft);
        // Above the other HUD layers, as the glyph's tooltip is: a minimap mod drawn in the top left corner
        // later in the frame hid the whole panel (Xaero's on the Fabric pack).
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2500);
        if (siegeUp) {
            float opacity = SiegeClock.opacity(shownAt, endsAt, now);
            drawSiege(graphics, minecraft, player, Math.round(255 * HudKit.smooth(opacity)), now);
        } else if (pinged) {
            if (!callDrawn) {
                callShownAt = now;
                callDrawn = true;
            }
            long game = minecraft.level.getGameTime();
            long left = pingUntilGame - game;
            float out = Math.min(1f, left / (SiegeClock.FADE_OUT_MS / 50f));
            float in = Math.min(1f, (now - Math.max(pingAt, callShownAt)) / (float) SiegeClock.FADE_IN_MS);
            drawCall(graphics, minecraft, player, Math.round(255 * HudKit.smooth(Math.min(in, out))), now);
        }
        if (siegeUp || !pinged) {
            callDrawn = false;
        }
        graphics.pose().popPose();
        if (pinged) {
            compassPing(graphics, minecraft, player, now);
        }
    }

    /** The panel's eased top, so it slides out of a banner's way instead of jumping. */
    private static float shownTop = Float.NaN;
    private static long shownTopAt;

    /**
     * Where a panel {@code height} tall goes this frame: at the hearth.siegePanelY setting (0.06), or,
     * while a discovery banner (the siege announcement comes with one) would cover it on a short screen,
     * just under the banner, and never down over the hotbar ({@link SiegeClock#panelTop}). It eases there
     * over about 200 ms and back.
     */
    private static int top(GuiGraphics graphics, int height, long now) {
        int target = SiegeClock.panelTop(graphics.guiHeight(), height, ProficiencyClientConfig.hearthSiegePanelY(),
                MARGIN, HudKit.bannerTop(), HudKit.bannerBottom());
        if (Float.isNaN(shownTop) || now - shownTopAt > 500) {
            shownTop = target;
        } else {
            float k = Math.min(1f, (now - shownTopAt) / 70f);
            shownTop += (target - shownTop) * k;
            if (Math.abs(target - shownTop) < 0.5f) {
                shownTop = target;
            }
        }
        shownTopAt = now;
        return Math.round(shownTop);
    }

    private static void drawSiege(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, int alpha, long now) {
        if (alpha <= 8 || siege == null) {
            return;
        }
        Font font = minecraft.font;
        SiegePanelPayload p = siege;
        long game = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        String title;
        int titleColour = SkillPalette.TEXT;
        int border = HearthPalette.SIEGE;
        float bar = -1f;
        int barColour = HearthPalette.SIEGE;
        String sub = null;
        int subColour = -1;
        SiegeRules.Outcome outcome = null;
        switch (p.phase()) {
            case SiegePanelPayload.COUNTDOWN -> {
                long left = SiegeClock.ticksLeft(p.due(), game);
                title = left > 0
                        ? Component.translatable("proficiency.hearth.client.siege.countdown", SiegeClock.clock(left)).getString()
                        : Component.translatable("proficiency.hearth.client.siege.nightfall").getString();
                bar = SiegeClock.bar(p.due(), game, HearthView.warningTicks(), p.pos() == announcedPos ? announcedSpan : 0L);
                barColour = HearthPalette.EMBER;
                if (p.group() > 0) {
                    sub = Component.translatable("proficiency.hearth.client.siege.warm", p.warm(), p.group()).getString();
                }
            }
            case SiegePanelPayload.WAVES -> {
                title = Component.translatable("proficiency.hearth.client.siege.wave", p.wave(), p.waves(), p.left())
                        .getString();
                bar = waveMax > 0 ? Math.min(1f, p.left() / (float) waveMax) : 0f;
            }
            case SiegePanelPayload.OUTCOME -> {
                outcome = outcome(p.outcome());
                String id = outcome == null ? "dawn" : outcome.id();
                title = Component.translatable("proficiency.hearth.client.siege.outcome." + id).getString();
                if (outcome == SiegeRules.Outcome.DEFENDED) {
                    titleColour = 0xFF000000 | GOLD;
                    border = GOLD;
                } else if (outcome == SiegeRules.Outcome.DOUSED) {
                    titleColour = 0xFFE8A39B;
                } else {
                    titleColour = SkillPalette.TEXT_DIM;
                    border = 0xFF7A828C;
                }
                // What the outcome gave or took, like the death recap's lines (the chat has the numbers).
                if (outcome == SiegeRules.Outcome.DEFENDED || outcome == SiegeRules.Outcome.DOUSED
                        || outcome == SiegeRules.Outcome.UNEARNED) {
                    sub = Component.translatable("proficiency.hearth.client.siege.outcome." + id + ".sub").getString();
                    subColour = outcome == SiegeRules.Outcome.DEFENDED ? HearthPalette.EMBER
                            : outcome == SiegeRules.Outcome.DOUSED ? 0xFFC9877F : SkillPalette.TEXT_DIM;
                }
            }
            default -> {
                return;
            }
        }
        boolean away = p.phase() != SiegePanelPayload.OUTCOME && !home(p.pos());
        boolean otherDim = away && !sameDimension(minecraft, p.dimension());
        String distance = away && !otherDim ? distanceText(player, p.pos()) : null;
        String dimText = otherDim ? Component.translatable("proficiency.hearth.client.siege.other_dim").getString() : null;

        int maxWidth = Math.max(MIN_WIDTH, graphics.guiWidth() * 2 / 5);
        int want = WarmthGlyph.WIDTH + 3 + font.width(title);
        int subW = (sub == null ? 0 : font.width(sub)) + (distance != null ? 4 + 9 + font.width(distance) : 0)
                + (dimText != null ? 4 + font.width(dimText) : 0);
        int inner = Math.min(maxWidth - PAD * 2, Math.max(MIN_WIDTH - PAD * 2, Math.max(want, subW)));
        boolean hasSub = sub != null || distance != null || dimText != null;
        // An outcome's recap line wraps on a narrow screen, up to three lines like the death recap's survival
        // line; the rest stay one line.
        List<String> recap = null;
        if (sub != null && subColour != -1) {
            boolean[] cut = new boolean[1];
            recap = HudKit.wrap(font, sub, inner, 3, cut);
            if (cut[0]) {
                HudKit.note("hearth.siege.sub");
            }
        }
        int subLines = recap == null ? (hasSub ? 1 : 0) : recap.size();
        int height = PAD + LINE + (bar >= 0 ? BAR_H + 4 : 0) + subLines * LINE + PAD - 2;
        int x = MARGIN;
        int y = top(graphics, height, now);
        int width = inner + PAD * 2;
        graphics.fill(x, y, x + width, y + height, ((int) (alpha * 0.72f) << 24) | 0x0E1014);
        graphics.fill(x, y, x + width, y + 1, (alpha << 24) | (border & 0xFFFFFF));
        graphics.fill(x, y + height - 1, x + width, y + height, (alpha / 3 << 24) | 0x2C333D);

        int lineY = y + PAD;
        graphics.drawString(font, String.valueOf(WarmthGlyph.SIEGE), x + PAD, lineY, (alpha << 24) | 0xFFFFFF, true);
        String shown = HudKit.clip(font, title, inner - WarmthGlyph.WIDTH - 3);
        if (!shown.equals(title)) {
            HudKit.note("hearth.siege.title");
        }
        int pulse = titleColour & 0xFFFFFF;
        if (outcome == SiegeRules.Outcome.DEFENDED) {
            // The win swells once: white to gold over the first second.
            float t = Math.min(1f, (now - (endsAt - SiegeClock.OUTCOME_MS)) / 1000f);
            pulse = HudKit.lerp(0xFFFFFF, GOLD, HudKit.smooth(t));
        }
        graphics.drawString(font, shown, x + PAD + WarmthGlyph.WIDTH + 3, lineY, (alpha << 24) | pulse, true);
        lineY += LINE;
        if (bar >= 0) {
            int barW = inner;
            graphics.fill(x + PAD, lineY + 1, x + PAD + barW, lineY + 1 + BAR_H, (alpha << 24) | (SkillPalette.TRACK & 0xFFFFFF));
            int filled = Math.round(barW * bar);
            if (bar > 0 && filled < 1) {
                filled = 1;
            }
            if (filled > 0) {
                graphics.fill(x + PAD, lineY + 1, x + PAD + filled, lineY + 1 + BAR_H, (alpha << 24) | (barColour & 0xFFFFFF));
            }
            lineY += BAR_H + 4;
        }
        if (hasSub) {
            int cursor = x + PAD;
            if (recap != null) {
                for (String line : recap) {
                    graphics.drawString(font, line, cursor, lineY, (alpha << 24) | (subColour & 0xFFFFFF), true);
                    lineY += LINE;
                }
            } else if (sub != null) {
                boolean allWarm = p.warm() >= p.group() && p.group() > 0;
                int colour = allWarm ? HearthPalette.EMBER : SkillPalette.TEXT_DIM;
                graphics.drawString(font, sub, cursor, lineY, (alpha << 24) | (colour & 0xFFFFFF), true);
                cursor += font.width(sub) + 4;
            }
            if (distance != null) {
                arrow(graphics, cursor + 4, lineY + 3, angle(player, p.pos()), (alpha << 24) | (HearthPalette.EMBER & 0xFFFFFF));
                graphics.drawString(font, distance, cursor + 9, lineY, (alpha << 24) | (SkillPalette.TEXT & 0xFFFFFF), true);
            } else if (dimText != null) {
                graphics.drawString(font, HudKit.clip(font, dimText, x + width - PAD - cursor), cursor, lineY,
                        (alpha << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
            }
        }
    }

    private static void drawCall(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, int alpha, long now) {
        if (alpha <= 8 || ping == null) {
            return;
        }
        Font font = minecraft.font;
        BellRules.Call call = ping.call() >= 0 && ping.call() < BellRules.Call.values().length
                ? BellRules.Call.values()[ping.call()] : BellRules.Call.COME_HOME;
        String title = Component.translatable("proficiency.hearth.client.call." + call.id()).getString();
        boolean otherDim = !sameDimension(minecraft, ping.dimension());
        boolean home = home(ping.pos());
        String distance = otherDim || home ? null : distanceText(player, ping.pos());
        int want = WarmthGlyph.WIDTH + 3 + font.width(title) + (distance != null ? 6 + 9 + font.width(distance) : 0);
        int inner = Math.min(Math.max(MIN_WIDTH, graphics.guiWidth() * 2 / 5) - PAD * 2, Math.max(MIN_WIDTH - PAD * 2, want));
        int height = PAD + LINE + PAD - 2;
        int x = MARGIN;
        int y = top(graphics, height, now);
        int width = inner + PAD * 2;
        graphics.fill(x, y, x + width, y + height, ((int) (alpha * 0.72f) << 24) | 0x0E1014);
        int border = call == BellRules.Call.ALARM ? HearthPalette.SIEGE : HearthPalette.EMBER;
        graphics.fill(x, y, x + width, y + 1, (alpha << 24) | (border & 0xFFFFFF));
        // The bell sways for its first second.
        float swing = (float) Math.sin((now - pingAt) / 90.0) * Math.max(0f, 1f - (now - pingAt) / 1000f);
        int bellX = x + PAD;
        int lineY = y + PAD;
        graphics.pose().pushPose();
        graphics.pose().rotateAround(Axis.ZP.rotationDegrees(swing * 18f), bellX + 3.5f, lineY - 1, 0);
        graphics.drawString(font, String.valueOf(WarmthGlyph.BELL), bellX, lineY, (alpha << 24) | 0xFFFFFF, true);
        graphics.pose().popPose();
        int room = inner - WarmthGlyph.WIDTH - 3 - (distance != null ? 6 + 9 + font.width(distance) : 0);
        String shown = HudKit.clip(font, title, Math.max(20, room));
        graphics.drawString(font, shown, bellX + WarmthGlyph.WIDTH + 3, lineY, (alpha << 24) | (SkillPalette.TEXT & 0xFFFFFF), true);
        if (distance != null) {
            int ax = x + width - PAD - font.width(distance) - 9;
            arrow(graphics, ax + 4, lineY + 3, angle(player, ping.pos()), (alpha << 24) | (border & 0xFFFFFF));
            graphics.drawString(font, distance, ax + 9, lineY, (alpha << 24) | (SkillPalette.TEXT & 0xFFFFFF), true);
        }
    }

    /** True while the HUD says this player stands by that Hearth. */
    private static boolean home(long pos) {
        var hud = HearthView.hud();
        return hud != null && hud.home() && hud.hearthPos() == pos;
    }

    private static boolean sameDimension(Minecraft minecraft, String dimension) {
        return minecraft.level != null && (dimension == null || dimension.isEmpty()
                || minecraft.level.dimension().location().toString().equals(dimension));
    }

    private static String distanceText(LocalPlayer player, long pos) {
        BlockPos at = BlockPos.of(pos);
        int blocks = HomeArrow.distance(player.getX(), player.getZ(), at.getX() + 0.5, at.getZ() + 0.5);
        return Component.translatable("proficiency.hearth.client.siege.distance", blocks).getString();
    }

    private static float angle(LocalPlayer player, long pos) {
        BlockPos at = BlockPos.of(pos);
        return HomeArrow.relative(player.getX(), player.getZ(), at.getX() + 0.5, at.getZ() + 0.5, player.getYRot());
    }

    /**
     * A small arrow centred on ({@code cx}, {@code cy}), pointing up when {@code degrees} is 0 and
     * turning clockwise: a head of three rows over a two-pixel shaft, drawn with fills under a rotation.
     */
    static void arrow(GuiGraphics graphics, int cx, int cy, float degrees, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(cx + 0.5f, cy + 0.5f, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        graphics.fill(0, -4, 1, -3, argb);
        graphics.fill(-1, -3, 2, -2, argb);
        graphics.fill(-2, -2, 3, -1, argb);
        graphics.fill(-3, -1, 4, 0, argb);
        graphics.fill(-1, 0, 2, 4, argb);
        graphics.pose().popPose();
    }

    /** A gold pulse round the held compass slot when that compass points at the called Hearth. */
    private static void compassPing(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, long now) {
        if (ping == null || minecraft.options.hideGui) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!pointsAt(stack, ping)) {
                continue;
            }
            int cx;
            int cy = graphics.guiHeight() - 11;
            boolean offhandLeft = player.getMainArm() == HumanoidArm.RIGHT;
            if (hand == InteractionHand.MAIN_HAND) {
                cx = graphics.guiWidth() / 2 - 90 + player.getInventory().selected * 20 + 10;
            } else {
                cx = offhandLeft ? graphics.guiWidth() / 2 - 91 - 29 + 12 : graphics.guiWidth() / 2 + 91 + 17;
            }
            long phase = (now - pingAt) % 1000;
            float t = phase / 1000f;
            int a = Math.round(255 * (1f - t) * 0.9f);
            if (a > 8) {
                float r = 9f + t * 9f;
                // The vanilla HUD layers each sit higher than the last (the hotbar and the XP bar among them);
                // at z 0 this event's drawing is hidden under them, so the ring round the slot never showed.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 3000);
                HudKit.ring(graphics, cx, cy, r, r, (a << 24) | GOLD, t < 0.5f ? 2 : 1, 1, 64);
                graphics.pose().popPose();
            }
        }
    }

    private static boolean pointsAt(ItemStack stack, HearthPingPayload ping) {
        if (stack.isEmpty()) {
            return false;
        }
        // 1.20.1 keeps a lodestone compass's target in its NBT (1.21.1: the lodestone_tracker component).
        if (!CompassItem.isLodestoneCompass(stack) || stack.getTag() == null) {
            return false;
        }
        GlobalPos target = CompassItem.getLodestonePosition(stack.getTag());
        if (target == null) {
            return false;
        }
        if (ping.dimension() != null && !ping.dimension().isEmpty()
                && !target.dimension().location().toString().equals(ping.dimension())) {
            return false;
        }
        BlockPos hearth = BlockPos.of(ping.pos());
        return target.pos().equals(hearth) || target.pos().equals(hearth.below());
    }
}
