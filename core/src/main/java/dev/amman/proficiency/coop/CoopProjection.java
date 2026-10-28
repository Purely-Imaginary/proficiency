package dev.amman.proficiency.coop;

/**
 * Where a world point sits on the GUI (1.45.0, co-op XP streams): the start of a partner's dots. Pure, no
 * Minecraft: Minecraft's own camera conventions (yaw 0 faces +Z, positive pitch looks down, the FOV option is the
 * vertical angle) in plain trigonometry, so it is the same on 1.21.1 and 1.20.1 and it is unit tested. A point
 * that is off the screen, or behind the camera, is pulled to the screen's edge in its direction, {@code inset}
 * pixels in, so a partner behind you to the left streams in from the left edge.
 */
public final class CoopProjection {

    /** Closer than this along the view (blocks) counts as behind. */
    static final double NEAR = 0.05;

    private CoopProjection() {
    }

    /**
     * @param x        GUI x
     * @param y        GUI y
     * @param onScreen true when the point is in front and inside the inset rectangle (else it was clamped)
     */
    public record Point(float x, float y, boolean onScreen) {
    }

    /**
     * @param cx camera position
     * @param yaw camera yaw, degrees
     * @param pitch camera pitch, degrees (positive looks down)
     * @param fovY the vertical field of view, degrees
     * @param width GUI width
     * @param height GUI height
     * @param tx the world point
     * @param inset how far inside the screen edge a clamped point sits
     */
    public static Point project(double cx, double cy, double cz, float yaw, float pitch, double fovY, int width,
            int height, double tx, double ty, double tz, int inset) {
        double yawR = Math.toRadians(yaw);
        double pitchR = Math.toRadians(pitch);
        double sy = Math.sin(yawR);
        double cyw = Math.cos(yawR);
        double sp = Math.sin(pitchR);
        double cp = Math.cos(pitchR);
        // Forward, right and up of the camera in world space.
        double fx = -sy * cp;
        double fy = -sp;
        double fz = cyw * cp;
        double rx = -cyw;
        double rz = -sy;
        // up = right x forward
        double ux = 0 * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - 0 * fx;

        double dx = tx - cx;
        double dy = ty - cy;
        double dz = tz - cz;
        double depth = dx * fx + dy * fy + dz * fz;
        double side = dx * rx + dz * rz;
        double rise = dx * ux + dy * uy + dz * uz;

        double halfW = width / 2.0;
        double halfH = height / 2.0;
        if (depth > NEAR) {
            double tan = Math.tan(Math.toRadians(Math.max(1.0, Math.min(179.0, fovY))) / 2.0);
            double aspect = height > 0 ? width / (double) height : 1.0;
            double ndcX = side / (depth * tan * aspect);
            double ndcY = rise / (depth * tan);
            double sx = halfW * (1 + ndcX);
            double sy2 = halfH * (1 - ndcY);
            if (sx >= inset && sx <= width - inset && sy2 >= inset && sy2 <= height - inset) {
                return new Point((float) sx, (float) sy2, true);
            }
            return clamp(halfW, halfH, sx - halfW, sy2 - halfH, inset);
        }
        // Behind: the side decides which edge; height counts for little, since "up" behind you is a guess.
        double ddx = side;
        double ddy = -rise * 0.25;
        if (Math.abs(ddx) < 1e-6 && Math.abs(ddy) < 1e-6) {
            ddy = 1;
        }
        return clamp(halfW, halfH, ddx, ddy, inset);
    }

    /**
     * The point on the inset rectangle round the centre ({@code halfW}, {@code halfH}) in the direction
     * ({@code dx}, {@code dy}), GUI y down.
     */
    public static Point clamp(double halfW, double halfH, double dx, double dy, int inset) {
        double extentX = Math.max(1.0, halfW - inset);
        double extentY = Math.max(1.0, halfH - inset);
        if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) {
            return new Point((float) halfW, (float) (halfH + extentY), false);
        }
        double scale = Math.min(Math.abs(dx) > 1e-9 ? extentX / Math.abs(dx) : Double.MAX_VALUE,
                Math.abs(dy) > 1e-9 ? extentY / Math.abs(dy) : Double.MAX_VALUE);
        return new Point((float) (halfW + dx * scale), (float) (halfH + dy * scale), false);
    }
}
