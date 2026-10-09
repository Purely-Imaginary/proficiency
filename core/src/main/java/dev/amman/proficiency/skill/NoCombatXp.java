package dev.amman.proficiency.skill;

/**
 * Entities that pay no combat XP because hitting them is not fighting: target dummies and training
 * dummies, which take any number of hits and put the health back. The main rule is the entity tag
 * {@code proficiency:no_combat_xp}, which a datapack can extend. This is the safety net for a
 * dummy from a mod the tag does not name yet.
 */
public final class NoCombatXp {

    private NoCombatXp() {
    }

    /** Whether an entity type's path (the part after the colon) names a dummy, e.g. {@code target_dummy}. */
    public static boolean looksLikeDummy(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).contains("dummy");
    }
}
