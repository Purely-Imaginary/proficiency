package dev.amman.proficiency.client;

import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of its own recent-XP lists, one per skill, drawn under the synergies in that
 * skill's tree screen. The server sends ages; this turns them into local clock times on arrival,
 * so "5s ago" keeps counting between packets without the server having to send anything.
 */
public final class ClientXpLog {

    /**
     * One line, with the local clock times of its oldest and newest gain.
     *
     * @param source  a translation key, or "" for none
     * @param base    the XP asked for before multipliers, summed over the merged gains
     * @param factors the newest gain's multipliers, XpFactors.encode form, "" for none
     */
    public record Line(Skill skill, String source, float amount, float base, int count, long first,
            long at, String factors) {
    }

    private static volatile Map<Skill, List<Line>> lines = Map.of();

    private ClientXpLog() {
    }

    /** Replaces the lines of every skill the packet names; other skills keep theirs. */
    public static void accept(XpLogPayload payload) {
        long now = System.currentTimeMillis();
        Map<Skill, List<Line>> next = new EnumMap<>(Skill.class);
        next.putAll(lines);
        for (int ordinal : payload.skills()) {
            if (ordinal >= 0 && ordinal < Skill.VALUES.length) {
                next.remove(Skill.VALUES[ordinal]);
            }
        }
        Map<Skill, List<Line>> fresh = new EnumMap<>(Skill.class);
        for (XpLogPayload.Line line : payload.lines()) {
            if (line.skill() < 0 || line.skill() >= Skill.VALUES.length
                    || !payload.skills().contains(line.skill())) {
                continue;
            }
            Skill skill = Skill.VALUES[line.skill()];
            fresh.computeIfAbsent(skill, s -> new ArrayList<>()).add(new Line(skill, line.source(),
                    line.amount(), line.base(), Math.max(1, line.count()),
                    now - line.firstAgeMillis(), now - line.ageMillis(), line.factors()));
        }
        fresh.forEach((skill, list) -> next.put(skill, List.copyOf(list)));
        lines = Map.copyOf(next);
    }

    /** One skill's lines, newest first. Empty until that skill's first XP of the session. */
    public static List<Line> lines(Skill skill) {
        return lines.getOrDefault(skill, List.of());
    }

    /** A new world or server starts a new session, and its log starts empty. */
    public static void clear() {
        lines = Map.of();
    }
}
