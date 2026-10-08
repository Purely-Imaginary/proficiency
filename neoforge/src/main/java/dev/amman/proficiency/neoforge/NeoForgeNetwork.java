package dev.amman.proficiency.neoforge;

import dev.amman.proficiency.net.ActivateAbilityPayload;
import dev.amman.proficiency.net.CalledShotPayload;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.LevelUpPayload;
import dev.amman.proficiency.net.ProcFxPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.net.SyncSkillsPayload;
import dev.amman.proficiency.net.UnlockPerkPayload;
import dev.amman.proficiency.net.VisitedPayload;
import dev.amman.proficiency.net.XpFeedPayload;
import dev.amman.proficiency.net.XpLogPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The payloads on NeoForge. Client handlers run inside {@code enqueueWork} and reference the client
 * classes only there, so a dedicated server never resolves them.
 */
public final class NeoForgeNetwork {

    private NeoForgeNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // The channel version is the protocol (ProficiencyNetwork.PROTOCOL): a mismatched pair fails
        // at negotiation, not in readUtf.
        // Optional channels, but do not read this as "the mod is optional on the client". It is
        // not: the mod registers items, and vanilla registry sync rejects a client that cannot
        // resolve proficiency:foresters_compass, long before any of these channels matter. Tested,
        // not assumed. What the optional flag still buys is that a send during a window where the
        // channel is not negotiated returns false instead of throwing out of an event handler.
        PayloadRegistrar registrar = event.registrar(Integer.toString(ProficiencyNetwork.PROTOCOL)).optional();
        registrar.playToClient(SyncSkillsPayload.TYPE, SyncSkillsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onSync(payload, context.player())));
        registrar.playToClient(LevelUpPayload.TYPE, LevelUpPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onLevelUp(payload)));
        registrar.playToClient(XpLogPayload.TYPE, XpLogPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onXpLog(payload)));
        registrar.playToClient(XpFeedPayload.TYPE, XpFeedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onXpFeed(payload)));
        registrar.playToClient(DiscoveryPayload.TYPE, DiscoveryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onDiscovery(payload)));
        registrar.playToClient(VisitedPayload.TYPE, VisitedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onVisited(payload)));
        registrar.playToClient(CalledShotPayload.TYPE, CalledShotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onCalledShot(payload)));
        registrar.playToClient(DeathRecapPayload.TYPE, DeathRecapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onDeathRecap(payload)));
        registrar.playToClient(ProcFxPayload.TYPE, ProcFxPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.amman.proficiency.client.ClientWiring.onProcFx(payload)));
        registrar.playToServer(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ProficiencyNetwork.onActivate(payload, player);
                    }
                }));
        registrar.playToServer(UnlockPerkPayload.TYPE, UnlockPerkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ProficiencyNetwork.onUnlock(payload, player);
                    }
                }));
    }
}
