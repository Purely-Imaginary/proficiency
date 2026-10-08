package dev.amman.proficiency.xp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every loaded rule, indexed by domain. Immutable once built, so the server thread can swap it for
 * a new one on /reload while another thread still reads the old one.
 *
 * <p>Which rule wins when several match one subject: the highest {@code priority}; then the most
 * specific match (an exact id beats a tag, a tag beats a namespace wildcard, a wildcard beats
 * {@code *}); then the rule loaded last. A rule never matches a subject it excludes, or one of the
 * wrong {@code kind}, or one under its {@code min_health}.
 */
public final class XpTable {

    public static final XpTable EMPTY = new XpTable(new EnumMap<>(XpDomain.class), 0);

    /** Best first: priority, then specificity, then load order. */
    private static final Comparator<XpRule> BEST_FIRST = Comparator
            .comparingInt((XpRule r) -> r.priority)
            .thenComparingInt(r -> r.matcher.rank())
            .thenComparingInt(r -> r.order)
            .reversed();

    private static final class Index {
        final Map<String, List<XpRule>> byId = new HashMap<>();
        final Map<String, List<XpRule>> byNamespace = new HashMap<>();
        final List<XpRule> tags = new ArrayList<>();
        final List<XpRule> any = new ArrayList<>();
        final List<XpRule> all = new ArrayList<>();
    }

    private final Map<XpDomain, Index> indexes = new EnumMap<>(XpDomain.class);
    private final int files;

    XpTable(Map<XpDomain, List<XpRule>> rules, int files) {
        this.files = files;
        for (Map.Entry<XpDomain, List<XpRule>> entry : rules.entrySet()) {
            Index index = new Index();
            for (XpRule rule : entry.getValue()) {
                index.all.add(rule);
                switch (rule.matcher) {
                    case ID -> index.byId.computeIfAbsent(rule.key, k -> new ArrayList<>()).add(rule);
                    case NAMESPACE -> index.byNamespace.computeIfAbsent(rule.key, k -> new ArrayList<>()).add(rule);
                    case TAG -> index.tags.add(rule);
                    case ANY -> index.any.add(rule);
                }
            }
            index.all.sort(BEST_FIRST);
            indexes.put(entry.getKey(), index);
        }
    }

    /** Rules across all domains. */
    public int ruleCount() {
        int total = 0;
        for (Index index : indexes.values()) {
            total += index.all.size();
        }
        return total;
    }

    public int ruleCount(XpDomain domain) {
        Index index = indexes.get(domain);
        return index == null ? 0 : index.all.size();
    }

    /** How many files fed this table. */
    public int fileCount() {
        return files;
    }

    /** Every rule of a domain, best first. */
    public List<XpRule> rules(XpDomain domain) {
        Index index = indexes.get(domain);
        return index == null ? List.of() : Collections.unmodifiableList(index.all);
    }

    /** The winning rule for the subject, or null when none applies. */
    public XpMatch match(XpDomain domain, XpSubject subject) {
        return match(domain, subject, null);
    }

    /** As {@link #match(XpDomain, XpSubject)}, ignoring rules that carry any of these flags. */
    public XpMatch matchWithout(XpDomain domain, XpSubject subject, String flag) {
        return match(domain, subject, flag);
    }

    private XpMatch match(XpDomain domain, XpSubject subject, String ignoredFlag) {
        Index index = indexes.get(domain);
        if (index == null) {
            return null;
        }
        XpRule best = null;
        best = consider(best, index.byId.get(subject.id()), subject, ignoredFlag);
        best = consider(best, index.byNamespace.get(subject.namespace()), subject, ignoredFlag);
        best = consider(best, index.tags, subject, ignoredFlag);
        best = consider(best, index.any, subject, ignoredFlag);
        return best == null ? null : new XpMatch(best, subject);
    }

