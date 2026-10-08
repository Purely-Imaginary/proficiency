package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mastery stars on the client: the small gold star glyph (U+E001, a 7 by 8 bitmap in the same
 * custom font as the streak chevrons, so no unifont is involved) and the hover tooltip that says
 * stars x/5 and how far the next one is.
 */
public final class MasteryStars {

    public static final char GLYPH = '';
    /** Width of one star in GUI pixels, the 7 of the bitmap plus the 1 of spacing. */
    public static final int WIDTH = 8;
    public static final int GOLD = 0xFFC83A;

    private MasteryStars() {
    }

    /** Stars to draw next to a skill's name: earned ones only, and only at level 100. */
    public static int shown(PlayerSkills skills, Skill skill) {
        return skills.level(skill) >= SkillMath.MAX_LEVEL ? Mastery.shownStars(skills.stars(skill)) : 0;
    }

    public static int width(int stars) {
        return stars * WIDTH;
    }

    /** {@code stars} glyphs in one string, for a single drawString. */
    public static String text(int stars) {
        return String.valueOf(GLYPH).repeat(Math.max(0, Math.min(Mastery.MAX_STARS, stars)));
    }

    /** Draws the earned stars in gold at {@code x}, {@code y} (the text baseline top). */
    public static void draw(GuiGraphics graphics, Font font, int x, int y, int stars, int alpha) {
        if (stars > 0 && alpha > 8) {
            graphics.drawString(font, text(stars), x, y, (alpha << 24) | GOLD, true);
        }
    }

    /**
     * Stars x/5, then the bar toward the next star, or a line saying every star is earned. Empty
     * when the skill is below 100 or Mastery is switched off.
     */
    public static List<Component> tooltip(PlayerSkills skills, Skill skill) {
        List<Component> lines = new ArrayList<>();
        if (skills.level(skill) < SkillMath.MAX_LEVEL || (Mastery.maxStars() <= 0 && skills.stars(skill) <= 0)) {
            return lines;
        }
        // A cap lowered after stars were earned must not read 5/3.
        int stars = Mastery.shownStars(skills.stars(skill));
        lines.add(Component.translatable("proficiency.tooltip.stars", stars, Mastery.lastStar())
                .withStyle(ChatFormatting.GOLD));
        if (stars >= Mastery.lastStar()) {
            lines.add(Component.translatable("proficiency.tooltip.stars.full").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (Mastery.maxStars() > 0) {
            int cost = Math.round(Mastery.starCost(stars + 1));
            int done = Math.min(cost, (int) Math.floor(skills.overflow(skill)));
            lines.add(Component.translatable("proficiency.tooltip.stars.next", done, cost,
                    (int) Math.floor(skills.starProgress(skill) * 100)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
