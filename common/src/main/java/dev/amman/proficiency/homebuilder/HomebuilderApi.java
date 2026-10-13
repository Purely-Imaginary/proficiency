package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.world.entity.player.Player;

/**
 * What the other Hearth lanes read from Homebuilder's tree (docs/HEARTH-CONTRACT.md, "Homebuilder
 * API"), so the effects and tales lanes and this one meet without editing each other's files. Each
 * method reads the player's synced ranks and returns a plain number; 0 when the skill is switched
 * off or the talent is not taken.
 */
public final class HomebuilderApi {

    private HomebuilderApi() {
    }

    /**
     * Warm Welcome: how much faster this player's Warmth fills, as a fraction (0.10 per rank, 0.30 at
     * full). The effects lane applies it on top of Homecoming's readiness:
     * {@code WarmthMath.withSpeedBonus(riseSeconds, HomebuilderApi.warmWelcome(player))}.
     */
    public static double warmWelcome(Player player) {
        return ProficiencyConfig.enabled(Skill.HOMEBUILDER)
                ? HomebuilderMath.warmWelcome(rank(player, HomebuilderMath.WARM_WELCOME)) : 0.0;
    }

    /**
     * Storyteller: how many more Tales this player banks, as a fraction (0.10 per rank, 0.30 at full).
     * The tales lane passes it to {@code TalesMath.banked(skill, xp, HomebuilderApi.storyteller(player), d)}.
     */
    public static double storyteller(Player player) {
        return ProficiencyConfig.enabled(Skill.HOMEBUILDER)
                ? HomebuilderMath.storyteller(rank(player, HomebuilderMath.STORYTELLER)) : 0.0;
    }

    /** Whether this player has Night Watch (siege mobs glow for them; the early horn). */
    public static boolean nightWatch(Player player) {
        return ProficiencyConfig.enabled(Skill.HOMEBUILDER) && rank(player, HomebuilderMath.NIGHT_WATCH) > 0;
    }

    private static int rank(Player player, String special) {
        return HomebuilderMath.rank(ProficiencyAttachments.of(player), special);
    }
}
