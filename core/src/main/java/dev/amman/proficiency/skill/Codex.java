package dev.amman.proficiency.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Codex: per skill, how many distinct first-time kinds a player has found, and the titles that
 * 10, 25, 50 and 100 of them earn. Pure rules (no game classes) so a test can hold them.
 *
 * <p>A title is cosmetic only. It gives no stat, no XP and nothing that softens a death. The list is
 * bounded: {@link #MILESTONES} tiers times the skills, and {@link #all()} lists every one.
 */
public final class Codex {

    /** Kinds found in one skill that award a title, ascending. The last one also tells the server. */
    public static final int[] MILESTONES = {10, 25, 50, 100};

    /** The tier that is announced to everyone. */
    public static final int ANNOUNCE_AT = 100;

    /** One title: the skill and the number of kinds that earns it. */
    public record Title(Skill skill, int kinds) {
        /** The id a command and the save use: {@code mining:25}. */
        public String id() {
            return skill.id() + ":" + kinds;
        }

        /** 0 to 3, the index into {@link #MILESTONES}. */
        public int tier() {
            return tierOf(kinds);
        }
    }

    private Codex() {
    }

    public static int tierOf(int kinds) {
        for (int i = 0; i < MILESTONES.length; i++) {
            if (MILESTONES[i] == kinds) {
                return i;
            }
        }
        return -1;
    }

    /** The distinct canonical first-time kinds this visited set holds for one skill. */
    public static int count(Collection<String> visited, Skill skill) {
        String prefix = "first:" + skill.id() + ":";
        Set<String> kinds = new LinkedHashSet<>();
        for (String key : visited) {
            if (key.startsWith(prefix) && key.length() > prefix.length()) {
                kinds.add(FirstTimeKinds.canonical(key.substring(prefix.length())));
            }
        }
        return kinds.size();
    }

    /** The milestones reached at this count, ascending. */
    public static List<Integer> reached(int count) {
        List<Integer> out = new ArrayList<>();
        for (int m : MILESTONES) {
            if (count >= m) {
                out.add(m);
            }
        }
        return out;
    }

    /** The next milestone above this count, or 0 when every one is earned. */
    public static int next(int count) {
        for (int m : MILESTONES) {
            if (count < m) {
                return m;
            }
        }
        return 0;
    }

    /** The highest title tier earned at this count, 0 when none. */
    public static int best(int count) {
        int best = 0;
        for (int m : MILESTONES) {
            if (count >= m) {
                best = m;
            }
        }
        return best;
    }

    /** Every title there is, skill by skill, lowest tier first. */
    public static List<Title> all() {
        List<Title> out = new ArrayList<>();
        for (Skill skill : Skill.VALUES) {
            for (int m : MILESTONES) {
                out.add(new Title(skill, m));
            }
        }
        return out;
    }

    /** A title from its id, or null when it is not one ({@code mining:25} yes, {@code mining:26} no). */
    public static Title parse(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.lastIndexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            return null;
        }
        Skill skill = Skill.byId(id.substring(0, colon));
        if (skill == null) {
            return null;
        }
        try {
            int kinds = Integer.parseInt(id.substring(colon + 1));
            return tierOf(kinds) < 0 ? null : new Title(skill, kinds);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether this visited set has earned the title. */
    public static boolean earned(Collection<String> visited, Title title) {
        return count(visited, title.skill()) >= title.kinds();
    }
}
