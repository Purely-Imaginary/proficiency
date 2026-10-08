package dev.amman.proficiency.xp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-text answers to "which rule paid this and why", for the op command and the tests.
 * ASCII only, one fact per line.
 */
public final class XpExplain {

    private XpExplain() {
    }

    /** The lines for one domain: the winner and its numbers first, then every rule that lost or was skipped. */
    public static List<String> lines(XpTable table, XpDomain domain, XpSubject subject) {
        List<String> out = new ArrayList<>();
        List<XpTable.Explained> explained = table.explain(domain, subject);
        out.add("[" + domain.key() + "] " + subject.id() + " (" + subject.kind() + ")");
        if (explained.isEmpty()) {
            out.add("  no rule applies" + fallbackNote(domain));
            return out;
        }
        for (XpTable.Explained entry : explained) {
            XpRule rule = entry.rule();
            String head = entry.winner() ? "  WINS  " : "  other ";
            out.add(head + rule.matchText() + "  priority " + rule.priority + "  from " + rule.sourceFile);
            if (entry.winner()) {
                out.add("        " + pays(rule, subject));
            }
            out.add("        " + entry.why());
        }
        return out;
    }

    private static String fallbackNote(XpDomain domain) {
        return switch (domain) {
            case STRUCTURE -> ", so the config structureXp applies";
            case BIOME, DIMENSION -> ", so the built-in amount applies";
            case KILL_BONUS -> ", so the config kill bonus formula applies";
            case BOSS -> ", so only the config bossHealth backstop decides";
            case FIRST_TIME -> ", so the multiplier is x1";
            default -> ", so it pays nothing";
        };
    }

    /** What the winning rule pays this subject. */
    public static String pays(XpRule rule, XpSubject subject) {
        StringBuilder sb = new StringBuilder();
        if (rule.domain == XpDomain.BOSS) {
            return Boolean.TRUE.equals(rule.boss) ? "counts as a boss" : "does not count as a boss";
        }
        if (rule.domain == XpDomain.FIRST_TIME) {
            return "first-time bonus multiplier x" + trim(rule.multiplier == null ? 1.0 : rule.multiplier);
        }
        if (rule.paysNothing) {
            return "pays nothing (skill none)";
        }
        if (rule.xp == null) {
            sb.append(rule.domain == XpDomain.STRUCTURE
                    ? "pays the config amount (structureXp, or grandStructureXp when grand)"
                    : "pays the default amount");
        } else {
            double value = rule.xp.eval(subject.hardness(), subject.maxHealth());
            sb.append("pays ");
            if (rule.skill != null) {
                sb.append(rule.skill.id()).append(' ');
            }
            sb.append(trim(value)).append(" XP");
            String formula = formula(rule.xp, subject);
            if (!formula.isEmpty()) {
                sb.append(" (").append(formula).append(')');
            }
        }
        if (rule.also != null) {
            sb.append(", and ").append(rule.also.skill().id()).append(' ')
                    .append(trim(rule.also.xp().eval())).append(" XP");
        }
        if (rule.tool != null) {
            sb.append(", needs a ").append(rule.tool);
        }
        if (!rule.flags.isEmpty()) {
            sb.append(", flags ").append(String.join(" ", new java.util.TreeSet<>(rule.flags)));
        }
        return sb.toString();
    }

    private static String formula(XpSpec spec, XpSubject subject) {
        if (spec.perHardness() == 0 && spec.perHealth() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(trim(spec.base()));
        if (spec.perHardness() != 0) {
            sb.append(" + ").append(trim(spec.perHardness())).append(" x hardness ")
                    .append(trim(subject.hardness()));
        }
        if (spec.perHealth() != 0) {
            sb.append(" + ").append(trim(spec.perHealth())).append(" x max health ")
                    .append(trim(subject.maxHealth()));
        }
        if (Double.isFinite(spec.max())) {
            sb.append(", at most ").append(trim(spec.max()));
        }
        return sb.toString();
    }

    private static String trim(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
