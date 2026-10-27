package dev.amman.proficiency.export;

import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One player as the dashboard export shows them: a snapshot, taken while they were online and kept
 * (marked offline) after they leave. Plain numbers only, so it can be written and read back without
 * Minecraft. Skill arrays are in {@link Skill#VALUES} order.
 */
public record PlayerRow(
        String uuid,
        String name,
        boolean online,
        /** Wall clock (ms since the epoch) of the snapshot. */
        long seenMs,
        int[] levels,
        /** Fraction 0..1 of the bar a player sees: to the next level, at level 100 to the next star. */
        float[] bar,
        int[] stars,
        int synergies,
        int expertisePoints,
        int expertiseSpent,
        int totalLevels,
        int totalStars,
        /** Skill ids, best first, at most {@link #TOP}. */
        List<String> top,
        int deathsWeek) {

    public static final int TOP = 3;

    public static PlayerRow of(UUID id, String name, boolean online, long seenMs, PlayerSkills skills,
            int deathsWeek) {
        int count = Skill.VALUES.length;
        int[] levels = new int[count];
        float[] bar = new float[count];
        int[] stars = new int[count];
        int totalLevels = 0;
        int totalStars = 0;
        for (Skill skill : Skill.VALUES) {
            int i = skill.ordinal();
            levels[i] = skills.level(skill);
            float fraction = skills.barProgress(skill);
            bar[i] = Float.isFinite(fraction) ? Math.max(0f, Math.min(1f, fraction)) : 0f;
            stars[i] = skills.stars(skill);
            totalLevels += levels[i];
            totalStars += stars[i];
        }
        return new PlayerRow(id.toString(), name, online, seenMs, levels, bar, stars,
                Synergies.active(skills).size(), skills.expertisePoints(), skills.expertiseSpent(),
                totalLevels, totalStars, top(levels, stars, bar), Math.max(0, deathsWeek));
    }

    /** The best skills: level, then stars, then the bar, then list order. A skill at level 0 with an empty bar is not "top". */
    static List<String> top(int[] levels, int[] stars, float[] bar) {
        List<Skill> ranked = new ArrayList<>();
        for (Skill skill : Skill.VALUES) {
            int i = skill.ordinal();
            if (levels[i] > 0 || bar[i] > 0f) {
                ranked.add(skill);
            }
        }
        ranked.sort((a, b) -> {
            int c = Integer.compare(levels[b.ordinal()], levels[a.ordinal()]);
            if (c == 0) {
                c = Integer.compare(stars[b.ordinal()], stars[a.ordinal()]);
            }
            if (c == 0) {
                c = Float.compare(bar[b.ordinal()], bar[a.ordinal()]);
            }
            return c != 0 ? c : Integer.compare(a.ordinal(), b.ordinal());
        });
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP, ranked.size()); i++) {
            out.add(ranked.get(i).id());
        }
        return out;
    }

    public PlayerRow asOffline() {
        return new PlayerRow(uuid, name, false, seenMs, levels, bar, stars, synergies, expertisePoints,
                expertiseSpent, totalLevels, totalStars, top, deathsWeek);
    }

    public PlayerRow withName(String newName) {
        return new PlayerRow(uuid, newName, online, seenMs, levels, bar, stars, synergies, expertisePoints,
                expertiseSpent, totalLevels, totalStars, top, deathsWeek);
    }

    public PlayerRow withDeaths(int deaths) {
        return new PlayerRow(uuid, name, online, seenMs, levels, bar, stars, synergies, expertisePoints,
                expertiseSpent, totalLevels, totalStars, top, deaths);
    }
}
