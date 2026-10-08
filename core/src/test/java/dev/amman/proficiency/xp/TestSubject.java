package dev.amman.proficiency.xp;

import java.util.Set;

/** A subject from plain data, for the rule tests. */
public record TestSubject(String kind, String id, Set<String> tags, double hardness, double maxHealth)
        implements XpSubject {

    public static TestSubject block(String id, double hardness, String... tags) {
        return new TestSubject("block", id, Set.of(tags), hardness, 0);
    }

    public static TestSubject item(String id, String... tags) {
        return new TestSubject("item", id, Set.of(tags), 0, 0);
    }

    public static TestSubject entity(String id, double maxHealth, String... tags) {
        return new TestSubject("entity", id, Set.of(tags), 0, maxHealth);
    }

    public static TestSubject plain(String kind, String id, String... tags) {
        return new TestSubject(kind, id, Set.of(tags), 0, 0);
    }

    @Override
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
