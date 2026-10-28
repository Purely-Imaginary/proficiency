package dev.amman.proficiency.skill;

/** The four personal bests tracked per skill (see {@link RecordRules}). */
public enum RecordMetric {
    /** Most XP earned in any rolling 60 seconds (XP per minute). */
    RATE,
    /** Most actions in one unbroken Tempo chain. */
    CHAIN,
    /** The biggest single XP grant. */
    GRANT,
    /** The skill's own best: Beastslaying kills in one chain, Fishing catches in a minute; 0 for the rest. */
    EXTRA;

    public static final RecordMetric[] VALUES = values();

    public static RecordMetric byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
