package dev.amman.proficiency.client;

import dev.amman.proficiency.net.ProcFxPayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The name of a signature moment ("Motherlode!", "Timber!") just above the crosshair, where your
 * eyes already are, in the skill's colour. It pops in, holds, then fades and drifts up
 * ({@link CalloutFade}). Fed by the proc particle packet, and only for your own procs: someone
 * else's Motherlode is theirs to read. A frenzy procs on every action, so a repeat of the line
 * that is already up keeps it up instead of popping it again.
 */
public final class ProcCallout {

    /** Gap between the crosshair's centre and the text's bottom edge, in GUI pixels. */
    private static final int ABOVE_CROSSHAIR = 12;

    private static Skill skill;
    private static Component text = Component.empty();
    private static long startedAt = Long.MIN_VALUE;

    private ProcCallout() {
    }

    public static void accept(ProcFxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || payload.entityId() != minecraft.player.getId()) {
            return;
        }
        Skill landed = Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)];
        long now = System.currentTimeMillis();
        long elapsed = now - startedAt;
        if (landed == skill && CalloutFade.active(elapsed)) {
            // Already showing: hold it again, without a second pop.
            startedAt = now - Math.min(elapsed, CalloutFade.POP_MS);
            return;
        }
        skill = landed;
        text = Component.translatable(landed.procKey()).withStyle(ChatFormatting.BOLD);
        startedAt = now;
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, long now) {
        if (skill == null) {
            return;
        }
        long elapsed = now - startedAt;
        if (!CalloutFade.active(elapsed)) {
            skill = null;
            return;
        }
        Font font = minecraft.font;
        float scale = CalloutFade.scale(elapsed);
        int color = CalloutFade.withAlpha(SkillPalette.procAccent(skill), CalloutFade.alpha(elapsed));
        float cx = graphics.guiWidth() / 2f;
        float bottom = graphics.guiHeight() / 2f - ABOVE_CROSSHAIR - CalloutFade.rise(elapsed);
        int width = font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, bottom, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -width / 2, -font.lineHeight, color, true);
        graphics.pose().popPose();
    }

    public static void reset() {
        skill = null;
        startedAt = Long.MIN_VALUE;
    }
}
