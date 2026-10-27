package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.net.HearthClients;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.skill.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What this client knows about the Hearth, from the payloads alone (the client never reads a block
 * entity): the last state, the player's own HUD numbers, the ledger, the tracker's rows. The HUD,
 * the tracker, the panel and the range ring all read it. Client thread only.
 */
public final class HearthView {

    /** The tracker's numbers and dots. */
    static final HearthTrackerModel TRACKER = new HearthTrackerModel();

    @Nullable
    private static HearthStatePayload state;
    private static long stateAt = Long.MIN_VALUE / 2;
    /** The last stage seen per Hearth this session (a stage that rose by one). */
    private static final Map<Long, Integer> STAGES = new HashMap<>();

    @Nullable
    private static HearthLedgerPayload ledger;

    private HearthView() {
    }

    // ---- State ------------------------------------------------------------------------------------

    /**
     * A state packet. Returns the stage it raised (the same Hearth was one stage lower before), or 0.
     */
    static int acceptState(HearthStatePayload payload, long now) {
        HearthMapMarks.note(payload);
        if (payload.reason() == HearthStatePayload.CAMPFIRE) {
            // A plain campfire's stage-0 panel: kept apart, so the HUD and tracker stay on the real Hearth.
            preview = payload;
            return 0;
        }
        state = payload;
        stateAt = now;
        if (payload.extra() != null) {
            // The server's offerings, for the click the Hearth block swallows (stages lane).
            dev.amman.proficiency.hearth.stages.HearthOfferings.serverOfferings(payload.extra().offerings());
        }
        double[] thresholds = floats(payload.extra() == null ? List.of() : payload.extra().thresholds());
        TRACKER.thresholds(thresholds);
        // Arriving in range starts the rows over at the server's numbers (no catch-up flight or tier-up).
        boolean arriving = payload.reason() == HearthStatePayload.ENTER;
        for (PoolRow row : payload.pools()) {
            TRACKER.apply(payload.pos(), row.pool(), row.charge(), row.tier(), row.lock(), row.need(), now, arriving);
        }
        Integer before = STAGES.put(payload.pos(), payload.stage());
        return before != null && payload.stage() == before + 1 ? payload.stage() : 0;
    }

    static void acceptTracker(HearthTrackerPayload payload, long now) {
        for (PoolRow row : payload.rows()) {
            TRACKER.apply(payload.pos(), row.pool(), row.charge(), row.tier(), row.lock(), row.need(), now);
        }
    }

    /**
     * Mirrored shares. A share of the skill the HUD line shows waits until that line's own dots have
     * landed in its bar, then leaves from there.
     */
    static void acceptGains(HearthGainPayload payload, long now, boolean animate) {
        for (HearthGainPayload.Gain gain : payload.gains()) {
            Skill skill = gain.skill() >= 0 && gain.skill() < Skill.VALUES.length ? Skill.VALUES[gain.skill()] : null;
            long delay = skill != null && dev.amman.proficiency.client.SkillHud.showing(skill, now)
                    ? dev.amman.proficiency.client.XpGainDots.FLIGHT_MS : 0;
            TRACKER.gain(gain.pool(), gain.amount(), now, delay, animate);
        }
    }

    @Nullable
    public static HearthStatePayload state() {
        return state;
    }

    /** A plain campfire's stage-0 panel ({@link HearthStatePayload#CAMPFIRE}) while it is open, else null. */
    @Nullable
    private static HearthStatePayload preview;

    /** What the Hearth panel shows: the campfire preview while one is open, else the Hearth's state. */
    @Nullable
    public static HearthStatePayload panelState() {
        return preview != null ? preview : state;
    }

    /** True while the panel shows a plain campfire, not a Hearth. */
    public static boolean previewing() {
        return preview != null;
    }

    /** The panel closed: the campfire preview is gone. */
    public static void clearPreview() {
        preview = null;
    }

    static long stateAt() {
        return stateAt;
    }

    // ---- HUD --------------------------------------------------------------------------------------

    /**
     * The player's own HUD numbers: the one copy {@link HearthClients#hud} keeps (the loaders' receiver
     * stores it before handing it on), so the HUD, the tracker and the skills panel read the same packet.
     */
    @Nullable
    static HearthHudPayload hud() {
        return HearthClients.lastHud();
    }

    /** Client time ({@code Util.getMillis}) the HUD packet arrived. */
    static long hudAt() {
        return HearthClients.hudAt();
    }

    /** True while the server says this player is by a Hearth. */
    public static boolean home() {
        HearthHudPayload hud = hud();
        return hud != null && hud.home();
    }

    /** The survival bonus's stacks are safe at the Hearth (the skills panel says so). */
    public static boolean streakSafe() {
        return HearthClients.streakSafe();
    }

    // ---- Ledger -----------------------------------------------------------------------------------

    static void acceptLedger(HearthLedgerPayload payload) {
        ledger = payload;
    }

    @Nullable
    public static HearthLedgerPayload ledger() {
        return ledger;
    }

    // ---- The server's data, or the shipped defaults -------------------------------------------------
    // (from what the panel shows: a campfire's preview carries the same data as a Hearth's state)

    @Nullable
    private static HearthStatePayload.Extra extra() {
        HearthStatePayload s = panelState();
        return s == null || s.extra() == null ? null : s.extra();
    }

    public static double[] thresholds() {
        return TRACKER.thresholdsCopy();
    }

    /** Every Comfort kind there is, in data order. */
    public static List<String> comfortAll() {
        HearthStatePayload.Extra extra = extra();
        return extra != null && !extra.comfortAll().isEmpty() ? extra.comfortAll()
                : HearthData.DEFAULTS.comfort().kinds();
    }

    public static int characterMax() {
        HearthStatePayload.Extra extra = extra();
        return extra != null && extra.characterMax() > 0 ? extra.characterMax() : HearthData.DEFAULTS.character().max();
    }

    /** The spacing between Hearths (the red ring), from the server, else twice the range plus 16. */
    static int spacing() {
        HearthStatePayload.Extra extra = extra();
        if (extra != null && extra.spacing() > 0) {
            return extra.spacing();
        }
        HearthStatePayload s = panelState();
        return s == null ? 64 : Math.round(2 * s.radius() + 16);
    }

    static long warningTicks() {
        HearthStatePayload.Extra extra = extra();
        return extra != null && extra.warningTicks() > 0 ? extra.warningTicks() : SiegeClock.DEFAULT_WARNING_TICKS;
    }

    @Nullable
    static HearthStatePayload.Boon boon(int pool) {
        HearthStatePayload.Extra extra = extra();
        if (extra == null) {
            return null;
        }
        for (HearthStatePayload.Boon boon : extra.boons()) {
            if (boon.pool() == pool) {
                return boon;
            }
        }
        return null;
    }

    public static String nextOffering() {
        HearthStatePayload.Extra extra = extra();
        if (extra != null) {
            return extra.nextOffering();
        }
        HearthStatePayload s = panelState();
        int stage = s == null ? 0 : s.stage();
        return stage >= 0 && stage < HearthData.DEFAULTS.stages().size()
                ? HearthData.DEFAULTS.stages().get(stage).offering() : "";
    }

    private static double[] floats(List<Float> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    /** Leaving a world: nothing carries over to the next server. */
    static void clear() {
        state = null;
        preview = null;
        stateAt = Long.MIN_VALUE / 2;
        STAGES.clear();
        ledger = null;
        TRACKER.clear();
        dev.amman.proficiency.hearth.stages.HearthOfferings.forgetServerOfferings();
    }
}
