package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.client.DotFlight;
import dev.amman.proficiency.client.HudKit;
import dev.amman.proficiency.client.RightEdge;
import dev.amman.proficiency.client.SkillHud;
import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * The opt-in Hearth tracker (docs/HEARTH-SPEC.md, "Hearth tracker"; client switch
 * {@code hearth.tracker}, off by default): a narrow column on the middle of the right edge, at home
 * only, fading 5 s after you leave. One row per pool with charge: its icon, the roman tier, a thin bar
 * to the next tier, and for a pool held back a lock and what it waits on. Comfort n/12 and Character
 * n/50 under them.
 *
 * <p>The mirrored share of each gain flies as dots from the skill bar into its row
 * ({@link HearthTrackerModel}, 6 dots across all rows), and a row whose tier rises plays its own
 * {@link TierUpFx}: a flash on the bar, the numeral rolling over, a ring, gold at VI, and a chime.
 * The dots follow {@code hud.xpDots}, the tier-up {@code hud.levelUpFx}.
 *
 * <p>It shares the right edge with the death recap and the Tales panel: it steps under (or over) one that
 * would cover it and slides back after, and fades with it only where there is no room ({@link RightEdge}).
 */
public final class TrackerHud {

    private static final int ROW = 11;
    private static final int BAR_W = 40;
    private static final int PAD = 4;
    private static final int RIGHT_MARGIN = 4;
    /** Clear of the effect icons at the top right, and of the hotbar's rows at the bottom. */
    private static final int MIN_TOP = 54;
    private static final int BOTTOM_ROOM = 30;
    private static final int GOLD = 0xFFD24A;

    /** Icon locations by name. Declared before the icons below: static fields initialise in textual order. */
    private static final java.util.Map<String, ResourceLocation> ICONS = new java.util.HashMap<>();
    private static final ResourceLocation[] POOL_ICONS = new ResourceLocation[HearthPool.COUNT];
    static final ResourceLocation COMFORT_ICON = icon("comfort");
    static final ResourceLocation CHARACTER_ICON = icon("character");

    /** At home or not, told every HUD pass (drawn or hidden): the fade in and the linger after leaving. */
    private static final TrackerPresence PRESENCE = new TrackerPresence();
    /** The drawn top, sliding clear of a right-edge panel and back ({@link RightEdge}). */
    private static final RightEdge.Follower SLIDE = new RightEdge.Follower();

    // Layout of the last frame, rebuilt when the window, the language or the rows change. The language is the
    // loaded table itself: the selected code changes before the new strings load, so a frame drawn during the
    // reload would keep the old language's text under the new code; the table is replaced only once it is in.
    private static int romanW = -1;
    private static Language romanLanguage;

    // What the tracker draws every frame, built again only when its inputs change (no text or record
    // allocated per frame while nothing moves).
    private static final Need[] NEEDS = new Need[HearthPool.COUNT];
    private static final int[] NEED_WIDTHS = new int[HearthPool.COUNT];
    private static final long[] NEED_KEYS = new long[HearthPool.COUNT];
    /** This frame's need per pool, from the width pass, for the row pass. */
    private static final Need[] FRAME_NEEDS = new Need[HearthPool.COUNT];
    private static Language needLanguage;
    private static long footerKey = Long.MIN_VALUE;
    private static Language footerLanguage;
    private static String comfortText = "";
    private static String characterText = "";
    private static int comfortW;
    private static int characterW;
    private static DotFlight flight;

    static {
        java.util.Arrays.fill(NEED_KEYS, Long.MIN_VALUE);
    }

    private TrackerHud() {
    }

    public static ResourceLocation icon(String name) {
        return ICONS.computeIfAbsent(name,
                n -> new ResourceLocation(Proficiency.MOD_ID, "textures/gui/hearth/" + n + ".png"));
    }

    /** A held row's "what it needs": an icon and "20/24" with icons on, the words ("Character 20/24") without. */
    record Need(@org.jetbrains.annotations.Nullable ResourceLocation icon, String text) {

        /** Lock glyph, icon, text. */
        int width(Font font) {
            return WarmthGlyph.WIDTH + (icon != null ? 9 : 0) + font.width(text);
        }
    }

    @org.jetbrains.annotations.Nullable
    static Need need(int lock, int need, @org.jetbrains.annotations.Nullable HearthStatePayload state, boolean icons) {
        int comfort = state == null ? 0 : state.comfort();
        int character = state == null ? 0 : state.character();
        if (icons) {
            String text = HearthText.needShort(lock, need, comfort, character);
            String icon = HearthText.needIcon(lock, need);
            return text == null ? null : new Need(icon == null ? null : icon(icon), text);
        }
        Component full = HearthText.needFull(lock, need, comfort, character);
        return full == null ? null : new Need(null, full.getString());
    }

