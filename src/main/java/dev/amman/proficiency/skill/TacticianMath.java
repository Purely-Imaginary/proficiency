package dev.amman.proficiency.skill;

/**
 * The numbers behind Tactician. Pure: no Minecraft types, so every rule here is unit tested.
 * {@code TacticianEvents} watches the world and pays; this class only decides.
 *
 * <p>Tactician is the back line: fighting with range from behind your own front line, and making
 * that front line stronger. Any ranged weapon counts (a bow, a crossbow, a thrown trident, a
 * spell, a potion, even a snowball). Three XP sources:
 * <ul>
 * <li>support: a ranged hit on a mob that is after another player, per point of damage;</li>
 * <li>rescue: a ranged kill of a mob that hurt another player in the last 5 s;</li>
 * <li>Overwatch: a ranged hit on a mob with a friend between you and it, by distance.</li>
 * </ul>
 * Archery and Crossbows keep raw ranged damage; Sneaking keeps hits from behind a mob. "Behind"
 * here means behind your friends.
 */
public final class TacticianMath {

    // ---- XP --------------------------------------------------------------------------------

    /** Support: XP per point of damage on a mob that is after another player. */
    public static final double DEFAULT_SUPPORT_XP_PER_DAMAGE = 0.15;
    /** Rescue: XP for a ranged kill of a 20-health mob that hurt a friend in the last 5 s. */
    public static final double DEFAULT_RESCUE_XP = 2.5;
    /** Overwatch: XP per block between you and the mob, on a hit with a friend in between. */
    public static final double DEFAULT_OVERWATCH_XP_PER_BLOCK = 0.06;

    /** Support: one hit counts at most this much damage. */
    public static final double SUPPORT_HIT_CAP = 20.0;
    /** Support: one mob pays for at most this much damage in total. Saved on the mob. */
    public static final double SUPPORT_MOB_CAP = 40.0;
    /** Overwatch: a hit from closer than this is not a ranged shot worth paying. */
    public static final double OVERWATCH_MIN_BLOCKS = 6.0;
    /** Overwatch: counted up to this far. */
    public static final double OVERWATCH_MAX_BLOCKS = 30.0;
    /** Overwatch: one mob pays at most this many times. Saved on the mob. */
    public static final int OVERWATCH_PAYS_PER_MOB = 3;
    /** Rescue: the friend the mob hurt must be this close to you. */
    public static final double RESCUE_RADIUS = 32.0;

    /** A mob is "engaged" with a friend this long after it hurt them, or after you pulled it off them (5 s). */
    public static final long ENGAGED_TICKS = 100L;

    /** Hammer and Anvil: hits on a mob fighting a Charger (not you) pay this many times the XP. */
    public static final double PAIR_XP = 2.0;

    // ---- Anti-farm ---------------------------------------------------------------------------

    /** The spot rule: payouts this close to each other count as one spot. */
    public static final double SPOT_RADIUS = 12.0;
    /** The spot rule: payouts are remembered this long (5 min). */
    public static final long SPOT_WINDOW_TICKS = 6000L;
    /** The spot rule: at most this much base XP from one spot in the window. */
    public static final double SPOT_XP_LIMIT = 48.0;

    // ---- Overwatch (the state) -----------------------------------------------------------------

    /** Overwatch: the friend in front of you is at most this far from you. */
    public static final double OVERWATCH_FRIEND_RADIUS = 16.0;
    /** A friend is "between" when they stand this close to the line from you to the mob... */
    public static final double FRONT_WIDTH = 3.0;
    /** ...at least this far from both ends of it... */
    public static final double BETWEEN_MARGIN = 1.0;
    /** ...and within this much height of the line's ends. */
    public static final double BETWEEN_HEIGHT = 6.0;

    // ---- Called Shot -------------------------------------------------------------------------

    /** Called Shot's mark lasts this long (8 s). */
    public static final int MARK_TICKS = 160;
    /** Painted Target: this much longer (4 s). Field Marshal adds the same again. */
    public static final int MARK_LONG_TICKS = 80;
    /** Friends (not you) deal this much more damage to a marked mob, times proc power. */
    public static final double MARK_FRIEND_BONUS = 0.15;
    /** Painted Target: this much more on top. */
    public static final double MARK_LONG_BONUS = 0.05;
    /** The mark's icon, sound and HUD line go to players this close to the mob. */
    public static final double MARK_AUDIENCE_RADIUS = 48.0;
    /** One Tactician keeps at most this many marks; a new one drops the oldest. */
    public static final int MARKS_PER_PLAYER = 4;
    /** Focus Fire: a mark jumps to a hostile this close to the mob that died. */
    public static final double MARK_JUMP_RADIUS = 8.0;

