package dev.amman.proficiency.xp;

import dev.amman.proficiency.skill.Skill;

import java.util.Set;

/** One parsed rule. Immutable. See the README, "Tuning XP with a datapack", for the file format. */
public final class XpRule {

    /** How a rule's {@code match} names its subjects. The order is the tie-break: id beats tag. */
    public enum Matcher {
        ANY(0), NAMESPACE(1), TAG(2), ID(3);

        private final int rank;

        Matcher(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    /** What a rule must not match: whole mods, single ids, tags. */
    public record Exclude(Set<String> namespaces, Set<String> ids, Set<String> tags) {
        public static final Exclude NONE = new Exclude(Set.of(), Set.of(), Set.of());

        public boolean isEmpty() {
            return namespaces.isEmpty() && ids.isEmpty() && tags.isEmpty();
        }
    }

    /** The second skill of a split payment (stairs pay half Masonry and half Decorating). */
    public record Also(Skill skill, XpSpec xp) {
    }

    public final XpDomain domain;
    public final Matcher matcher;
    /** The id, the tag without its hash, the namespace without {@code :*}, or "*". */
    public final String key;
    public final int priority;
    /** Null when the rule says "pay nothing" ({@code "skill": "none"}) or has no skill. */
    public final Skill skill;
    public final boolean paysNothing;
    public final XpSpec xp;
    public final Also also;
    public final Set<String> flags;
    public final String tool;
    /** Restricts the rule to one subject kind, or null for any. */
    public final String kind;
    public final double minHealth;
    public final Double multiplier;
    public final Boolean boss;
    public final Exclude exclude;
    public final String sourceFile;
    /** Position in load order; a later rule beats an earlier one of equal rank. */
    public final int order;

    XpRule(Builder b) {
        this.domain = b.domain;
        this.matcher = b.matcher;
        this.key = b.key;
        this.priority = b.priority;
        this.skill = b.skill;
        this.paysNothing = b.paysNothing;
        this.xp = b.xp;
        this.also = b.also;
        this.flags = Set.copyOf(b.flags);
        this.tool = b.tool;
        this.kind = b.kind;
        this.minHealth = b.minHealth;
        this.multiplier = b.multiplier;
        this.boss = b.boss;
        this.exclude = b.exclude == null ? Exclude.NONE : b.exclude;
        this.sourceFile = b.sourceFile;
        this.order = b.order;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    /** The string a rule is written as: {@code #tag}, {@code ns:*}, {@code *} or the id. */
    public String matchText() {
        return switch (matcher) {
            case TAG -> "#" + key;
            case NAMESPACE -> key + ":*";
            case ANY -> "*";
            case ID -> key;
        };
    }

    /** Identity inside a domain: a later rule with the same key replaces an earlier one. */
    public String identity() {
        return (kind == null ? "" : kind) + "|" + matchText() + (minHealth > 0 ? "|" + minHealth : "");
    }

    XpRule withOrder(int newOrder) {
        Builder b = new Builder(this);
        b.order = newOrder;
        return new XpRule(b);
    }

    static final class Builder {
        XpDomain domain;
        Matcher matcher;
        String key;
        int priority;
        Skill skill;
        boolean paysNothing;
        XpSpec xp;
        Also also;
        Set<String> flags = Set.of();
        String tool;
        String kind;
        double minHealth;
        Double multiplier;
        Boolean boss;
        Exclude exclude;
        String sourceFile;
        int order;

        Builder() {
        }

        Builder(XpRule r) {
            domain = r.domain;
            matcher = r.matcher;
            key = r.key;
            priority = r.priority;
            skill = r.skill;
            paysNothing = r.paysNothing;
            xp = r.xp;
            also = r.also;
            flags = r.flags;
            tool = r.tool;
            kind = r.kind;
            minHealth = r.minHealth;
            multiplier = r.multiplier;
            boss = r.boss;
            exclude = r.exclude;
            sourceFile = r.sourceFile;
            order = r.order;
        }
    }
}
