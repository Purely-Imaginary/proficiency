package dev.amman.proficiency.hearth.client;

/**
 * The arrow home on the siege panel and after a bell's "come home" call: which way the Hearth lies
 * from where the player looks, and how far. Minecraft's yaw: 0 faces south (+z), 90 west (-x),
 * clockwise seen from above.
 */
public final class HomeArrow {

    private HomeArrow() {
    }

    /**
     * Degrees from the player's facing to the target, -180..180, clockwise positive: 0 straight
     * ahead (the arrow points up), 90 to the right, 180 behind.
     */
    public static float relative(double px, double pz, double tx, double tz, float playerYaw) {
        double dx = tx - px;
        double dz = tz - pz;
        if (dx * dx + dz * dz < 1e-6) {
            return 0f;
        }
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        return wrap((float) (target - playerYaw));
    }

    /** An angle folded into -180..180. */
    public static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** Horizontal distance in whole blocks. */
    public static int distance(double px, double pz, double tx, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
