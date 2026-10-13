package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.SiegeRules;

/**
 * Whether a Siege Horn may call a siege at a Hearth, and if not, why (docs/HEARTH-SPEC.md, "The
 * Siege Horn"): sieges on and not Peaceful, a Hearth in range, not smouldering, the ceiling the horn's
 * level needs ({@link SiegeRules#ceilingForLevel}: III for the first horn, VI for the last),
 * nothing pending or running there (two sieges never overlap) and {@code hornCooldownPlayHours} of
 * play since the last one ended. The first failing reason wins, in that order. Pure; the rule itself
 * is {@link SiegeRules#hornAllowed}.
 */
public final class HornCheck {

    public enum Result { OK, OFF, PEACEFUL, NO_HEARTH, SMOULDERING, CEILING, BUSY, COOLDOWN }

    private HornCheck() {
    }

    /** The first horn's check (siege level I). */
    public static Result check(boolean siegesOn, boolean peaceful, boolean hearthInRange, boolean smouldering,
            int ceiling, boolean busy, double playHoursSince, HearthData.Siege s) {
        return check(1, siegesOn, peaceful, hearthInRange, smouldering, ceiling, busy, playHoursSince, s);
    }

    public static Result check(int level, boolean siegesOn, boolean peaceful, boolean hearthInRange,
            boolean smouldering, int ceiling, boolean busy, double playHoursSince, HearthData.Siege s) {
        if (!siegesOn) {
            return Result.OFF;
        }
        if (peaceful) {
            return Result.PEACEFUL;
        }
        if (!hearthInRange) {
            return Result.NO_HEARTH;
        }
        if (smouldering) {
            return Result.SMOULDERING;
        }
        if (ceiling < SiegeRules.ceilingForLevel(level)) {
            return Result.CEILING;
        }
        if (busy) {
            return Result.BUSY;
        }
        if (!SiegeRules.hornAllowed(ceiling, playHoursSince, false, s)) {
            return Result.COOLDOWN;
        }
        return Result.OK;
    }

    /** Play minutes still to wait before the horn may be blown again (0 when it may), rounded up. */
    public static long minutesLeft(double playHoursSince, HearthData.Siege s) {
        double left = s.hornCooldownPlayHours() - playHoursSince;
        return left > 0 && Double.isFinite(left) ? (long) Math.ceil(left * 60.0) : 0L;
    }
}
