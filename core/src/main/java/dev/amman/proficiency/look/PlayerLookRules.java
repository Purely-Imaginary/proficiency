package dev.amman.proficiency.look;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SocialMath;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Jade tooltip of another player says (1.36.0): their three best skills and what the two of you
 * gain together right now. Pure rules, no Minecraft: the server collects the levels, the distance and the
 * Social talents and this class decides which skills are shown and which gains apply at which distance.
 *
 * <p>Distances (blocks), the same ones the XP code uses:
 * <ul>
 *   <li>Company and mentor bonus: the other player is within the LOOKING player's company radius
 *       ({@code company.radius} plus Reach rank), as {@code CompanyBonus.scan} counts them.</li>
 *   <li>Teaching (rested XP): the teacher is within the TEACHER's own company radius plus Wide Classroom
 *       ({@code RestedMath.teachRadius}).</li>
 *   <li>The shared Hearth has no distance of its own: both have the same Hearth as their home.</li>
 * </ul>
 */
public final class PlayerLookRules {

    /** How many skills the short line shows. */
    public static final int TOP = 3;
    /** How many skills a mentor line lists. */
    public static final int LIST_MAX = 5;

    /** Wire bits of {@link Gains#flags()}. */
    public static final int IN_COMPANY = 1;
    public static final int MENTOR = 2;
    public static final int CAN_TEACH_ME = 4;
    public static final int I_CAN_TEACH = 8;
    public static final int SAME_HEARTH = 16;

    private PlayerLookRules() {
    }

    /** One skill and a level. */
    public record Pick(Skill skill, int level) {
    }

    /**
     * The best {@code n} skills by level, highest first; a tie keeps the skills' own order (the order of
     * the Skills panel), so the same player always shows the same three. A skill at level 0 is never shown.
     *
     * @param levels the level of each skill by ordinal (shorter arrays count as 0 beyond their end)
     */
    public static List<Pick> top(int[] levels, int n) {
        List<Pick> all = new ArrayList<>();
        for (Skill skill : Skill.VALUES) {
            int level = skill.ordinal() < levels.length ? levels[skill.ordinal()] : 0;
            if (level > 0) {
                all.add(new Pick(skill, level));
            }
        }
        // A stable sort on the level alone: ties stay in Skill order.
        all.sort((a, b) -> Integer.compare(b.level(), a.level()));
        return all.size() > n ? new ArrayList<>(all.subList(0, Math.max(0, n))) : all;
    }

    /**
     * One side of the pair.
     *
     * @param levels        level of each skill by ordinal
     * @param companyRadius company radius, talents included
     * @param teachRadius   how far this player teaches (company radius plus Wide Classroom)
     * @param gap           the level lead another player needs to count as this one's mentor
     * @param talents       the Social talents
     * @param teachRank     Teaching (the talent that lifts people around a better Social player), 0 for none
     * @param socialLevel   Social level, for the Teaching talent
     */
    public record Side(int[] levels, double companyRadius, double teachRadius, int gap,
            SocialMath.Talents talents, int teachRank, int socialLevel) {
    }

    /**
     * What the pair gains together, for the looking player.
     *
     * @param flags         {@code IN_COMPANY}, {@code MENTOR}, {@code CAN_TEACH_ME}, {@code I_CAN_TEACH}, {@code SAME_HEARTH}
     * @param companyExtra  the XP fraction company adds for you with them (0.15 = +15%), 0 out of range
     * @param companyRadius your company radius, the distance the bonus needs
     * @param mentors       skills they lead you in by your gap or more, biggest lead first
     * @param students      skills you lead them in by their gap or more, biggest lead first
     */
    public record Gains(int flags, double companyExtra, double companyRadius, List<Skill> mentors,
            List<Skill> students) {
        public boolean has(int bit) {
            return (flags & bit) != 0;
        }

        /** Nothing to say: out of range, no mentoring either way and no shared Hearth. */
        public boolean isEmpty() {
            return flags == 0;
        }
    }

    /**
     * @param distance      blocks between the two
     * @param camaraderie   {@code company.camaraderieBonus}
     * @param mentorBonus   {@code company.mentorBonus}
     * @param sameHearth    both have the same Hearth as their home
     * @param teachingOn    rested XP and teaching are switched on ({@code rested.capFactor} above 0)
     */
    public static Gains gains(double distance, Side me, Side them, double camaraderie, double mentorBonus,
            double socialPassive, boolean sameHearth, boolean teachingOn) {
        int flags = 0;
        double extra = 0.0;
        List<Skill> mentors = leads(them.levels(), me.levels(), me.gap());
        List<Skill> students = leads(me.levels(), them.levels(), them.gap());
        boolean inCompany = distance <= me.companyRadius();
        if (inCompany) {
            flags |= IN_COMPANY;
            boolean mentor = !mentors.isEmpty();
            double teaching = them.teachRank() > 0 && them.socialLevel() > me.socialLevel()
                    ? SocialMath.teaching(them.teachRank()) : 0.0;
            // One other player: the crowd bonus (3 others at most) never applies to a pair.
            extra = SocialMath.extra(new SocialMath.Company(1, mentor, teaching), camaraderie, mentorBonus,
                    me.talents(), socialPassive);
            if (mentor) {
                flags |= MENTOR;
            }
        }
        if (teachingOn) {
            if (!mentors.isEmpty() && distance <= them.teachRadius()) {
                flags |= CAN_TEACH_ME;
            }
            if (!students.isEmpty() && distance <= me.teachRadius()) {
                flags |= I_CAN_TEACH;
            }
        }
        if (sameHearth) {
            flags |= SAME_HEARTH;
        }
        return new Gains(flags, extra, me.companyRadius(), cap(mentors), cap(students));
    }

    private static List<Skill> cap(List<Skill> list) {
        return list.size() > LIST_MAX ? new ArrayList<>(list.subList(0, LIST_MAX)) : list;
    }

    /** Skills where {@code a} is at least {@code gap} levels above {@code b}, biggest lead first. */
    static List<Skill> leads(int[] a, int[] b, int gap) {
        List<Skill> out = new ArrayList<>();
        int[] lead = new int[Skill.VALUES.length];
        for (Skill skill : Skill.VALUES) {
            int i = skill.ordinal();
            int mine = i < a.length ? a[i] : 0;
            int theirs = i < b.length ? b[i] : 0;
            if (mine > 0 && mine >= theirs + Math.max(0, gap)) {
                out.add(skill);
                lead[i] = mine - theirs;
            }
        }
        out.sort((x, y) -> Integer.compare(lead[y.ordinal()], lead[x.ordinal()]));
        return out;
    }

    /** "+15%" from a fraction: whole percent, as the company line prints it. */
    public static int percent(double fraction) {
        return (int) Math.round(fraction * 100.0);
    }
}
