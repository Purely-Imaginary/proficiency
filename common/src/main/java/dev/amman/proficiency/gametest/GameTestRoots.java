package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.world.entity.player.Player;

/** The ability needs the tree's root as well as the level: tests that cast one buy it first. */
final class GameTestRoots {

    private GameTestRoots() {
    }

    static <P extends Player> P open(P player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        Talent root = Talents.root(skill);
        while (!skills.isFull(root)) {
            skills.addRank(root);
        }
        return player;
    }
}
