package dev.amman.proficiency.hearth.net;

/**
 * What the client does with each Hearth payload. The loaders' receivers call
 * {@link HearthClients#get()} on the client thread; until the client lane installs its own
 * ({@code HearthClientInit}), every method does nothing. Plain interface, no client classes, so a
 * dedicated server can load it.
 */
public interface HearthClient {

    default void onState(HearthStatePayload payload) {
    }

    default void onTracker(HearthTrackerPayload payload) {
    }

    default void onGains(HearthGainPayload payload) {
    }

    default void onLedger(HearthLedgerPayload payload) {
    }

    default void onBuildPlan(HearthBuildPlanPayload payload) {
    }

    default void onSiegePanel(SiegePanelPayload payload) {
    }

    default void onPing(HearthPingPayload payload) {
    }

    default void onHud(HearthHudPayload payload) {
    }

    /**
     * A tale was told (Tales paid in, a Chronicle page set down, a tale retold). The default shows
     * the tales lane's pay-in panel, so a client implementation need not handle it; override it to
     * draw the moment differently.
     */
    default void onTales(HearthTalesPayload payload) {
        dev.amman.proficiency.hearth.tales.client.TalesClient.accept(payload);
    }

    /** Leaving a world: drop everything shown, so the next server starts clean. */
    default void onLeaveWorld() {
    }
}
