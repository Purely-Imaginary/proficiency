package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.hearth.net.HearthClient;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import net.minecraft.Util;

/**
 * The client lane's {@link HearthClient}: each Hearth payload goes to the part of the client that
 * shows it. Installed by {@link HearthClientInit}; runs on the client thread.
 */
final class HearthClientImpl implements HearthClient {

    @Override
    public void onState(HearthStatePayload payload) {
        // A stage sealed is the stages lane's moment (its DiscoveryPayload banner and world sounds); the
        // open panel plays its own unlock sweep when the stage it shows rises.
        HearthView.acceptState(payload, Util.getMillis());
        if (payload.reason() == HearthStatePayload.PANEL || payload.reason() == HearthStatePayload.CAMPFIRE) {
            HearthScreen.open();
        }
    }

    @Override
    public void onTracker(HearthTrackerPayload payload) {
        HearthView.acceptTracker(payload, Util.getMillis());
    }

    @Override
    public void onGains(HearthGainPayload payload) {
        HearthView.acceptGains(payload, Util.getMillis(), ProficiencyClientConfig.hudXpDots());
    }

    @Override
    public void onLedger(HearthLedgerPayload payload) {
        HearthView.acceptLedger(payload);
    }

    @Override
    public void onSiegePanel(SiegePanelPayload payload) {
        SiegePanelHud.accept(payload, Util.getMillis());
    }

    @Override
    public void onPing(HearthPingPayload payload) {
        SiegePanelHud.acceptPing(payload, Util.getMillis());
    }

    @Override
    public void onHud(HearthHudPayload payload) {
        HearthHud.accept(payload, Util.getMillis());
    }

    @Override
    public void onLeaveWorld() {
        HearthView.clear();
        HearthChat.reset();
        HearthHud.reset();
        TrackerHud.reset();
        SiegePanelHud.reset();
        HearthRangeRing.reset();
    }
}
