package dev.amman.proficiency.perk;

/** What happened when a player tried to put a rank into a talent node. */
public enum TalentOutcome {
    OK,
    MAXED,
    PARENT_NOT_FULL,
    LEVEL_TOO_LOW,
    NOT_ENOUGH_POINTS,
    MISSING_MATERIALS
}
