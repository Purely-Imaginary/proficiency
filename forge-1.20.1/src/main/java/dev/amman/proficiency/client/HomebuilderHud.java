package dev.amman.proficiency.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.homebuilder.net.HomebuilderNet;
import dev.amman.proficiency.homebuilder.net.HomebuilderPayload;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Homebuilder on the client (docs/HOMEBUILDER-SPEC.md, "Finish and polish"): what the server's
 * {@link HomebuilderPayload} says, and the two things it draws, both in the idiom of
 * {@link AbilityFx}.
 *
 * <ul>
 * <li><b>Homeward's channel</b>: a warm-gold ring round the crosshair that fills clockwise as the
 * channel runs, with "Homeward" and the seconds left under it. It eases in; a broken channel flashes
 * red and melts out, an arrival throws a gold ring outwards. Drawn outside the ability ring.</li>
 * <li><b>A friend's House Proud</b>: the ability's own edge glow, gold and at half strength, while
 * the shared passive lasts.</li>
 * </ul>
 * Both follow the {@code hud.abilityFx} switch. The ability wheel reads the magnet switch and the
 * Homeward day from here for its two Homebuilder slots.
 *
 * <p>Forge 1.20.1 port of common's class: the HUD listener is Forge's {@code RenderGuiEvent.Post}, as
 * {@code SkillHud}'s; installed from {@code ProficiencyClientSetup}.
 */
public final class HomebuilderHud {

    /** House Proud's warm gold (the caster's ability glow and ring, and the friend's half glow). */
    public static final int GOLD = 0xFFE8B04A;
    private static final int RED = 0xFFE0614F;
    private static final int RING_RADIUS = 17;
    private static final long END_MS = 700L;
    private static final float FADE_IN_TICKS = 7f;
    /** A channel ring with no end from the server is dropped this long after it should have ended. */
    private static final int STALE_TICKS = 60;

    public static final ResourceLocation MAGNET_ICON = Proficiency.id("textures/gui/homebuilder/magnet.png");
    public static final ResourceLocation HOMEWARD_ICON = Proficiency.id("textures/gui/homebuilder/homeward.png");

    /** Wheel slots after the abilities: the magnet switch and Homeward. */
    public static final int SLOT_MAGNET = 0;
    public static final int SLOT_HOMEWARD = 1;

    private static boolean magnetOff;
    private static long homewardDay = Long.MIN_VALUE;
    private static long homewardAt = Long.MIN_VALUE;
    private static long channelStart = Long.MIN_VALUE;
    private static int channelTicks;
    private static int endReason = -1;
    private static long endedAtMs;
    private static long sharedUntil;
    private static String sharedBy = "";
    private static long sharedSeenAt = Long.MIN_VALUE;

    private HomebuilderHud() {
    }

    /** Client start: the payload handler and the HUD listener. */
    public static void install() {
        HomebuilderNet.setClient(HomebuilderHud::accept);
        MinecraftForge.EVENT_BUS.register(HomebuilderHud.class);
    }

    /** A payload from the server, on the client thread. */
    public static void accept(HomebuilderPayload payload) {
        switch (payload.kind()) {
            case HomebuilderPayload.MAGNET -> magnetOff = payload.value() != 0;
            case HomebuilderPayload.HOMEWARD_DAY -> homewardDay = payload.time();
            case HomebuilderPayload.HOMEWARD_AT -> homewardAt = payload.time();
            case HomebuilderPayload.CHANNEL -> {
                channelStart = payload.time();
                channelTicks = Math.max(1, payload.value());
                endReason = -1;
            }
            case HomebuilderPayload.CHANNEL_END -> {
                endReason = payload.value();
                endedAtMs = System.currentTimeMillis();
                channelStart = Long.MIN_VALUE;
            }
            case HomebuilderPayload.SHARED -> {
                if (payload.time() > 0 && sharedUntil <= 0) {
                    sharedSeenAt = Minecraft.getInstance().level == null ? 0L
                            : Minecraft.getInstance().level.getGameTime();
                }
                sharedUntil = payload.time();
                sharedBy = payload.name();
            }
            default -> {
            }
        }
    }

    /** Leaving a world: nothing carries over. */
    public static void reset() {
        magnetOff = false;
        homewardDay = Long.MIN_VALUE;
        homewardAt = Long.MIN_VALUE;
        channelStart = Long.MIN_VALUE;
        endReason = -1;
        sharedUntil = 0;
        sharedBy = "";
    }

    public static boolean magnetOff() {
        return magnetOff;
    }

    /** Whether Homeward can be used today, as far as this client knows. */
    public static boolean homewardReady(Player player) {
        return HomebuilderMath.homewardReady(homewardDay, HomebuilderMath.day(player.level().getDayTime()),
                homewardAt, player.level().getGameTime());
    }

    public static boolean channelling() {
        return channelStart != Long.MIN_VALUE;
    }

    /** The Homebuilder slots this player's wheel shows after the abilities, from their synced tree. */
    public static List<Integer> wheelSlots(Player player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        List<Integer> slots = new ArrayList<>(2);
        if (HomebuilderMath.rank(skills, HomebuilderMath.TIDY_HOME) > 0) {
            slots.add(SLOT_MAGNET);
        }
        if (HomebuilderMath.rank(skills, HomebuilderMath.HOMEWARD) > 0) {
            slots.add(SLOT_HOMEWARD);
        }
        return slots;
    }

