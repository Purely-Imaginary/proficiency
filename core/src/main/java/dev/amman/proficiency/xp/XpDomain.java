package dev.amman.proficiency.xp;

import java.util.Locale;
import java.util.Set;

/**
 * The places XP is decided, one list of rules each. The JSON key of a domain in an xp_sources
 * file is {@link #key()}.
 */
public enum XpDomain {
    /** Breaking a block: the gathering skill, the XP, the tool it needs. */
    BREAK("break", Set.of("crop")),
    /** Placing a block: Masonry, Decorating, Farming (planting) or Engineering (machines). */
    PLACE("place", Set.of("planting", "machine", "decor")),
    /** Crafting an item: the Engineering craft bonus for machine parts. */
    CRAFT("craft", Set.of("machine")),
    /** An item used or a projectile flying that counts as a spell (Spellcasting). */
    CAST("cast", Set.of()),
    /** Killing a mob: the XP the kill itself pays (Beastslaying for modded mobs). */
    KILL("kill", Set.of()),
    /** The kill bonus on top of the weapon skill, as the same kind of spec. No rule: the config formula. */
    KILL_BONUS("kill_bonus", Set.of()),
    /** Whether a mob counts as a boss ({@code "boss": true}) or not ({@code "boss": false}). */
    BOSS("boss", Set.of()),
    /** Standing in a structure for the first time. */
    STRUCTURE("structure", Set.of("grand")),
    /** Entering a biome for the first time. */
    BIOME("biome", Set.of()),
    /** Entering a dimension for the first time. */
    DIMENSION("dimension", Set.of()),
    /** The first-time bonus multiplier of a block, mob or item kind. */
    FIRST_TIME("first_time", Set.of());

    private final String key;
    private final Set<String> flags;

    XpDomain(String key, Set<String> flags) {
        this.key = key;
        this.flags = flags;
    }

    public String key() {
        return key;
    }

    /** The flag names a rule of this domain may carry. */
    public Set<String> flags() {
        return flags;
    }

    public static XpDomain byKey(String key) {
        if (key == null) {
            return null;
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (XpDomain domain : values()) {
            if (domain.key.equals(wanted)) {
                return domain;
            }
        }
        return null;
    }
}