    /** Hammer and Anvil: a Charger's first blood on a mob another player marked hits this much harder. */
    public static final double PAIR_FIRST_BLOOD = 1.5;
    /** The Hammer and Anvil synergy on either of the two: this instead. */
    public static final double PAIR_SYNERGY_FIRST_BLOOD = 2.0;

    // ---- Talents -----------------------------------------------------------------------------

    /** Headshot: this much more damage per rank for a projectile that hits the head. */
    public static final double HEADSHOT_PER_RANK = 0.10;
    /** Headshot: the head starts this far below the eyes. */
    public static final double HEAD_ZONE = 0.3;
    /** Crossfire: this much more damage per friend between you and the mob, per rank. */
    public static final double CROSSFIRE_PER_FRIEND = 0.03;
    /** Crossfire: at most this many friends count. */
    public static final int CROSSFIRE_MAX_FRIENDS = 3;
    /** Quartermaster: the chance per rank that a kill on a marked mob gives the arrow back. */
    public static final double REFUND_PER_RANK = 1.0 / 3.0;
    /** Field Marshal: each kill a friend makes on your mark takes this off Suppressing Fire (3 s). */
    public static final long FIELD_MARSHAL_COOLDOWN_TICKS = 60L;

    // ---- Suppressing Fire and Covering Fire --------------------------------------------------

    /** Suppressing Fire: what your projectiles hit is slowed this long (3 s)... */
    public static final int SUPPRESS_SLOW_TICKS = 60;
    /** ...at Slowness II (amplifier 1), III with Field Marshal. */
    public static final int SUPPRESS_SLOW_AMPLIFIER = 1;

    /** Covering Fire: the Guardian a mob is after needs this Guardian level... */
    public static final int GUARDIAN_LEVEL = 10;
    /**
     * ...and the Tactician this Tactician level. Hammer and Anvil's double XP also needs the
     * Charger at this Charger level, so a friend who never trained Charger does not count.
     */
    public static final int PAIR_LEVEL = 10;
    /** Covering Fire: a mob you hit while it is after a Guardian deals less damage for this long (3 s)... */
    public static final int COVER_TICKS = 60;
    /** ...this share less. */
    public static final double COVER_CUT = 0.25;
    /** The Covering Fire synergy on either of the two: this share less instead, for twice as long. */
    public static final double COVER_SYNERGY_CUT = 0.40;

    private TacticianMath() {
    }

    // ---- XP ----------------------------------------------------------------------------------

    /**
     * Support XP for one hit: the damage counted (at most 20 per hit, and whatever is left of the
     * mob's 40), times the rate. {@code alreadyCounted} is what this mob already paid for.
     */
    public static double supportCounted(double damage, double alreadyCounted) {
        if (!(damage > 0) || !Double.isFinite(damage)) {
            return 0.0;
        }
        double left = SUPPORT_MOB_CAP - Math.max(0.0, alreadyCounted);
        return Math.max(0.0, Math.min(Math.min(damage, SUPPORT_HIT_CAP), left));
    }

    public static double supportXp(double counted, double rate) {
        return counted > 0 && rate > 0 ? counted * rate : 0.0;
    }

    /** Rescue XP: the base times the mob's worth. */
    public static double rescueXp(double base, double worth) {
        return base > 0 && worth > 0 ? base * worth : 0.0;
    }

    /** Overwatch XP: per block from 6 blocks on, counted up to 30. Closer than 6 pays nothing. */
    public static double overwatchXp(double distance, double perBlock) {
        if (!(distance >= OVERWATCH_MIN_BLOCKS) || !(perBlock > 0)) {
            return 0.0;
        }
        return Math.min(distance, OVERWATCH_MAX_BLOCKS) * perBlock;
    }

    /** A mob hurt a friend, or was pulled off one, at {@code at}: it still counts as engaged. */
    public static boolean engaged(long at, long now) {
        return at >= 0 && now >= at && now - at <= ENGAGED_TICKS;
    }

    /** The spot rule: this much base XP may still be paid here. {@code recentNear} is what this spot paid in 5 min. */
    public static double spotRoom(double recentNear) {
        return Math.max(0.0, SPOT_XP_LIMIT - Math.max(0.0, recentNear));
    }