    /** Draws a {@link Need} from {@code x}, cut to {@code room} pixels; {@code site} counts a cut for the layout audit. */
    static void drawNeed(GuiGraphics graphics, Font font, Need need, int x, int y, int room, int alpha, boolean shadow,
            String site) {
        int a24 = alpha << 24;
        graphics.drawString(font, String.valueOf(WarmthGlyph.LOCK), x, y, a24 | 0xFFFFFF, shadow);
        int cursor = x + WarmthGlyph.WIDTH;
        if (need.icon() != null) {
            HudKit.icon(graphics, need.icon(), cursor, y - 1, 8, alpha);
            cursor += 9;
        }
        String text = HudKit.clip(font, need.text(), Math.max(0, x + room - cursor));
        if (!text.equals(need.text())) {
            HudKit.note(site);
        }
        graphics.drawString(font, text, cursor, y, a24 | (HearthPalette.LOCKED & 0xFFFFFF), shadow);
    }

    static ResourceLocation poolIcon(int pool) {
        if (pool < 0 || pool >= POOL_ICONS.length) {
            return icon("comfort");
        }
        ResourceLocation texture = POOL_ICONS[pool];
        if (texture == null) {
            texture = icon("pool/" + HearthPool.VALUES[pool].id());
            POOL_ICONS[pool] = texture;
        }
        return texture;
    }

    static void reset() {
        PRESENCE.reset();
        SLIDE.reset();
        java.util.Arrays.fill(NEED_KEYS, Long.MIN_VALUE);
        footerKey = Long.MIN_VALUE;
    }

    /**
     * {@link #need} for one pool's tracker row, kept until its inputs change; its width (lock, icon,
     * text) is in {@code NEED_WIDTHS}, 0 for none.
     */
    @org.jetbrains.annotations.Nullable
    private static Need rowNeed(int pool, int lock, int need, @org.jetbrains.annotations.Nullable HearthStatePayload state,
            boolean icons, Font font, Language language) {
        int comfort = state == null ? 0 : state.comfort();
        int character = state == null ? 0 : state.character();
        long key = ((long) (lock & 0xFF) << 56) | ((long) (need & 0xFFFF) << 40) | ((long) (comfort & 0xFFFF) << 24)
                | ((long) (character & 0x7FFFFF) << 1) | (icons ? 1 : 0);
        if (NEED_KEYS[pool] != key || language != needLanguage) {
            if (language != needLanguage) {
                java.util.Arrays.fill(NEED_KEYS, Long.MIN_VALUE);
                needLanguage = language;
            }
            Need built = need(lock, need, state, icons);
            NEEDS[pool] = built;
            NEED_WIDTHS[pool] = built == null ? 0 : built.width(font);
            NEED_KEYS[pool] = key;
        }
        return NEEDS[pool];
    }

    /** The two footer lines' text, built again only when the numbers or the language change. */
    private static void footers(HearthStatePayload state, Font font, Language language) {
        int all = HearthView.comfortAll().size();
        int max = HearthView.characterMax();
        long key = ((long) (state.comfort() & 0xFFFF) << 48) | ((long) (all & 0xFFFF) << 32)
                | ((long) (state.character() & 0xFFFF) << 16) | (max & 0xFFFF);
        if (key != footerKey || language != footerLanguage) {
            comfortText = HearthText.comfort(state.comfort(), all).getString();
            characterText = HearthText.character(state.character(), max).getString();
            comfortW = font.width(comfortText);
            characterW = font.width(characterText);
            footerKey = key;
            footerLanguage = language;
        }
    }

    /** The dots' flight from the skill bar's launch point, built again only when that point moves. */
    private static DotFlight flight(int startX, int startY) {
        DotFlight cached = flight;
        if (cached == null || cached.startX() != startX || cached.startY() != startY) {
            cached = new DotFlight(startX, startY, 4, 22, 8);
            flight = cached;
        }
        return cached;
    }

