package dev.amman.proficiency.hearth.net;

import net.minecraft.Util;

/** The installed {@link HearthClient}: a no-op until the client lane sets its own. */
public final class HearthClients {

    private static final HearthClient NONE = new HearthClient() {
    };

    private static volatile HearthClient current = NONE;

    /**
     * The last {@code hearth_hud} payload: the one copy on the client (the Hearth HUD, the tracker and
     * the skills panel's streak block all read it); null until one came.
     */
    private static volatile HearthHudPayload lastHud;
    /** Client time ({@code Util.getMillis}) the last HUD payload arrived: the HUD moves Warmth on from it. */
    private static volatile long hudAt;

    private HearthClients() {
    }

    public static HearthClient get() {
        return current;
    }

    public static void set(HearthClient client) {
        current = client == null ? NONE : client;
    }

    /**
     * The loaders' receiver for {@link HearthHudPayload}: remembers it ({@link #lastHud}), then hands it
     * to the installed client. Client thread.
     */
    public static void hud(HearthHudPayload payload) {
        lastHud = payload;
        hudAt = Util.getMillis();
        current.onHud(payload);
    }

    /** The last HUD payload this client was sent, or null (none yet, or left the world). */
    public static HearthHudPayload lastHud() {
        return lastHud;
    }

    /** When the last HUD payload arrived ({@code Util.getMillis}). */
    public static long hudAt() {
        return hudAt;
    }

    /**
     * Whether the survival streak is held at a Hearth right now: the server's {@code STREAK_SAFE} flag
     * while the player is home. The one rule: the HUD tooltip and the skills panel both ask it.
     */
    public static boolean streakSafe() {
        HearthHudPayload hud = lastHud;
        return hud != null && hud.home() && hud.has(HearthHudPayload.FLAG_STREAK_SAFE);
    }

    /** Leaving a world: forgets the last HUD payload, then tells the installed client. */
    public static void leaveWorld() {
        lastHud = null;
        current.onLeaveWorld();
    }
}