    /** Draws a slot's icon, faded when it is off or used. */
    public static void drawSlotIcon(GuiGraphics graphics, int slot, int x, int y, int size, boolean dim) {
        ResourceLocation texture = slot == SLOT_MAGNET ? MAGNET_ICON : HOMEWARD_ICON;
        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        if (!blendWasOn) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        float[] colour = RenderSystem.getShaderColor();
        float r = colour[0];
        float g = colour[1];
        float b = colour[2];
        float a = colour[3];
        if (dim) {
            RenderSystem.setShaderColor(r * 0.55f, g * 0.55f, b * 0.55f, a);
        }
        graphics.blit(texture, x, y, size, size, 0f, 0f, 16, 16, 16, 16);
        RenderSystem.setShaderColor(r, g, b, a);
        if (!blendWasOn) {
            RenderSystem.disableBlend();
        }
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }
        if (minecraft.options.hideGui || !ProficiencyClientConfig.hudAbilityFx()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        float partial = event.getPartialTick();
        long gameTime = player.level().getGameTime();
        long nowMs = System.currentTimeMillis();
        sharedGlow(graphics, gameTime, partial, nowMs);
        channel(graphics, minecraft.font, gameTime, partial, nowMs);
    }

    /** A friend's House Proud: the ability edge glow in gold, at half strength, eased in and out. */
    private static void sharedGlow(GuiGraphics graphics, long gameTime, float partial, long nowMs) {
        if (sharedUntil <= 0) {
            return;
        }
        float left = sharedUntil - (gameTime + partial);
        if (left <= 0f) {
            sharedUntil = 0;
            return;
        }
        float elapsed = (gameTime + partial) - sharedSeenAt;
        float fade = Math.min(Math.min(1f, left / 20f), HudMath.clamp01(elapsed / FADE_IN_TICKS));
        AbilityFx.edgeGlow(graphics, GOLD, fade * 0.5f, nowMs);
    }

    /** Homeward: the filling ring, then the end flash. */
    private static void channel(GuiGraphics graphics, Font font, long gameTime, float partial, long nowMs) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int rgb = GOLD & 0x00FFFFFF;
        if (channelStart != Long.MIN_VALUE) {
            float elapsed = (gameTime + partial) - channelStart;
            if (elapsed > channelTicks + STALE_TICKS || elapsed < -STALE_TICKS) {
                // The server's end never came (a lost packet, a clock jump): let the ring go quietly.
                channelStart = Long.MIN_VALUE;
                return;
            }
            float progress = HudMath.clamp01(elapsed / channelTicks);
            float fade = HudMath.smooth(elapsed / FADE_IN_TICKS);
            int lit = Math.round(progress * 64);
            int track = (Math.round(60 * fade) << 24) | rgb;
            int bright = (Math.round(235 * fade) << 24) | StreakBadge.lerp(rgb, 0xFFFFFF, 0.25f + 0.3f * progress);
            HudMath.ring(graphics, cx, cy, RING_RADIUS, RING_RADIUS, track, 1, 1, 64);
            HudMath.ring(graphics, cx, cy, RING_RADIUS, RING_RADIUS, bright, 2, 1, lit);
            int seconds = (int) Math.ceil(Math.max(0f, channelTicks - elapsed) / 20f);
            label(graphics, font, Component.translatable("proficiency.homebuilder.homeward.hud", seconds).getString(),
                    cx, cy + RING_RADIUS + 6, (Math.round(255 * fade) << 24) | rgb);
            return;
        }
        if (endReason < 0) {
            return;
        }
        float t = (nowMs - endedAtMs) / (float) END_MS;
        if (t >= 1f) {
            endReason = -1;
            return;
        }
        float out = 1f - HudMath.smooth(t);
        boolean arrived = endReason == HomebuilderPayload.END_ARRIVED;
        int colour = arrived ? rgb : RED & 0x00FFFFFF;
        int alpha = Math.round(230 * out);
        if (alpha <= 4) {
            return;
        }
        // Arrival throws the ring outwards; a broken channel shivers in place as it fades.
        float radius = arrived ? RING_RADIUS + 22f * HudMath.smooth(t) : RING_RADIUS;
        HudMath.ring(graphics, cx, cy, radius, radius, (alpha << 24) | colour, 2, 1, 64);
        label(graphics, font, Component.translatable(arrived ? "proficiency.homebuilder.homeward.hud_home"
                : "proficiency.homebuilder.homeward.hud_broken").getString(), cx, cy + RING_RADIUS + 6,
                (alpha << 24) | colour);
    }

    private static void label(GuiGraphics graphics, Font font, String text, int cx, int y, int argb) {
        if ((argb >>> 24) <= 4) {
            return;
        }
        int room = Math.max(40, graphics.guiWidth() / 3);
        String shown = TextFit.clip(font, text, room);
        if (!shown.equals(text)) {
            TextFit.note("homebuilder.homeward");
        }
        graphics.drawString(font, shown, cx - font.width(shown) / 2, y, argb, true);
    }
}