    /**
     * Advances the model every frame (so the numerals stay true while the tracker is hidden) and
     * draws the tracker when it is on and there is anything to show.
     */
    static void render(GuiGraphics graphics, Minecraft minecraft, long now) {
        boolean dots = ProficiencyClientConfig.hudXpDots();
        boolean fx = ProficiencyClientConfig.hudLevelUpFx();
        int rose = HearthView.TRACKER.frame(now, dots, fx);
        PRESENCE.update(home(), now);
        int alpha = alpha(now);
        int top = 0;
        if (alpha > 8) {
            // Clear of a right-edge panel that would cover it (the death recap, the Tales panel): under it or
            // over it where there is room, so a tier-up the Tales pay in still plays here; where there is none
            // it fades with the panel's own fade, and a tier-up it misses goes to the chat (RightEdge).
            int guiHeight = graphics.guiHeight();
            int height = height();
            int mid = Math.round(guiHeight * ProficiencyClientConfig.hearthTrackerY());
            int wanted = Math.max(MIN_TOP, Math.min(guiHeight - BOTTOM_ROOM - height, mid - height / 2));
            RightEdge.Place place = RightEdge.place(wanted, height, MIN_TOP, guiHeight - BOTTOM_ROOM, now);
            if (place.squeezed()) {
                HudKit.note("hearth.tracker.gives_way");
            }
            top = SLIDE.follow(place.top(), now);
            alpha = Math.round(alpha * place.alpha());
        }
        boolean drawn = alpha > 8;
        // The chat filter is settled by this frame: a tier-up played on the screen replaces the line,
        // one that was not lets it through (HearthChat).
        HearthChat.settle(now, drawn && fx ? rose : 0, HearthView.TRACKER);
        if (!drawn) {
            return;
        }
        if (rose != 0 && fx) {
            chime(minecraft, rose);
        }
        draw(graphics, minecraft.font, alpha, now, top);
    }

    /** The tracker's height: its listed rows, and the Comfort and Character lines once a state is in. */
    private static int height() {
        int rows = 0;
        for (HearthTrackerModel.Row row : HearthView.TRACKER.rows()) {
            if (row.listed()) {
                rows++;
            }
        }
        return PAD + rows * ROW + (HearthView.state() != null ? 3 + 2 * ROW : 0) + PAD - 2;
    }

    /**
     * The HUD pass with the GUI hidden (F1): the numbers and the home clock move on and nothing is drawn,
     * so neither a tier-up nor a walk out replays when the GUI comes back; a held tier-up line whose
     * numeral turned over unseen goes to the chat. Both loaders' hidden branches call this one method.
     */
    static void hidden(long now) {
        HearthView.TRACKER.frame(now, false, false);
        PRESENCE.update(home(), now);
        HearthChat.settle(now, 0, HearthView.TRACKER);
    }

    /** At home at the Hearth the tracker follows. */
    private static boolean home() {
        HearthHudPayload hud = HearthView.hud();
        return hud != null && hud.home() && sameHearth(hud);
    }

    /**
     * The tracker's own opacity this frame, 0..255; 0 when it is off or has no row to show. A right-edge panel in
     * its way is {@link #render}'s ({@link RightEdge}).
     */
    private static int alpha(long now) {
        if (!(ProficiencyClientConfig.hearthTracker() || HearthDemo.trackerForced()) || !HearthView.TRACKER.any()) {
            return 0;
        }
        return Math.round(255 * HudKit.smooth(PRESENCE.opacity(now)));
    }

    private static boolean sameHearth(HearthHudPayload hud) {
        return HearthView.TRACKER.hearth() == hud.hearthPos();
    }

