package dev.amman.proficiency.fabric.client;

import dev.amman.proficiency.hearth.net.HearthClients;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The Hearth's client receivers on Fabric; each goes to the installed {@code HearthClient}. */
public final class FabricHearthClientNetwork {

    private FabricHearthClientNetwork() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HearthStatePayload.TYPE,
                (payload, context) -> HearthClients.get().onState(payload));
        ClientPlayNetworking.registerGlobalReceiver(HearthTrackerPayload.TYPE,
                (payload, context) -> HearthClients.get().onTracker(payload));
        ClientPlayNetworking.registerGlobalReceiver(HearthGainPayload.TYPE,
                (payload, context) -> HearthClients.get().onGains(payload));
        ClientPlayNetworking.registerGlobalReceiver(HearthLedgerPayload.TYPE,
                (payload, context) -> HearthClients.get().onLedger(payload));
        ClientPlayNetworking.registerGlobalReceiver(SiegePanelPayload.TYPE,
                (payload, context) -> HearthClients.get().onSiegePanel(payload));
        ClientPlayNetworking.registerGlobalReceiver(HearthPingPayload.TYPE,
                (payload, context) -> HearthClients.get().onPing(payload));
        ClientPlayNetworking.registerGlobalReceiver(HearthHudPayload.TYPE,
                (payload, context) -> HearthClients.hud(payload));
        ClientPlayNetworking.registerGlobalReceiver(HearthTalesPayload.TYPE,
                (payload, context) -> HearthClients.get().onTales(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.amman.proficiency.homebuilder.net.HomebuilderPayload.TYPE,
                (payload, context) -> dev.amman.proficiency.homebuilder.net.HomebuilderNet.client().accept(payload));
    }
}
