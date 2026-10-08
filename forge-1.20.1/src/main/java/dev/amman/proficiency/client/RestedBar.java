package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rested XP on the client: the blue segment of a bar and the tooltip lines. The segment starts
 * where the filled part ends and is as long as the XP that will pay double, so it shows how far
 * the pool reaches. Both the HUD line and the skills panel use it.
 */
public final class RestedBar {

    private RestedBar() {
    }

    /**
     * Pixels of blue for a pool that reaches {@code reach} of a {@code width} pixel bar whose fill
     * is {@code filled} pixels. At least one pixel when there is any pool and any room, never past
     * the end of the bar.
     */
    public static int pixels(float reach, int width, int filled) {
        int room = width - Math.max(0, filled);
        if (!(reach > 0f) || room <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(room, Math.round(width * reach)));
    }

    /** "Rested: N XP", and the Rusty line after a week unused. Empty when there is nothing to say. */
    public static List<Component> tooltip(PlayerSkills skills, Skill skill) {
        List<Component> lines = new ArrayList<>(2);
        int pool = Math.round(skills.rested(skill));
        if (pool > 0) {
            lines.add(Component.translatable("proficiency.tooltip.rested", pool)
                    .withStyle(style -> style.withColor(SkillPalette.RESTED & 0x00FFFFFF)));
        }
        int days = skills.daysIdle(skill);
        if (skills.level(skill) > 0 && RestedMath.rusty(days)) {
            lines.add(Component.translatable("proficiency.tooltip.rusty", days).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