    private static XpRule consider(XpRule best, List<XpRule> candidates, XpSubject subject,
            String ignoredFlag) {
        if (candidates == null) {
            return best;
        }
        for (XpRule rule : candidates) {
            if (best != null && BEST_FIRST.compare(best, rule) <= 0) {
                continue;
            }
            if (ignoredFlag != null && rule.has(ignoredFlag)) {
                continue;
            }
            if (applies(rule, subject)) {
                best = rule;
            }
        }
        return best;
    }

    /** Whether the rule matches the subject, ignoring who wins. */
    public static boolean applies(XpRule rule, XpSubject subject) {
        return refusal(rule, subject) == null;
    }

    /** Why the rule does not apply to the subject, or null when it does. */
    static String refusal(XpRule rule, XpSubject subject) {
        if (rule.kind != null && !rule.kind.equals(subject.kind())) {
            return "is for " + rule.kind + " subjects, this is a " + subject.kind();
        }
        boolean hit = switch (rule.matcher) {
            case ID -> rule.key.equals(subject.id());
            case NAMESPACE -> rule.key.equals(subject.namespace());
            case TAG -> subject.hasTag(rule.key);
            case ANY -> true;
        };
        if (!hit) {
            return "does not match";
        }
        if (rule.minHealth > 0 && subject.maxHealth() < rule.minHealth) {
            return "needs max health " + trim(rule.minHealth) + " or more, this has " + trim(subject.maxHealth());
        }
        XpRule.Exclude ex = rule.exclude;
        if (!ex.isEmpty()) {
            if (ex.ids().contains(subject.id())) {
                return "excludes the id " + subject.id();
            }
            if (ex.namespaces().contains(subject.namespace())) {
                return "excludes the mod " + subject.namespace();
            }
            for (String tag : ex.tags()) {
                if (subject.hasTag(tag)) {
                    return "excludes the tag #" + tag;
                }
            }
        }
        return null;
    }

    static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** One line of an explanation: a rule that concerns the subject and what became of it. */
    public record Explained(XpRule rule, boolean winner, String why) {
    }

    /**
     * Every rule of the domain that names the subject (by id, namespace, tag or {@code *}), the
     * winner first, with the reason each other one lost or did not apply.
     */
    public List<Explained> explain(XpDomain domain, XpSubject subject) {
        List<Explained> out = new ArrayList<>();
        XpMatch winner = match(domain, subject);
        if (winner != null) {
            out.add(new Explained(winner.rule(), true, "wins: " + reason(winner.rule())));
        }
        for (XpRule rule : rules(domain)) {
            if (winner != null && rule == winner.rule()) {
                continue;
            }
            String refusal = refusal(rule, subject);
            if (refusal == null) {
                out.add(new Explained(rule, false, "matches but loses: " + loses(winner.rule(), rule)));
            } else if (!refusal.equals("does not match") && !refusal.startsWith("is for ")) {
                out.add(new Explained(rule, false, "skipped, it " + refusal));
            }
        }
        return out;
    }

    private static String reason(XpRule rule) {
        return switch (rule.matcher) {
            case ID -> "exact id";
            case TAG -> "tag #" + rule.key;
            case NAMESPACE -> "every id of " + rule.key;
            case ANY -> "matches anything";
        } + ", priority " + rule.priority;
    }

    private static String loses(XpRule winner, XpRule loser) {
        if (winner.priority != loser.priority) {
            return "priority " + loser.priority + " is below " + winner.priority;
        }
        if (winner.matcher != loser.matcher) {
            return "a " + winner.matcher.name().toLowerCase() + " match beats a "
                    + loser.matcher.name().toLowerCase() + " match at equal priority";
        }
        return loser.sourceFile.equals(winner.sourceFile)
                ? "listed earlier in " + loser.sourceFile + " than the winner"
                : "loaded earlier (" + loser.sourceFile + ") than the winner (" + winner.sourceFile + ")";
    }
}
