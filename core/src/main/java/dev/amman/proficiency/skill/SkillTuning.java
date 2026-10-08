package dev.amman.proficiency.skill;

/**
 * The config numbers the pure skill maths reads. The server config implements this and installs
 * itself when its class loads; until then, and in unit tests, {@link #DEFAULTS} answers with the
 * same values the config falls back to while it is not loaded.
 */
public interface SkillTuning {

    double curveFloor();

    double curveBase();

    double curveExponent();

    boolean enabled(Skill skill);

    double maxBonus(Skill skill);

    boolean procsEnabled();

    int procUnlockLevel();

    double procFloor();

    double procChance(Skill skill);

    /** Mastery stars: star 1 costs this times the XP of level 99 to 100 (see {@link Mastery}). */
    default double masteryStarFactor() {
        return 1.0;
    }

    /** Mastery stars a skill can earn, 0 to 5; 0 turns the overflow bar off. */
    default int masteryMaxStars() {
        return Mastery.MAX_STARS;
    }

    /** The fallbacks of the server config, number for number. */
    SkillTuning DEFAULTS = new SkillTuning() {
        @Override
        public double curveFloor() {
            return 8.0;
        }

        @Override
        public double curveBase() {
            return 2.0;
        }

        @Override
        public double curveExponent() {
            return 1.35;
        }

        @Override
        public boolean enabled(Skill skill) {
            return true;
        }

        @Override
        public double maxBonus(Skill skill) {
            return skill.defaultMaxBonus();
        }

        @Override
        public boolean procsEnabled() {
            return true;
        }

        @Override
        public int procUnlockLevel() {
            return 25;
        }

        @Override
        public double procFloor() {
            return 0.05;
        }

        @Override
        public double procChance(Skill skill) {
            return skill.defaultProcChance();
        }
    };

    static SkillTuning current() {
        return Holder.current;
    }

    static void install(SkillTuning tuning) {
        Holder.current = tuning;
    }

    /** Where the installed tuning lives; interfaces cannot hold a mutable field. */
    final class Holder {
        private static volatile SkillTuning current = DEFAULTS;

        private Holder() {
        }
    }
}
