package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.TaleTrip;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.net.DiscoveryNames;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel after a tale is told at the Hearth: Tales paid in on a homecoming (how far you walked,
 * what you found, what the Hearth took), a Chronicle page set down, a rated tale retold (its stars
 * light one by one). It mirrors {@link DeathRecapHud}: the right edge, the same fade in and out, the
 * same panel and border, the same text fitting, the number rolling up like a level does.
 *
 * <p>It sits at the right edge at the death recap's height (30%), below the toasts and below a minimap in
 * the top right corner (Xaero's in the Reclamation and Pandowo packs; at 10% the panel was drawn over it,
 * polish pass, Forge 1.20.1 real client, 2026-10-10). Like the recap it may run down to the hotbar's rows; it
 * claims the rows it covers on that edge ({@link RightEdge}), so the Hearth tracker steps under it where there is
 * room (a tier-up the pay-in brings plays there) and fades with it only where there is none. On a window too
 * short for every row the places fold away before anything overlaps.
 * Shown for about five seconds, never twice at once (a new tale replaces the old one). Drawn only with
 * the GUI on.
 *
 * <p>Forge 1.20.1 port of common's panel: registered on the client's Forge bus by its annotation (as
 * {@code SkillHud}), drawn from {@code RenderGuiEvent.Post}.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class TalesPayInHud {

    public static final long TOTAL_MS = 5000;
    public static final long FADE_IN_MS = 250;
    public static final long FADE_OUT_MS = 700;
    /** The number starts rolling once the panel is up, and takes this long. */
    public static final long ROLL_DELAY_MS = 350;
    public static final long ROLL_MS = 900;
    public static final long STAR_DELAY_MS = 400;
    public static final long STAR_STAGGER_MS = 130;
    /** How far the panel slides in from the edge while it fades in. */
    private static final int SLIDE = 10;

    private static final int MIN_WIDTH = 124;
    private static final int RIGHT_MARGIN = 4;
    private static final int ROW = 10;
    private static final int GOLD = 0xFFD100;
    private static final int PALE = 0xE8E1C8;
    private static final int DIM = 0xA0A6AE;
    private static final int AMBER = 0xE8A33D;
    private static final int UNLIT_STAR = 0x4A4030;
    /** The Tales scroll as a font glyph, for when the skill icons are switched off. */
    private static final String SCROLL = "";
    private static final ResourceLocation TALES_ICON = Proficiency.id("textures/gui/hearth/tales.png");
    private static final ResourceLocation[] POOL_ICONS = new ResourceLocation[HearthPool.COUNT];

    /** A tale as the panel keeps it, its strings built once when it arrives. */
    private record Tale(int kind, String title, List<String> lines, int pool, String poolName, float amount, int stars) {
    }

    private static Tale tale;
    private static boolean pending;
    private static long start = Long.MIN_VALUE / 2;
    private static int laidOutWidth = -1;
    private static int laidOutHeight = -1;
    private static int laidOutTop = -1;
    private static int panelWidth = MIN_WIDTH;
    private static int panelTop = 36;
    /** The panel's eased top while it steps under a discovery banner and back (the siege panel's way). */
    private static float shownTop = Float.NaN;
    private static long shownTopAt;
    private static List<String> titleLines = List.of();
    private static List<String> bodyLines = List.of();

    private TalesPayInHud() {
    }

    /** Client thread, from the packet. */
    public static synchronized void accept(HearthTalesPayload payload) {
        List<String> lines = new ArrayList<>();
        String titleKey;
        switch (payload.kind()) {
            case HearthTalesPayload.CHRONICLE -> {
                titleKey = "proficiency.hearth.tales.panel.chronicle";
                if (!payload.title().isEmpty()) {
                    lines.add("\"" + payload.title() + "\"");
                }
            }
            case HearthTalesPayload.RETOLD -> {
                titleKey = "proficiency.hearth.tales.panel.retold";
                if (!payload.title().isEmpty()) {
                    lines.add("\"" + payload.title() + "\"");
                }
            }
            default -> {
                titleKey = "proficiency.hearth.tales.panel.paid";
                if (payload.blocks() > 0) {
                    lines.add(Component.translatable("proficiency.hearth.tales.panel.blocks",
                            TaleTrip.group(payload.blocks())).getString());
                }
                for (String place : payload.places()) {
                    lines.add("· " + placeName(place));
                }
            }
        }
        HearthPool pool = HearthPool.byIndex(payload.pool());
        String poolName = pool == null ? "" : Component.translatable(pool.translationKey()).getString();
        tale = new Tale(payload.kind(), Component.translatable(titleKey).getString(), lines, payload.pool(), poolName,
                payload.amount(), payload.kind() == HearthTalesPayload.RETOLD ? Math.max(0, Math.min(5, payload.stars())) : -1);
        laidOutWidth = -1;
        laidOutHeight = -1;
        pending = true;
        start = Long.MIN_VALUE / 2;
    }

    private static ResourceLocation poolIcon(int pool) {
        HearthPool p = HearthPool.byIndex(pool);
        if (p == null) {
            return null;
        }
        ResourceLocation icon = POOL_ICONS[pool];
        if (icon == null) {
            icon = Proficiency.id("textures/gui/hearth/pool/" + p.id() + ".png");
            POOL_ICONS[pool] = icon;
        }
        return icon;
    }

    /** The room the title's icon takes: the 8 px texture and its gap, or the font glyph and a gap. */
    private static int titleIconAdvance(Font font) {
        return SkillIcons.enabled() ? SkillIcons.advance(SkillIcons.SMALL) : font.width(SCROLL) + 4;
    }

    /** A place's own translation, or a name made from its id when the game has none. */
    private static String placeName(String languageKey) {
        int dot = languageKey.indexOf('.');
        String rest = dot < 0 ? languageKey : languageKey.substring(dot + 1);
        int namespaceEnd = rest.indexOf('.');
        String id = namespaceEnd < 0 ? rest
                : rest.substring(0, namespaceEnd) + ":" + rest.substring(namespaceEnd + 1).replace('.', '/');
        return DiscoveryNames.component(languageKey, id).getString();
    }

    /** Panel opacity, 0 to 1: a quick fade in, steady, then a slower fade out (the recap's own curve). */
    public static float opacity(long elapsed) {
        if (elapsed < 0 || elapsed >= TOTAL_MS) {
            return 0f;
        }
        float in = Math.min(1f, elapsed / (float) FADE_IN_MS);
        float out = Math.min(1f, (TOTAL_MS - elapsed) / (float) FADE_OUT_MS);
        return Math.min(in, out);
    }

    /** How much of the amount shows, 0 to 1 (eased), at {@code elapsed} ms. */
    public static float roll(long elapsed) {
        float t = (elapsed - ROLL_DELAY_MS) / (float) ROLL_MS;
        return (float) XpGainDots.ease(Math.max(0f, Math.min(1f, t)));
    }

    /** How many stars are lit at {@code elapsed} ms, one more every {@link #STAR_STAGGER_MS}. */
    public static int starsLit(int stars, long elapsed) {
        if (elapsed < STAR_DELAY_MS) {
            return 0;
        }
        return (int) Math.min(stars, 1 + (elapsed - STAR_DELAY_MS) / STAR_STAGGER_MS);
    }

    /** Drops the panel and anything waiting to show: its five seconds end, or the player left the world. */
    public static synchronized void reset() {
        pending = false;
        tale = null;
        start = Long.MIN_VALUE / 2;
        shownTop = Float.NaN;
        RightEdge.release(RightEdge.TALES);
    }

    /**
     * Where the panel's top belongs: the death recap's row ({@link RightEdge#panelTop}, 30%), or just under a
     * discovery banner ({@code bannerBottom}, 0 with none) that would cover it. A homecoming's first Tales at a Hearth come with Homebuilder's
     * first-time banner; on a narrow screen the banner runs under the panel. The death recap and the siege panel
     * step under it too.
     */
    static int wantedTop(int guiHeight, int bannerBottom) {
        return RightEdge.panelTop(guiHeight, bannerBottom);
    }

    /** Eases the drawn top towards {@code target} over about 200 ms, so the panel slides instead of jumping. */
    private static int easedTop(int target, long now) {
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

    private static void layout(Font font, int guiWidth, int guiHeight) {
        int top = wantedTop(guiHeight, DiscoveryBanner.bottom());
        if (laidOutWidth == guiWidth && laidOutHeight == guiHeight && laidOutTop == top) {
            return;
        }
        laidOutWidth = guiWidth;
        laidOutHeight = guiHeight;
        laidOutTop = top;
        int limit = Math.max(MIN_WIDTH, Math.min(guiWidth - RIGHT_MARGIN * 2, guiWidth * 6 / 10));
        int iconRoom = titleIconAdvance(font);
        int widest = iconRoom + font.width(tale.title());
        for (String line : tale.lines()) {
            widest = Math.max(widest, font.width(line));
        }
        widest = Math.max(widest, SkillIcons.advance(SkillIcons.SMALL) + font.width("+0000  " + tale.poolName()));
        if (tale.stars() >= 0) {
            widest = Math.max(widest, MasteryStars.width(5));
        }
        panelWidth = Math.max(MIN_WIDTH, Math.min(limit, widest + 14));
        int text = panelWidth - 12;
        boolean[] cut = new boolean[1];
        titleLines = TextFit.wrapLimited(font, tale.title(), text - iconRoom, 2, cut);
        List<String> body = new ArrayList<>();
        for (String line : tale.lines()) {
            body.addAll(TextFit.wrapLimited(font, line, text, 2, cut));
        }
        // Above the hotbar's rows, like the death recap (the tracker steps clear): fold the body until it fits.
        panelTop = top;
        int fixed = 5 + titleLines.size() * 9 + 4 + ROW + 4 + (tale.stars() >= 0 ? ROW : 0);
        int room = guiHeight - 44 - panelTop - fixed;
        int rows = Math.max(0, Math.min(body.size(), room / ROW));
        if (rows < body.size()) {
            TextFit.note("tales.rows_folded");
        }
        bodyLines = body.subList(0, rows);
        if (cut[0]) {
            TextFit.note("tales.text");
        }
    }

    /**
     * How strongly the panel is on screen now, 0 to 1 (its own fade; 0 while it waits for a screen to go). The
     * Hearth tracker steps clear of the rows it claims on the right edge ({@link RightEdge}).
     */
    public static float showing() {
        if (tale == null || pending || start < 0) {
            return 0f;
        }
        return opacity(System.currentTimeMillis() - start);
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, long now) {
        if (tale == null || (!pending && start < 0)) {
            return;
        }
        if (pending) {
            // Wait for a death screen or a loading screen to go.
            if (minecraft.screen != null) {
                return;
            }
            pending = false;
            start = now;
        }
        long elapsed = now - start;
        if (elapsed >= TOTAL_MS) {
            reset();
            return;
        }
        float opacity = opacity(elapsed);
        int alpha = Math.round(255 * opacity);
        if (alpha <= 8) {
            return;
        }
        Font font = minecraft.font;
        layout(font, graphics.guiWidth(), graphics.guiHeight());
        int bodyHeight = bodyLines.size() * ROW;
        int titleHeight = 5 + titleLines.size() * 9 + 4;
        int starsHeight = tale.stars() >= 0 ? ROW : 0;
        boolean amount = tale.amount() >= 0.5f;
        int amountHeight = amount ? ROW + 4 : 0;
        int height = titleHeight + bodyHeight + starsHeight + amountHeight + 4;
        int slide = Math.round(SLIDE * (1f - Math.min(1f, elapsed / (float) FADE_IN_MS)));
        int x = graphics.guiWidth() - panelWidth - RIGHT_MARGIN + slide;
        int y = easedTop(panelTop, now);
        RightEdge.claim(RightEdge.TALES, y, y + height, opacity, net.minecraft.Util.getMillis());

        graphics.fill(x, y, x + panelWidth, y + height, ((int) (alpha * 0.72f) << 24) | 0x0E1014);
        graphics.fill(x, y, x + panelWidth, y + 1, (alpha << 24) | GOLD);
        graphics.fill(x, y + height - 1, x + panelWidth, y + height, (alpha / 3 << 24) | 0x2C333D);

        int titleY = y + 5;
        if (SkillIcons.enabled()) {
            SkillIcons.drawTexture(graphics, TALES_ICON, x + 6, SkillIcons.smallTop(titleY), SkillIcons.SMALL, alpha);
        } else {
            graphics.drawString(font, SCROLL, x + 6, titleY, (alpha << 24) | 0xFFFFFF, true);
        }
        int titleX = x + 6 + titleIconAdvance(font);
        for (String line : titleLines) {
            graphics.drawString(font, line, titleX, titleY, (alpha << 24) | GOLD, true);
            titleY += 9;
        }
        int rowY = y + titleHeight;
        for (String line : bodyLines) {
            graphics.drawString(font, line, x + 6, rowY, (alpha << 24) | PALE, true);
            rowY += ROW;
        }
        if (tale.stars() >= 0) {
            int lit = starsLit(tale.stars(), elapsed);
            for (int i = 0; i < 5; i++) {
                int colour = i < lit ? MasteryStars.GOLD : UNLIT_STAR;
                if (i == lit - 1 && elapsed - STAR_DELAY_MS - i * STAR_STAGGER_MS < 160) {
                    colour = 0xFFFFFF;
                }
                graphics.drawString(font, String.valueOf(MasteryStars.GLYPH), x + 6 + i * MasteryStars.WIDTH, rowY,
                        (alpha << 24) | colour, true);
            }
            rowY += ROW;
        }
        if (amount) {
            rowY += 2;
            graphics.fill(x + 6, rowY - 2, x + panelWidth - 6, rowY - 1, (alpha / 5 << 24) | 0xFFFFFF);
            float rolled = roll(elapsed);
            String number = "+" + TaleTrip.charge(tale.amount() * rolled);
            int colour = rolled >= 1f && elapsed - ROLL_DELAY_MS - ROLL_MS < 220 ? 0xFFFFFF : GOLD;
            int numberX = x + 6;
            if (SkillIcons.enabled()) {
                SkillIcons.drawTexture(graphics, poolIcon(tale.pool()), numberX, SkillIcons.smallTop(rowY),
                        SkillIcons.SMALL, alpha);
                numberX += SkillIcons.advance(SkillIcons.SMALL);
            }
            graphics.drawString(font, number, numberX, rowY, (alpha << 24) | colour, true);
            int poolX = numberX + font.width("+" + TaleTrip.charge(tale.amount())) + 6;
            graphics.drawString(font, TextFit.clip(font, tale.poolName(), x + panelWidth - 6 - poolX), poolX, rowY,
                    (alpha << 24) | AMBER, true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            reset();
            return;
        }
        if (minecraft.options.hideGui) {
            return;
        }
        render(event.getGuiGraphics(), minecraft, System.currentTimeMillis());
    }
}
