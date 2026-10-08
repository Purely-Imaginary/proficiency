package dev.amman.proficiency.fabric;

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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** The payload types on Fabric, and the two server-bound receivers. */
public final class FabricNetwork {

    private FabricNetwork() {
    }

    public static void register() {
        // The mod registers items, so a client without it is refused by registry sync long before
        // any of these channels matter; canSend is still asked before every send, so a window
        // where the channel is not negotiated returns quietly instead of throwing out of a handler.
        // Fabric has no channel version: the sync packet carries the protocol number instead.
        PayloadTypeRegistry.playS2C().register(SyncSkillsPayload.TYPE, SyncSkillsPayload.VERSIONED_CODEC);
        PayloadTypeRegistry.playS2C().register(LevelUpPayload.TYPE, LevelUpPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(XpLogPayload.TYPE, XpLogPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(XpFeedPayload.TYPE, XpFeedPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DiscoveryPayload.TYPE, DiscoveryPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(VisitedPayload.TYPE, VisitedPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CalledShotPayload.TYPE, CalledShotPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DeathRecapPayload.TYPE, DeathRecapPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ProcFxPayload.TYPE, ProcFxPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UnlockPerkPayload.TYPE, UnlockPerkPayload.STREAM_CODEC);
        // Fabric runs play-phase receivers on the server thread already.
        ServerPlayNetworking.registerGlobalReceiver(ActivateAbilityPayload.TYPE,
                (payload, context) -> ProficiencyNetwork.onActivate(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UnlockPerkPayload.TYPE,
                (payload, context) -> ProficiencyNetwork.onUnlock(payload, context.player()));
    }
}
