package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A visible title for whatever you are best at.
 *
 * <p>With twenty-seven skills nobody can tell who does what, and the panel only tells you about
 * yourself. This puts your best skill in front of everyone else, in the tab list and over your
 * head.
 *
 * <p>Deliberately NOT done with scoreboard teams: FTB Teams is in this pack and owns those. The
 * name-format events are server-side and reach a vanilla client just the same.
 */
public final class RankBadges {

    /** Below this the badge is not worth showing; it is also where the signature proc unlocks. */
    private static final int MIN_LEVEL = 25;

    private RankBadges() {
    }

    @Nullable
    public static Component badgeFor(Player player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        Skill best = null;
        int bestLevel = MIN_LEVEL - 1;
        for (Skill skill : Skill.VALUES) {
            int level = skills.level(skill);
            if (level > bestLevel) {
                best = skill;
                bestLevel = level;
            }
        }
        if (best == null) {
            return null;
        }

        // Reuses the perk tier names, so "Master" plus "Woodcutting" reads as "Master Woodcutting"
        // with no extra translation keys.
        String tier = bestLevel >= 100 ? "master"
                : bestLevel >= 75 ? "expert"
                : bestLevel >= 50 ? "journeyman"
                : "apprentice";

        Component title = Component.translatable("proficiency.perk.tier." + tier,
                Component.translatable(best.translationKey()));
        int colour = bestLevel >= 100
                ? 0xF2D98A
                : accent(best);
        return Component.translatable("proficiency.badge", title)
                .withStyle(style -> style.withColor(colour));
    }

    private static int accent(Skill skill) {
        return switch (skill.category()) {
            case COMBAT -> 0xD4695A;
            case GATHERING -> 0x7FAE63;
            case MOVEMENT -> 0x5F9EC4;
            case CRAFTING -> 0xD2A249;
            case MASTERY -> 0x9B7FC4;
            case EXPEDITION -> 0xC98A4B;
            case CONSTRUCTION -> 0x6FB0A6;
            case SOCIAL -> 0xD98AB3;
            case SURVIVAL -> 0x6F7FC9;
        };
    }

    public static Component decorate(Player player, Component original) {
        Component badge = badgeFor(player);
        if (badge == null) {
            return original;
        }
        return Component.empty().append(badge).append(original.copy()
                .withStyle(ChatFormatting.RESET));
    }
}
