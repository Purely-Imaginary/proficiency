package dev.amman.proficiency.hearth;

/**
 * The context bell (docs/HEARTH-SPEC.md, "Gathering"): the stage-V bell of a Hearth does one thing
 * when rung, picked by the moment, first match wins: the alarm during a siege or its countdown,
 * the dinner bell within {@code dinnerTriggerSeconds} of someone eating in range, otherwise the call
 * home. Each call has its own cooldown; the dinner bell's is "one per Feast".
 */
public final class BellRules {

    public enum Call {
        NONE, ALARM, DINNER, COME_HOME;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The call and whether its cooldown allows it now. */
    public record Ring(Call call, boolean ready) {
    }

    /**
     * The bell's place: the Hearth's check box, 7x7 around the campfire from the platform to one above
     * the roof (docs/HEARTH-SPEC.md, "Building and upgrading"). The numbers are the structure check's
     * own, so the bell and the build can never disagree about the box.
     */
    public static final int BOX_RADIUS = dev.amman.proficiency.hearth.stages.HearthStructure.BOX_RADIUS;
    public static final int BOX_BELOW = dev.amman.proficiency.hearth.stages.HearthStructure.BOX_BELOW;
    public static final int BOX_ABOVE = dev.amman.proficiency.hearth.stages.HearthStructure.BOX_ABOVE;

    private BellRules() {
    }

    /** Whether a bell at this offset from the campfire is inside the Hearth's check box. */
    public static boolean inBox(int dx, int dy, int dz) {
        return Math.abs(dx) <= BOX_RADIUS && Math.abs(dz) <= BOX_RADIUS && dy >= -BOX_BELOW && dy <= BOX_ABOVE;
    }

    /**
     * Which call a ring makes.
     *
     * @param hearthBell     the rung bell is this Hearth's stage-V bell (in its check box)
     * @param siegeOrWarning a siege runs or counts down here
     * @param ateRecently    someone ate in range within {@code dinnerTriggerSeconds}
     */
    public static Call pick(int stage, boolean hearthBell, boolean siegeOrWarning, boolean ateRecently) {
        if (!hearthBell || stage < 5) {
            return Call.NONE;
        }
        if (siegeOrWarning) {
            return Call.ALARM;
        }
        if (ateRecently) {
            return Call.DINNER;
        }
        return Call.COME_HOME;
    }

    /**
     * The ring: the call picked by the moment, and whether it is off cooldown.
     *
     * @param lastAlarm      game time of the last alarm here (Long.MIN_VALUE for never)
     * @param lastComeHome   game time of the last call home here
     * @param feastDoneToday a Feast already happened today, or a dinner window is open: the dinner bell has rung its one
     */
    public static Ring ring(int stage, boolean hearthBell, boolean siegeOrWarning, boolean ateRecently,
            boolean feastDoneToday, long now, long lastAlarm, long lastComeHome, HearthData data) {
        Call call = pick(stage, hearthBell, siegeOrWarning, ateRecently);
        HearthData.Bell bell = data.bell();
        boolean ready = switch (call) {
            case ALARM -> since(now, lastAlarm) >= Math.round(bell.alarmCooldown() * 20.0);
            case COME_HOME -> since(now, lastComeHome) >= Math.round(bell.comeHomeCooldown() * 20.0);
            case DINNER -> !feastDoneToday;
            case NONE -> false;
        };
        return new Ring(call, ready);
    }

    private static long since(long now, long then) {
        return then == Long.MIN_VALUE ? Long.MAX_VALUE : now - then;
    }
}