    private static void chime(Minecraft minecraft, int rose) {
        for (HearthTrackerModel.Row row : HearthView.TRACKER.rows()) {
            if ((rose & (1 << row.pool())) == 0) {
                continue;
            }
            int tier = row.displayedTier();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(),
                    0.9f + 0.08f * tier, 0.5f));
            if (TierUpFx.isGold(tier)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.9f));
            }
            return;
        }
    }

    private static int romanWidth(Font font, Language language) {
        if (romanW < 0 || language != romanLanguage) {
            romanW = 0;
            for (int tier = 0; tier <= 6; tier++) {
                romanW = Math.max(romanW, font.width(HearthText.tierMark(tier)));
            }
            romanLanguage = language;
        }
        return romanW;
    }

    private static void draw(GuiGraphics graphics, Font font, int alpha, long now, int top) {
        HearthStatePayload state = HearthView.state();
        // A smouldering Hearth gives nothing: no lock to name, every row dimmed.
        boolean smoulders = state != null && state.smouldering();
        HearthTrackerModel model = HearthView.TRACKER;
        Language language = Language.getInstance();
        int roman = romanWidth(font, language);
        boolean icons = HudKit.iconsOn();
        int iconAdv = icons ? 8 + 2 : 0;
        int rows = 0;
        int widest = 0;
        for (HearthTrackerModel.Row row : model.rows()) {
            FRAME_NEEDS[row.pool()] = null;
            if (!row.listed()) {
                continue;
            }
            rows++;
            int width = iconAdv + roman + 3 + BAR_W;
            Need need = smoulders ? null : rowNeed(row.pool(), row.lock(), row.need(), state, icons, font, language);
            FRAME_NEEDS[row.pool()] = need;
            if (need != null) {
                width += 3 + NEED_WIDTHS[row.pool()];
            }
            widest = Math.max(widest, width);
        }
        if (state != null) {
            footers(state, font, language);
            widest = Math.max(widest, iconAdv + comfortW);
            widest = Math.max(widest, iconAdv + characterW);
        }
        // Never wider than a third of the screen: a long translation is cut, the hover-less HUD has no room.
        int maxWidth = Math.max(80, graphics.guiWidth() / 3);
        // On a narrow screen the bars give way first (down to 24 px), then the text is cut.
        int barW = BAR_W;
        if (widest > maxWidth) {
            barW = Math.max(24, BAR_W - (widest - maxWidth));
            widest -= BAR_W - barW;
        }
        int inner = Math.min(widest, maxWidth);
        int height = PAD + rows * ROW + (state != null ? 3 + 2 * ROW : 0) + PAD - 2;
        int width = inner + PAD * 2;
        int x = graphics.guiWidth() - RIGHT_MARGIN - width;
        int y = top;

        graphics.fill(x, y, x + width, y + height, ((int) (alpha * 0.55f) << 24) | 0x0E1014);
        graphics.fill(x, y, x + width, y + 1, (alpha << 24) | (HearthPalette.EMBER & 0xFFFFFF));
        int left = x + PAD;
        int rowY = y + PAD + 1;
        int startX = SkillHud.dotAnchorX(graphics.guiWidth(), now);
        int startY = SkillHud.dotAnchorY(graphics.guiHeight(), now);
        DotFlight flight = flight(startX, startY);
        for (HearthTrackerModel.Row row : model.rows()) {
            if (!row.listed()) {
                continue;
            }
            drawRow(graphics, font, row, model, left, rowY, roman, iconAdv, inner, barW, smoulders ? alpha / 2 : alpha, now,
                    flight, FRAME_NEEDS[row.pool()]);
            rowY += ROW;
        }
        if (state != null) {
            graphics.fill(left, rowY, left + inner, rowY + 1, (alpha / 5 << 24) | 0xFFFFFF);
            rowY += 3;
            footer(graphics, font, COMFORT_ICON, comfortText, comfortW, left, rowY, inner, iconAdv, alpha,
                    state.comfort() >= HearthView.comfortAll().size());
            rowY += ROW;
            footer(graphics, font, CHARACTER_ICON, characterText, characterW, left, rowY, inner, iconAdv, alpha,
                    state.character() >= HearthView.characterMax());
        }
    }

    private static void footer(GuiGraphics graphics, Font font, ResourceLocation icon, String text, int textW, int left,
            int y, int inner, int iconAdv, int alpha, boolean full) {
        if (iconAdv > 0) {
            HudKit.icon(graphics, icon, left, y - 1, 8, alpha);
        }
        String shown = textW <= inner - iconAdv ? text : HudKit.clip(font, text, inner - iconAdv);
        if (!shown.equals(text)) {
            HudKit.note("hearth.tracker");
        }
        graphics.drawString(font, shown, left + iconAdv, y, (alpha << 24) | ((full ? SkillPalette.MAXED : SkillPalette.TEXT)
                & 0xFFFFFF), true);
    }

    private static void drawRow(GuiGraphics graphics, Font font, HearthTrackerModel.Row row, HearthTrackerModel model,
            int left, int y, int romanW, int iconAdv, int inner, int barW, int alpha, long now, DotFlight flight,
            @org.jetbrains.annotations.Nullable Need need) {
        int colour = HearthPalette.pool(row.pool()) & 0xFFFFFF;
        int a24 = alpha << 24;
        TierUpFx fx = row.fx();
        boolean playing = fx.active(now);
        boolean gold = playing && fx.gold();
        float flash = playing ? fx.flash(now) : 0f;
        if (iconAdv > 0) {
            HudKit.icon(graphics, poolIcon(row.pool()), left, y - 1, 8, alpha);
        }
        // The numeral, rolling over while its tier-up plays.
        int numX = left + iconAdv;
        int tier = row.displayedTier();
        int tierColour = HearthPalette.tier(row.pool(), tier, model.maxTier()) & 0xFFFFFF;
        if (flash > 0f) {
            tierColour = HudKit.lerp(tierColour, gold ? GOLD : 0xFFFFFF, Math.min(1f, flash * 0.85f));
        }
        float roll = fx.roll(now);
        if (playing && roll < 1f) {
            int slide = Math.round(roll * 9);
            int oldA = Math.round(alpha * (1f - roll));
            int newA = Math.round(alpha * roll);
            graphics.enableScissor(numX - 1, y - 1, numX + romanW + 1, y + 9);
            if (oldA > 8) {
                graphics.drawString(font, HearthText.tierMark(fx.oldTier()), numX, y - slide, (oldA << 24) | tierColour, true);
            }
            if (newA > 8) {
                graphics.drawString(font, HearthText.tierMark(tier), numX, y + 9 - slide, (newA << 24) | tierColour, true);
            }
            graphics.disableScissor();
        } else {
            graphics.drawString(font, HearthText.tierMark(tier), numX, y, a24 | tierColour, true);
        }

        // The bar to the next tier, in the pool's colour; gold and full at the top tier.
        int barLeft = numX + romanW + 3;
        int barTop = y + 3;
        boolean locked = row.lock() != 0;
        graphics.fill(barLeft, barTop, barLeft + barW, barTop + 2, a24 | (SkillPalette.TRACK & 0xFFFFFF));
        double fill = model.fill(row.shown());
        int filled = (int) Math.round(barW * fill);
        int barColour = row.shown() >= model.maxTier() ? GOLD : colour;
        if (locked) {
            // Held back: the charge still shows, in a quieter tone, so feeding it still reads.
            barColour = HudKit.lerp(barColour, 0x707070, 0.45f);
        }
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + filled, barTop + 2, a24 | barColour);
        }
        if (flash > 0f) {
            int glow = Math.round(alpha * flash * 0.85f);
            if (glow > 8) {
                graphics.fill(barLeft - 1, barTop - 1, barLeft + barW + 1, barTop + 3,
                        (glow << 24) | (gold ? GOLD : 0xFFFFFF));
            }
        }
        if (playing) {
            ring(graphics, numX + romanW / 2, y + 3, fx, alpha, gold ? GOLD : HearthPalette.brighten(colour), now);
        }
        // Dots in flight to the fill edge.
        flight.draw(graphics, row.dots(), barLeft + filled, barTop + 1, colour, HearthPalette.brighten(colour), alpha,
                now);
        if (need != null) {
            int lockX = barLeft + barW + 3;
            drawNeed(graphics, font, need, lockX, y, left + inner - lockX, alpha, true, "hearth.tracker.need");
        }
    }

    /** The ring that leaves the numeral, as the skill HUD's level-up ring leaves its bar, smaller. */
    private static void ring(GuiGraphics graphics, int cx, int cy, TierUpFx fx, int alpha, int rgb, long now) {
        float t = fx.t(now);
        if (t < 0f || t >= 1f) {
            return;
        }
        float e = fx.ring(now);
        boolean gold = fx.gold();
        float reach = gold ? 26f : 16f;
        int a = Math.round(alpha * (1f - t) * 0.9f);
        if (a > 8) {
            HudKit.ring(graphics, cx, cy, 5f + e * reach, 3f + e * reach * 0.6f, (a << 24) | (rgb & 0xFFFFFF),
                    (1f - t) > 0.5f ? 2 : 1, 1, 64);
        }
        if (gold) {
            float t2 = t - 0.18f;
            if (t2 > 0f) {
                float e2 = 1f - (1f - t2) * (1f - t2) * (1f - t2);
                int a2 = Math.round(alpha * (1f - t2) * 0.8f);
                if (a2 > 8) {
                    HudKit.ring(graphics, cx, cy, 4f + e2 * 20f, 3f + e2 * 12f, (a2 << 24) | 0xFFFFFF, 1, 2, 64);
                }
            }
            for (int k = 0; k < 6; k++) {
                if (((now / 70) + k) % 3 == 0) {
                    continue;
                }
                int index = (k * 23) % 64;
                int px = cx + Math.round(HudKit.ringX(index) * (6f + e * reach) * 0.8f);
                int py = cy + Math.round(HudKit.ringY(index) * (4f + e * reach * 0.5f) - e * 6f);
                int sa = Math.round(alpha * (1f - t) * 0.95f);
                if (sa > 8) {
                    graphics.fill(px, py, px + 2, py + 2, (sa << 24) | (k % 2 == 0 ? GOLD : 0xFFFFFF));
                }
            }
        }
    }
}
