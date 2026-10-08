package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Skill;
import org.jetbrains.annotations.Nullable;

/**
 * The level-up moment on the skill HUD, without any Minecraft rendering in it: when it started,
 * how far along it is, and whether it is the bigger gold one (every tenth level). {@link SkillHud}
 * draws it; the unit tests drive it with a fake clock.
 *
 * <p>Two things start it. For the skill the HUD is showing, the HUD sees the displayed level turn
 * over (so the number rolls exactly when the bar wraps). For any other skill the server's level-up
 * packet {@link #queue queues} it and the HUD borrows the line for {@link #lineMs} to show it.
 */
public final class LevelUpFx {

    /** The ring, flash and number roll of a normal level-up. */
    public static final long NORMAL_MS = 600;
    /** The longer gold one, for every tenth level. */
    public static final long GOLD_MS = 1400;
    /** How long the number takes to turn over. */
    public static final long ROLL_MS = 380;
    /** How long a borrowed line stays up: the effect plus a moment to read the new number. */
    public static final long BORROW_NORMAL_MS = 2200;
    public static final long BORROW_GOLD_MS = 3000;

    /** A Mastery star's moment: a lighter ring and a few star sparks, no number roll. */
    public static final long STAR_MS = 900;
    /** The fifth star, the grand master one, runs as long as a gold level-up. */
    public static final long GRAND_MS = GOLD_MS;
    public static final long BORROW_STAR_MS = 2000;
    public static final long BORROW_GRAND_MS = 3000;

    private static Skill skill;
    private static int oldLevel;
    private static int newLevel;
    /** The star count this effect celebrates, or 0 when it is an ordinary level-up. */
    private static int star;
    private static long start = Long.MIN_VALUE / 2;
    private static boolean borrowed;

    private static Skill queuedSkill;
    private static int queuedLevel;
    private static int queuedStars;

    private LevelUpFx() {
    }

    /** Every tenth level is the big one. */
    public static boolean isGold(int level) {
        return level > 0 && level % 10 == 0;
    }

    public static long durationMs(int level) {
        return isGold(level) ? GOLD_MS : NORMAL_MS;
    }

    /** From the level-up packet. Client thread. */
    public static void queue(Skill skill, int level) {
        queuedSkill = skill;
        queuedLevel = level;
        queuedStars = 0;
    }

    /** From a level-up packet that carries a Mastery star. Client thread. */
    public static void queueStar(Skill skill, int stars) {
        queuedSkill = skill;
        queuedLevel = dev.amman.proficiency.skill.SkillMath.MAX_LEVEL;
        queuedStars = stars;
    }

    /** The star count of the queued packet, 0 for an ordinary level-up. */
    public static int queuedStars() {
        return queuedStars;
    }

    /** Takes the queued packet, if any, once. */
    @Nullable
    public static Skill takeQueuedSkill() {
        Skill taken = queuedSkill;
        queuedSkill = null;
        return taken;
    }

    public static int queuedLevel() {
        return queuedLevel;
    }

    /** Starts the effect. {@code borrowed} means the HUD is showing this skill only for the moment. */
    public static void start(Skill skill, int oldLevel, int newLevel, long now, boolean borrowed) {
        LevelUpFx.skill = skill;
        LevelUpFx.oldLevel = oldLevel;
        LevelUpFx.newLevel = newLevel;
        LevelUpFx.start = now;
        LevelUpFx.borrowed = borrowed;
        LevelUpFx.star = 0;
    }

    /** Starts the lighter Mastery star moment for {@code stars} (1 to 5) at level 100. */
    public static void startStar(Skill skill, int stars, long now, boolean borrowed) {
        start(skill, dev.amman.proficiency.skill.SkillMath.MAX_LEVEL,
                dev.amman.proficiency.skill.SkillMath.MAX_LEVEL, now, borrowed);
        LevelUpFx.star = stars;
    }

    /** The star this effect celebrates, 0 for a level-up. */
    public static int star() {
        return star;
    }

    private static long effectMs() {
        if (star > 0) {
            return star >= dev.amman.proficiency.skill.Mastery.lastStar() ? GRAND_MS : STAR_MS;
        }
        return durationMs(newLevel);
    }

    /** Forget everything (leaving the world). */
    public static void reset() {
        skill = null;
        queuedSkill = null;
        queuedStars = 0;
        star = 0;
        start = Long.MIN_VALUE / 2;
    }

    @Nullable
    public static Skill skill() {
        return skill;
    }

    public static int oldLevel() {
        return oldLevel;
    }

    public static int newLevel() {
        return newLevel;
    }

    /** The big gold level-up; a star is never one, though its level is 100. */
    public static boolean gold() {
        return star == 0 && isGold(newLevel);
    }

    public static boolean borrowed() {
        return borrowed;
    }

    public static long startedAt() {
        return start;
    }

    /** 0 at the start to 1 at the end of the effect; above 1 once it is over. */
    public static float t(long now) {
        return (now - start) / (float) effectMs();
    }

    /** True while the ring, flash and roll are still going. */
    public static boolean active(long now) {
        return skill != null && now >= start && now - start < effectMs();
    }

    /** True while a borrowed line should stay up. */
    public static boolean lineUp(long now) {
        return borrowed && skill != null && now >= start && now - start < lineMs();
    }

    public static long lineMs() {
        if (star > 0) {
            return star >= dev.amman.proficiency.skill.Mastery.lastStar() ? BORROW_GRAND_MS : BORROW_STAR_MS;
        }
        return isGold(newLevel) ? BORROW_GOLD_MS : BORROW_NORMAL_MS;
    }

    /** How far the number has rolled, 0 to 1, eased; 1 once it has landed. */
    public static float roll(long now) {
        if (star > 0) {
            return 1f;
        }
        float t = (now - start) / (float) ROLL_MS;
        return (float) XpGainDots.ease(Math.max(0f, Math.min(1f, t)));
    }

    /** Brightness of the bar flash, 1 at the start falling to 0 (a squared fall, so it snaps). */
    public static float flash(long now) {
        float t = Math.max(0f, Math.min(1f, t(now)));
        float left = 1f - t;
        // A star is the quieter cousin: the same fall at about half the brightness.
        return star > 0 ? left * left * 0.5f : left * left;
    }
}
