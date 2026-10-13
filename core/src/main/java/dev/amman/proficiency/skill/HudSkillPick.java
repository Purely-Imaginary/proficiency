package dev.amman.proficiency.skill;

/**
 * Which skill the HUD line shows for an empty hand. An empty hand is common (between items,
 * walking about) and a fist fight is rare, so Unarmed shows only while it is earning XP or its
 * ability runs. Otherwise the line follows how the player moves: Jumping just after a jump,
 * Running the rest of the time. Every other held item maps to its own skill as before.
 *
 * <p>Only the HUD uses this. The ability key keeps an empty hand on Unarmed.
 */
public final class HudSkillPick {

    /** How long a jump keeps Jumping on the line. */
    public static final long JUMP_HOLD_MS = 3000;
    /** How long an Unarmed XP gain keeps Unarmed on the line: the line's own show time. */
    public static final long UNARMED_HOLD_MS = 4000;

    private HudSkillPick() {
    }

    /**
     * @param held the skill the ability key reads for this moment ({@code null} for none); with an
     *        empty hand that is Swimming, Sneaking, Running or Endurance when they apply, else Unarmed
     * @param emptyHand whether the main hand is empty
     * @param sinceUnarmedGain ms since Unarmed last gained XP, {@code Long.MAX_VALUE} for never
     * @param unarmedActive whether the Unarmed ability is running
     * @param sinceJump ms since the player last jumped, {@code Long.MAX_VALUE} for never
     */
    public static Skill pick(Skill held, boolean emptyHand, long sinceUnarmedGain, boolean unarmedActive,
            long sinceJump) {
        if (!emptyHand) {
            return held;
        }
        // A real punch shows the fist, whatever the feet are doing.
        if (unarmedActive || sinceUnarmedGain < UNARMED_HOLD_MS) {
            return Skill.UNARMED;
        }
        if (held != Skill.UNARMED) {
            return held;
        }
        return sinceJump < JUMP_HOLD_MS ? Skill.JUMPING : Skill.RUNNING;
    }
}
