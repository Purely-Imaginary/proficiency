package dev.amman.proficiency;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillMath#totalXpFor} and {@link SkillMath#xpToNext} are two separate implementations of
 * the same underlying curve: one sums level costs, the other returns a single level's cost. The
 * panel shows totals, the docs derive the "actions for that last level" column from the
 * difference between adjacent totals, and nothing pins down that the two agree at every level.
 * {@link SkillMath#progress} is built from {@code xpToNext} the same way, so its behaviour right
 * at and past the boundary belongs alongside these.
 */
class SkillMathXpConsistencyTest {

    @Test
    void totalXpDeltaEqualsXpToNextAtEveryLevel() {
        // totalXpFor sums up to 100 float terms, so the two sides can drift by float rounding
        // alone; the tolerance here is sized generously above that noise floor (a couple hundred
        // additions of values up to ~316 bounds the accumulated float error well under 1.0) so it
        // still catches a real divergence, such as an off-by-one in the summed range.
        for (int level = 0; level < SkillMath.MAX_LEVEL; level++) {
            float delta = SkillMath.totalXpFor(level + 1) - SkillMath.totalXpFor(level);
            float direct = SkillMath.xpToNext(level);
            assertEquals(direct, delta, 1.0f,
                    "totalXpFor(" + (level + 1) + ") - totalXpFor(" + level + ") should equal xpToNext(" + level + ")");
        }
    }

    @Test
    void totalXpForZeroIsZero() {
        assertEquals(0f, SkillMath.totalXpFor(0));
    }

    @Test
    void totalXpForClimbsStrictlyWithLevel() {
        float previous = SkillMath.totalXpFor(0);
        for (int level = 1; level <= SkillMath.MAX_LEVEL; level++) {
            float total = SkillMath.totalXpFor(level);
            assertTrue(total > previous, "totalXpFor should strictly climb at level " + level);
            previous = total;
        }
    }

    @Test
    void progressIsExactlyOneWhenXpEqualsTheRequirement() {
        for (Skill skill : Skill.VALUES) {
            for (int level : new int[] {0, 1, 24, 25, 50, 99}) {
                float need = SkillMath.xpToNext(level);
                assertEquals(1.0f, SkillMath.progress(level, need), 1e-6f,
                        skill.id() + " at level " + level + " with xp exactly at the requirement");
            }
        }
    }

    @Test
    void progressClampsAtOneForXpBeyondWhatTheLevelNeeds() {
        for (int level : new int[] {0, 1, 25, 50, 99}) {
            float need = SkillMath.xpToNext(level);
            assertEquals(1.0f, SkillMath.progress(level, need * 2f), 1e-6f,
                    "double the requirement at level " + level + " must still clamp to 1.0, not overshoot");
            assertEquals(1.0f, SkillMath.progress(level, Float.MAX_VALUE), 1e-6f,
                    "an absurd xp value at level " + level + " must still clamp to 1.0");
        }
    }

    @Test
    void progressJustBelowTheRequirementIsJustBelowOne() {
        for (int level : new int[] {0, 1, 25, 50, 99}) {
            float need = SkillMath.xpToNext(level);
            float justShort = need - Math.max(1e-3f, need * 1e-4f);
            float progress = SkillMath.progress(level, justShort);
            assertTrue(progress < 1.0f, "level " + level + ": xp just short of the requirement should read below 1.0, was " + progress);
            assertTrue(progress > 0.9f, "level " + level + ": xp just short of the requirement should still read close to 1.0, was " + progress);
        }
    }
}
