package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;

/**
 * One continuous number for "how far along" a skill is, in level units: {@code level + progress}
 * up to level 100, then {@code 100 + stars + progress to the next star}. Because a new star adds
 * one to the whole part, the bar's value only ever goes up when XP is earned, so the HUD's gain
 * dots and the activity sparkline see a star as a gain, the same as a level-up, and never as a drop
 * from 100.95 back to 100.02.
 *
 * <p>A skill that holds every star the cap allows, or any level 100 skill with Mastery switched
 * off, reads {@code 100 + cap}: the top of the scale, which {@link #fill} draws as a full bar.
 */
public final class BarValue {

    private BarValue() {
    }

    /** The top of the scale for the configured cap: 100 when Mastery is off. */
    public static double top() {
        return SkillMath.MAX_LEVEL + Math.max(0, Mastery.maxStars());
    }

    public static double value(PlayerSkills skills, Skill skill) {
        return value(skills.level(skill), skills.barProgress(skill), skills.stars(skill));
    }

    public static double value(int level, float barProgress, int stars) {
        if (level < SkillMath.MAX_LEVEL) {
            return level + barProgress;
        }
        int cap = Math.max(0, Mastery.maxStars());
        int held = Math.min(Math.max(0, stars), cap);
        if (held >= cap) {
            return SkillMath.MAX_LEVEL + cap;
        }
        return SkillMath.MAX_LEVEL + held + Math.min(Math.max(barProgress, 0f), 0.999f);
    }

    /** The bar's fill, 0 to 1, for an (animated) value. The top of the scale is a full bar. */
    public static double fill(double shown) {
        if (shown >= top()) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, shown - Math.floor(shown)));
    }
}
