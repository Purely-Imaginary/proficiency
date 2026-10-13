package dev.amman.proficiency.fabric;

import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** The Hearth's payload types on Fabric and the one server-bound receiver. Client receivers: FabricHearthClientNetwork. */
public final class FabricHearthNetwork {

    private FabricHearthNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(HearthStatePayload.TYPE, HearthStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HearthTrackerPayload.TYPE, HearthTrackerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HearthGainPayload.TYPE, HearthGainPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HearthLedgerPayload.TYPE, HearthLedgerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SiegePanelPayload.TYPE, SiegePanelPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HearthPingPayload.TYPE, HearthPingPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HearthHudPayload.TYPE, HearthHudPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(HearthTalesPayload.TYPE, HearthTalesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(HearthRequestPayload.TYPE, HearthRequestPayload.STREAM_CODEC);
        // Fabric runs play-phase receivers on the server thread already.
        ServerPlayNetworking.registerGlobalReceiver(HearthRequestPayload.TYPE,
                (payload, context) -> HearthHooks.onRequest(context.player(), payload));
        // Homebuilder: its news to the client, the wheel's slots back.
        PayloadTypeRegistry.playS2C().register(dev.amman.proficiency.homebuilder.net.HomebuilderPayload.TYPE,
                dev.amman.proficiency.homebuilder.net.HomebuilderPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.TYPE,
                dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.TYPE,
                (payload, context) -> dev.amman.proficiency.homebuilder.HomebuilderEvents.onAction(context.player(),
                        payload));
    }
}
