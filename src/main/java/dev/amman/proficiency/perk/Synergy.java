package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.Skill;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A synergy between trees: a named combination that wakes up once you have invested far enough in
 * two or three different skills, and then pays out in all of them.
 *
 * <p>This is the half of the design Core Keeper does not have. A tree on its own rewards depth; a
 * synergy rewards the particular breadth that makes sense together, a miner who also smiths, a
 * sneak who also shoots. None of them are needed to finish anything. They are what makes one
 * character different from another at hour fifty, when everyone's favourite tree is full.
 *
 * @param requires          nodes that must reach at least the given rank
 * @param grandmastersNeeded additionally, how many trees must have their capstone. Zero for most
 * @param grants            numeric boosts, multiplied on top of the tree's own sum
 * @param special           a tag read where the bespoke behaviour lives, or null
 */
public record Synergy(
        String id,
        List<Need> requires,
        int grandmastersNeeded,
        List<Grant> grants,
        @Nullable String special) {

    /** One node that has to be at least this deep. */
    public record Need(Skill skill, String talentId, int minRank) {
        public Talent talent() {
            return Talents.get(skill, talentId);
        }
    }

    /**
     * @param skill the skill boosted, or null for every skill
     */
    public record Grant(@Nullable Skill skill, PerkEffect effect, double multiplier) {
        public boolean appliesTo(Skill target) {
            return skill == null || skill == target;
        }
    }

    public Component displayName() {
        return Component.translatable("proficiency.synergy." + id);
    }

    public String descriptionKey() {
        return "proficiency.synergy." + id + ".desc";
    }

    /** The one-line version for the short tooltip; absent when the description is already short. */
    public String shortKey() {
        return "proficiency.synergy." + id + ".short";
    }

    /** Whether this synergy has anything to do with a tree, either as a need or a payout. */
    public boolean involves(Skill skill) {
        return requires.stream().anyMatch(need -> need.skill() == skill)
                || grants.stream().anyMatch(grant -> grant.skill() == skill);
    }
}
