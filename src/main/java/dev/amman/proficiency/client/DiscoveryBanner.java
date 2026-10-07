package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.DiscoveryNames;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderGuiEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * The zone-name banner: walk into a biome, dimension or structure for the first time and its name
 * fades in across the top of the screen, like crossing into a new region in WoW, with the XP it
 * paid underneath in small type. Silent unless the player turns
 * the sound on in the client config.
 *
 * <p>Several at once (a portal drops you into a new dimension and a new biome in the same check)
 * queue up and play one after another rather than stacking.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class DiscoveryBanner {

    private static final long FADE_IN_MS = 600;
    private static final long HOLD_MS = 3200;
    private static final long FADE_OUT_MS = 1200;
    /** With another banner waiting, the current one gives way sooner. */
    private static final long HOLD_QUEUED_MS = 1600;

    private static final int TITLE_COLOUR = 0xFFD100;
    private static final int KICKER_COLOUR = 0xC9B27C;
    private static final float TITLE_SCALE = 3.0f;
    private static final int RULE_LENGTH = 48;
    /** Tallest a banner gets at scale 1: kicker, a full-size title, and the XP line. */
    private static final int MAX_HEIGHT = 54;

    /** A first-time kind (a new ore for Mining) is small news next to a new biome. */
    private static final float FIRST_SCALE = 2.0f;
    private static final long FIRST_HOLD_MS = 1800;
    /** First-time kinds come in bursts early on; past this many waiting, they are dropped. */
    private static final int MAX_QUEUED = 4;

    /** Re-entering a known structure: WoW zone text, smaller and quicker, with no kicker. */
    private static final float ENTER_SCALE = 1.6f;
    private static final long ENTER_HOLD_MS = 1500;
    private static final long ENTER_MAX_WAIT_MS = 3000;

    /** {@code kicker} and {@code xp} are null for a re-entry banner. */
    private record Banner(int kind, Component title, Component xp, Component kicker, Skill skill,
            long queuedAt) {
    }

    private static final Deque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner current;
    private static long startedAt;
    /** Where the banner showing now ends, in GUI pixels; 0 with none. The XP feed starts below it. */
    private static int bottom;

    private DiscoveryBanner() {
    }

    /** Whether the player's settings let this kind of banner show. The XP is paid regardless. */
    static boolean allowed(int kind) {
        if (!ProficiencyClientConfig.bannersEnabled()) {
            return false;
        }
        return switch (kind) {
            case DiscoveryPayload.ENTER -> ProficiencyClientConfig.bannerEntry();
            case DiscoveryPayload.FIRST -> ProficiencyClientConfig.bannerFirstTime();
            default -> ProficiencyClientConfig.bannerDiscoveries();
        };
    }

    public static void show(DiscoveryPayload payload) {
        if (!allowed(payload.kind())) {
            return;
        }
        if (payload.kind() == DiscoveryPayload.ENTER) {
            // Small news: at most one waits behind the others, and a newer one replaces it. It is
            // dropped in onRenderGui if it waited too long to still be true.
            QUEUE.removeIf(banner -> banner.kind() == DiscoveryPayload.ENTER);
            QUEUE.add(new Banner(payload.kind(), name(payload.name(), payload.id()), null, null,
                    Skill.WAYFARING, System.currentTimeMillis()));
            return;
        }
        boolean first = payload.kind() == DiscoveryPayload.FIRST;
        // A place always shows; a first-time kind only while the queue is short. Its XP is paid
        // either way, and the XP log and feed still list it.
        if (first && QUEUE.size() >= MAX_QUEUED) {
            return;
        }
        Skill skill = payload.skill() >= 0 && payload.skill() < Skill.VALUES.length
                ? Skill.VALUES[payload.skill()] : Skill.WAYFARING;
        Component title = first
                ? Component.literal(TalentTreeScreen.sourceName(payload.name()))
                : name(payload.name(), payload.id());
        Component xp = null;
        if (payload.xp() > 0) {
            xp = Component.translatable("proficiency.discovery.xp",
                    formatXp(payload.xp()), Component.translatable(skill.translationKey()));
        }
        Component kicker = Component.translatable(switch (payload.kind()) {
            case DiscoveryPayload.DIMENSION -> "proficiency.discovery.dimension";
            case DiscoveryPayload.STRUCTURE -> "proficiency.discovery.structure";
            case DiscoveryPayload.FIRST -> "proficiency.discovery.first";
            default -> "proficiency.discovery.biome";
        }, Component.translatable(skill.translationKey()));
        if (!payload.with().isEmpty()) {
            kicker = Component.translatable("proficiency.discovery.with", kicker, payload.with());
        }
        QUEUE.add(new Banner(payload.kind(), title, xp, kicker, skill, System.currentTimeMillis()));
    }

    /** A new world starts clean; a banner from the last one must not play in this one. */
    public static void clear() {
        QUEUE.clear();
        current = null;
        bottom = 0;
    }

    static int bottom() {
        return current == null ? 0 : bottom;
    }

    /**
     * The translated name if the game has one, otherwise one made from the id: most modded
     * structures, and every dimension, ship with no name at all, and "Skeleton Dungeon" beats
     * "structure.yungsbetterdungeons.skeleton_dungeon" across the top of the screen.
     */
    static Component name(String key, String id) {
        return DiscoveryNames.component(key, id);
    }

    static String formatXp(float xp) {
        return xp >= 10 ? String.valueOf(Math.round(xp)) : String.format(Locale.ROOT, "%.1f", xp);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        long now = System.currentTimeMillis();
        if (current == null) {
            current = QUEUE.poll();
            // A re-entry banner that waited this long names a place the player has left.
            while (current != null && current.kind() == DiscoveryPayload.ENTER
                    && now - current.queuedAt() > ENTER_MAX_WAIT_MS) {
                current = QUEUE.poll();
            }
            if (current == null) {
                return;
            }
            startedAt = now;
            // A soft toast for a discovery; never for the small re-entry banner.
            if (ProficiencyClientConfig.bannerSound() && current.kind() != DiscoveryPayload.ENTER) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_TOAST_IN, 1.0f, 0.6f));
            }
        }
        long hold = current.kind() == DiscoveryPayload.FIRST ? FIRST_HOLD_MS
                : current.kind() == DiscoveryPayload.ENTER ? ENTER_HOLD_MS
                : QUEUE.isEmpty() ? HOLD_MS : HOLD_QUEUED_MS;
        long age = now - startedAt;
        if (age > FADE_IN_MS + hold + FADE_OUT_MS) {
            current = null;
            bottom = 0;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        float alpha;
        if (age < FADE_IN_MS) {
            alpha = age / (float) FADE_IN_MS;
        } else if (age < FADE_IN_MS + hold) {
            alpha = 1f;
        } else {
            alpha = 1f - (age - FADE_IN_MS - hold) / (float) FADE_OUT_MS;
        }
        // Eased, so it swells in and melts out rather than ramping.
        alpha = alpha * alpha * (3f - 2f * alpha);
        draw(event.getGuiGraphics(), minecraft.font, current, alpha);
    }

    private static void draw(GuiGraphics graphics, Font font, Banner banner, float alpha) {
        // The font treats an alpha under 4 as fully opaque, so a nearly-gone banner would flash.
        int a = Math.round(alpha * 255);
        if (a < 8) {
            return;
        }
        int width = graphics.guiWidth();
        float userScale = ProficiencyClientConfig.bannerScale();
        // Clamp so the whole banner stays on screen whatever the setting and the window size.
        int maxTop = graphics.guiHeight() - 4 - Math.round(MAX_HEIGHT * userScale);
        int topPx = Math.max(4, Math.min(maxTop,
                Math.round(graphics.guiHeight() * ProficiencyClientConfig.bannerYFraction())));
        // The whole banner is drawn around its own origin (top centre) and scaled once, so the
        // player's size setting moves the kicker, rules and XP line together with the title.
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, topPx, 0);
        graphics.pose().scale(userScale, userScale, 1f);
        int bottomLocal = drawLocal(graphics, font, banner, a, width / userScale);
        graphics.pose().popPose();
        bottom = topPx + Math.round(bottomLocal * userScale);
    }

    /** Draws at origin (0, 0) = top centre, and returns the local y where the banner ends. */
    private static int drawLocal(GuiGraphics graphics, Font font, Banner banner, int a, float width) {
        int top = 0;

        Component kicker = banner.kicker();
        if (kicker != null) {
            int kickerWidth = font.width(kicker);
            // The kicker shrinks to fit a narrow window; the rules need 54 pixels a side, so
            // they go when they would run off the edge.
            float kickerScale = Math.min(1f, width * 0.95f / Math.max(1, kickerWidth));
            boolean rules = kickerWidth * kickerScale / 2 + 6 + RULE_LENGTH <= width / 2f;
            graphics.pose().pushPose();
            graphics.pose().scale(kickerScale, kickerScale, 1f);
            graphics.drawString(font, kicker, -kickerWidth / 2, top, (a << 24) | KICKER_COLOUR, true);
            if (rules) {
                rule(graphics, -kickerWidth / 2 - 6, top + 4, -1, a);
                rule(graphics, kickerWidth / 2 + 5, top + 4, 1, a);
            }
            graphics.pose().popPose();
        }
        int titleTop = kicker == null ? top + 4 : top + 12;

        // Shrink a long modded name to fit rather than run off both edges.
        int titleWidth = font.width(banner.title());
        float full = banner.kind() == DiscoveryPayload.FIRST ? FIRST_SCALE
                : banner.kind() == DiscoveryPayload.ENTER ? ENTER_SCALE : TITLE_SCALE;
        float scale = Math.min(full, width * 0.9f / Math.max(1, titleWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(0, titleTop, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, banner.title(), -titleWidth / 2, 0, (a << 24) | TITLE_COLOUR, true);
        graphics.pose().popPose();

        int y = titleTop + Math.round(9 * scale) + 5;
        if (banner.xp() != null) {
            graphics.drawString(font, banner.xp(), -font.width(banner.xp()) / 2, y,
                    (a << 24) | (SkillPalette.accent(banner.skill().category()) & 0xFFFFFF), true);
        }
        return y + 10;
    }

    /** A thin gold line that fades out away from the kicker, drawn a pixel column at a time. */
    private static void rule(GuiGraphics graphics, int x, int y, int direction, int alpha) {
        int length = RULE_LENGTH;
        for (int i = 0; i < length; i++) {
            int a = Math.round(alpha * (1f - i / (float) length) * 0.8f);
            if (a <= 0) {
                break;
            }
            int px = x + direction * i;
            graphics.fill(px, y, px + 1, y + 1, (a << 24) | KICKER_COLOUR);
        }
    }
}
