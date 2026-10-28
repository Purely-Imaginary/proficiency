package dev.amman.proficiency.jade;

import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.look.PlayerLookRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

import java.util.List;

/**
 * One Jade row: each of a player's best skills as its small icon and its level in the skill's colour,
 * side by side ({@link SkillIconElement}'s size and sampling, so it matches the other Proficiency lines).
 */
final class TopSkillsElement extends Element {

    /** Space after one skill's number, before the next icon. */
    private static final int BETWEEN = 6;

    private final List<PlayerLookRules.Pick> picks;

    TopSkillsElement(List<PlayerLookRules.Pick> picks) {
        this.picks = picks;
    }

    private static String text(PlayerLookRules.Pick pick) {
        return Integer.toString(pick.level());
    }

    @Override
    public Vec2 getSize() {
        int width = 0;
        var font = Minecraft.getInstance().font;
        for (PlayerLookRules.Pick pick : picks) {
            width += SkillIcons.SMALL + SkillIcons.GAP + font.width(text(pick)) + BETWEEN;
        }
        return new Vec2(Math.max(0, width - BETWEEN), SkillIcons.SMALL + 1);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        var font = Minecraft.getInstance().font;
        int at = Math.round(x);
        int top = Math.round(y);
        for (PlayerLookRules.Pick pick : picks) {
            SkillIcons.draw(graphics, pick.skill(), at, top, SkillIcons.SMALL);
            at += SkillIcons.SMALL + SkillIcons.GAP;
            String text = text(pick);
            graphics.drawString(font, text, at, top, LookText.colour(pick.skill()) | 0xFF000000, true);
            at += font.width(text) + BETWEEN;
        }
    }

    @Override
    public String getMessage() {
        StringBuilder out = new StringBuilder();
        for (PlayerLookRules.Pick pick : picks) {
            out.append(pick.skill().id()).append(' ').append(pick.level()).append("; ");
        }
        return out.toString();
    }
}
