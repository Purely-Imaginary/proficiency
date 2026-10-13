package dev.amman.proficiency.hearth;

/**
 * What a pool gives. Boon types are code; which pool gives which boon, and every number, are data
 * (docs/HEARTH-SPEC.md, "Data"). The effects lane implements each type.
 */
public enum BoonType {
    /** Hunger drains slower: exhaustion scaled by {@code 1 - value x Warmth}. */
    HUNGER_SLOW("hunger_slow"),
    /** Out of combat, one heart per {@code value} seconds; temp HP from {@code absorptionPerTier}. */
    REGEN_OUT_OF_COMBAT("regen_out_of_combat"),
    /** Damage from mobs reduced by {@code value}; never from players or their pets. */
    MOB_DAMAGE_REDUCTION("mob_damage_reduction"),
    /** Furnaces and brewing stands in range run {@code value} faster while someone is home. */
    STATION_SPEED("station_speed"),
    /** A material refund at crafting tables in range (the pool's {@code refund} numbers). */
    REFUND_CHANCE("refund_chance"),
    /** Warmth fills faster and Provisioned lasts longer ({@code warmthSeconds}, {@code provisionedMinutes}). */
    READINESS("readiness");

    private final String id;

    BoonType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BoonType byId(String id) {
        for (BoonType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
