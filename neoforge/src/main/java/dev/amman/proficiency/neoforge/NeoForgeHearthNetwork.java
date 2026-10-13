package dev.amman.proficiency.neoforge;

import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.net.HearthClients;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Hearth's payloads on NeoForge, on the same versioned registrar as the rest (protocol 7).
 * Client handlers run in {@code enqueueWork} and go to the installed {@code HearthClient}.
 */
public final class NeoForgeHearthNetwork {

    private NeoForgeHearthNetwork() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(HearthStatePayload.TYPE, HearthStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onState(payload)));
        registrar.playToClient(HearthTrackerPayload.TYPE, HearthTrackerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onTracker(payload)));
        registrar.playToClient(HearthGainPayload.TYPE, HearthGainPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onGains(payload)));
        registrar.playToClient(HearthLedgerPayload.TYPE, HearthLedgerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onLedger(payload)));
        registrar.playToClient(SiegePanelPayload.TYPE, SiegePanelPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onSiegePanel(payload)));
        registrar.playToClient(HearthPingPayload.TYPE, HearthPingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onPing(payload)));
        registrar.playToClient(HearthHudPayload.TYPE, HearthHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.hud(payload)));
        registrar.playToClient(HearthTalesPayload.TYPE, HearthTalesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onTales(payload)));
        registrar.playToServer(HearthRequestPayload.TYPE, HearthRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        HearthHooks.onRequest(player, payload);
                    }
                }));
        // Homebuilder (docs/HEARTH-CONTRACT.md, "Payloads"): its news to the client, the wheel's slots back.
        registrar.playToClient(dev.amman.proficiency.homebuilder.net.HomebuilderPayload.TYPE,
                dev.amman.proficiency.homebuilder.net.HomebuilderPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> dev.amman.proficiency.homebuilder.net.HomebuilderNet.client().accept(payload)));
        registrar.playToServer(dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.TYPE,
                dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        dev.amman.proficiency.homebuilder.HomebuilderEvents.onAction(player, payload);
                    }
                }));
    }
}
