package dev.amman.proficiency.fabric;

/** Implemented by ServerPlayerMixin: forget the cached tab-list name so the next request rebuilds it. */
public interface TabNameCache {
    void proficiency$resetTabListName();
}
