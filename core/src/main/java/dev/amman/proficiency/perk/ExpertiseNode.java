package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One node of the Expertise tree, the cross-skill tree paid for with Expertise points (see
 * {@link Expertise}). Shaped like a {@link Talent}: ranks, a cost per rank, parents that must be
 * full first and a grid place. Its effects multiply the same {@link PerkEffect} numbers the talent
 * trees do, for every skill or for the skills of some categories.
 *
 * @param categories the categories whose skills the effects reach; empty means every skill
 * @param perRank    effect added per rank, e.g. {@code BONUS -> 0.02} is +2% per rank
 */
public record ExpertiseNode(
        String id,
        Talent.Kind kind,
        int row,
        int col,
        int maxRank,
        int costPerRank,
        List<String> parents,
        Map<PerkEffect, Double> perRank,
        Set<SkillCategory> categories) {

    public int totalCost() {
        return maxRank * costPerRank;
    }

    /** Whether this node's effects reach {@code skill}. */
    public boolean appliesTo(Skill skill) {
        return categories.isEmpty() || categories.contains(skill.category());
    }

    public String nameKey() {
        return "proficiency.expertise.node." + id;
    }

    public String descriptionKey() {
        return "proficiency.expertise.node." + id + ".desc";
    }
}