    /** A remembered payout still counts for the spot rule. */
    public static boolean inSpotWindow(long paidAt, long now) {
        return paidAt >= 0 && now >= paidAt && now - paidAt < SPOT_WINDOW_TICKS;
    }

    // ---- Geometry ----------------------------------------------------------------------------

    /**
     * Whether a friend at {@code (fx, fy, fz)} stands between you at {@code (ax, ay, az)} and a mob
     * at {@code (tx, ty, tz)}: within 3 blocks of the straight line between you (on the ground
     * plane), more than a block from either end, and not far above or below. This is "behind your
     * own front line", not behind the mob.
     */
    public static boolean between(double ax, double ay, double az, double fx, double fy, double fz,
            double tx, double ty, double tz) {
        double dx = tx - ax;
        double dz = tz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length <= 2 * BETWEEN_MARGIN) {
            return false;
        }
        double ox = fx - ax;
        double oz = fz - az;
        double along = (ox * dx + oz * dz) / length;
        if (along <= BETWEEN_MARGIN || along >= length - BETWEEN_MARGIN) {
            return false;
        }
        double side = Math.abs(ox * dz - oz * dx) / length;
        if (side > FRONT_WIDTH) {
            return false;
        }
        double low = Math.min(ay, ty) - BETWEEN_HEIGHT;
        double high = Math.max(ay, ty) + BETWEEN_HEIGHT;
        return fy >= low && fy <= high;
    }

    /** Headshot: the hit point is at the eyes or at most 0.3 below them. */
    public static boolean headshot(double hitY, double eyeY) {
        return Double.isFinite(hitY) && hitY >= eyeY - HEAD_ZONE;
    }

    // ---- Damage ------------------------------------------------------------------------------

    /** The passive: ranged damage on a mob that is after someone else, times this. */
    public static double passiveMultiplier(double bonus) {
        return 1.0 + Math.max(0.0, bonus);
    }

    /** Called Shot: a friend's damage on your mark, times this. Proc power raises it. */
    public static double markMultiplier(double power, boolean paintedTarget) {
        double bonus = MARK_FRIEND_BONUS * Math.max(1.0, power) + (paintedTarget ? MARK_LONG_BONUS : 0.0);
        return 1.0 + bonus;
    }

    /** Called Shot's length: 8 s, plus 4 s for Painted Target and 4 s for Field Marshal. */
    public static int markTicks(boolean paintedTarget, boolean fieldMarshal) {
        return MARK_TICKS + (paintedTarget ? MARK_LONG_TICKS : 0) + (fieldMarshal ? MARK_LONG_TICKS : 0);
    }

    /** Whether a mark that ends at {@code until} still holds. */
    public static boolean markHolds(long until, long now) {
        return now < until;
    }

    /** Headshot's damage multiplier. */
    public static double headshotMultiplier(int rank) {
        return 1.0 + HEADSHOT_PER_RANK * Math.max(0, rank);
    }

    /** Crossfire's damage multiplier: 3% per friend in the line per rank, at most 3 friends. */
    public static double crossfireMultiplier(int rank, int friendsBetween) {
        int friends = Math.max(0, Math.min(CROSSFIRE_MAX_FRIENDS, friendsBetween));
        return 1.0 + CROSSFIRE_PER_FRIEND * Math.max(0, rank) * friends;
    }

    /** Hammer and Anvil, the Charger side: the first-blood multiplier on a mob another player marked. */
    public static double pairFirstBlood(boolean markedByOther, boolean synergy) {
        if (!markedByOther) {
            return 1.0;
        }
        return synergy ? PAIR_SYNERGY_FIRST_BLOOD : PAIR_FIRST_BLOOD;
    }

    /** Quartermaster's chance to give the arrow back. */
    public static double refundChance(int rank) {
        return Math.min(1.0, REFUND_PER_RANK * Math.max(0, rank));
    }

    /** Covering Fire: what is left of a covered mob's hit on a player. */
    public static double coverMultiplier(boolean synergy) {
        return 1.0 - (synergy ? COVER_SYNERGY_CUT : COVER_CUT);
    }

    /** Covering Fire: how long the cover lasts. */
    public static int coverTicks(boolean synergy) {
        return synergy ? COVER_TICKS * 2 : COVER_TICKS;
    }

    /** Suppressing Fire's slow: Slowness II, or III with Field Marshal. */
    public static int suppressAmplifier(boolean fieldMarshal) {
        return SUPPRESS_SLOW_AMPLIFIER + (fieldMarshal ? 1 : 0);
    }
}
