package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * One node in a skill's talent tree, Core Keeper style: it has ranks, each rank costs points from
 * that skill's own levels, and a node only opens once every node it hangs from is full.
 *
 * <p>Materials are charged once, on the rank that fills the node, and remembered. A respec refunds
 * the points but not the materials, and re-filling a node you already paid for costs points only.
 * Dragging 16 rainbow eucalyptus back from a distant biome is the expedition; making someone do it
 * twice because they changed their mind is a chore.
 *
 * @param col     grid column, 0..4. Branches sit on 0, 2 and 4; bridges between them on 1 and 3
 * @param parents ids in the same tree that must all be full before this node opens
 * @param perRank effect added per rank, e.g. {@code BONUS -> 0.05} is +5% per rank
 * @param special a tag read directly by the code that implements a bespoke behaviour, or null
 */
public record Talent(
        String id,
        Skill skill,
        Kind kind,
        int row,
        int col,
        int maxRank,
        int requiredLevel,
        int costPerRank,
        List<String> parents,
        Map<PerkEffect, Double> perRank,
        List<Requirement> materials,
        @Nullable String special) {

    public enum Kind {
        /** The trunk: every tree starts here. */
        ROOT,
        /** An ordinary ranked node on one of the three branches. */
        NODE,
        /** Hangs from two branches at once and only opens when both are full. */
        BRIDGE,
        /** A single-rank node that costs materials and changes how the skill plays. */
        KEYSTONE,
        /** The tip of the tree. Needs all three branches finished. */
        CAPSTONE
    }

    /** Stable key used in save data and in commands, e.g. {@code woodcutting/precision}. */
    public String key() {
        return skill.id() + "/" + id;
    }

    public int totalCost() {
        return maxRank * costPerRank;
    }

    /** Every skill's nodes have their own names. The lang key of this node's name. */
    public String nameKey() {
        return "proficiency.talent." + skill.id() + "." + id;
    }

    public String descriptionKey() {
        return "proficiency.talent." + skill.id() + "." + id + ".desc";
    }

    /**
     * The one-line version for the short tooltip. Every tree's root says the same thing, so they
     * share one string; a node whose description is already short has none and is shown whole.
     */
    public String shortKey() {
        return kind == Kind.ROOT ? "proficiency.talent.root.short"
                : "proficiency.talent." + skill.id() + "." + id + ".short";
    }
}
