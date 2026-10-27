package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.platform.Services;
import dev.amman.proficiency.platform.net.PacketDistributor;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side sends of the Hearth payloads. Every send first asks whether the client negotiated
 * the channel (sending one it did not throws out of whatever handler is running). Mirrored shares
 * are buffered per player and flushed with the skill sync cadence (every 10 ticks), one packet per
 * player that gained anything.
 */
public final class HearthNet {

    private static final Map<UUID, List<HearthGainPayload.Gain>> GAINS = new ConcurrentHashMap<>();

    private HearthNet() {
    }

    public static boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && Services.platform().canSend(player, type);
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (canReceive(player, payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendState(ServerPlayer player, HearthStatePayload payload) {
        send(player, payload);
    }

    public static void sendTracker(ServerPlayer player, HearthTrackerPayload payload) {
        send(player, payload);
    }

    public static void sendLedger(ServerPlayer player, HearthLedgerPayload payload) {
        send(player, payload);
    }

    public static void sendBuildPlan(ServerPlayer player, HearthBuildPlanPayload payload) {
        send(player, payload);
    }

    public static void sendSiegePanel(ServerPlayer player, SiegePanelPayload payload) {
        send(player, payload);
    }

    public static void sendPing(ServerPlayer player, HearthPingPayload payload) {
        send(player, payload);
    }

    public static void sendHud(ServerPlayer player, HearthHudPayload payload) {
        send(player, payload);
    }

    /** A mirrored share for the tracker's dots; sent with the next sync tick. {@code skill} null for none. */
    public static void queueGain(ServerPlayer player, HearthPool pool, float amount, @Nullable Skill skill) {
        if (!(amount > 0) || Float.isInfinite(amount)) {
            return;
        }
        List<HearthGainPayload.Gain> gains = GAINS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        if (gains.size() < HearthGainPayload.MAX) {
            gains.add(new HearthGainPayload.Gain(pool.ordinal(), amount, skill == null ? -1 : skill.ordinal()));
        }
    }

    /** Sends and clears every buffered share. Called every 10 ticks. */
    public static void flushGains(MinecraftServer server) {
        if (GAINS.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<HearthGainPayload.Gain> gains = GAINS.remove(player.getUUID());
            if (gains != null && !gains.isEmpty()) {
                send(player, new HearthGainPayload(List.copyOf(gains)));
            }
        }
        // Whoever left between two flushes.
        GAINS.clear();
    }

    public static void forget(UUID player) {
        GAINS.remove(player);
        HUD_DIRTY.remove(player);
    }

    // ---- Shared builders ------------------------------------------------------------------------

    /**
     * The state payload of a Hearth, built one way for every lane that sends it (Stages on entering
     * range and panel open, Charge on a tier change, Comfort after a scan).
     */
    public static HearthStatePayload stateOf(HearthBlockEntity hearth, int reason) {
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        int ceiling = state.ceiling(data);
        long playNow = 0L;
        String dimension = "";
        if (hearth.getLevel() instanceof ServerLevel level) {
            playNow = HearthWorldData.get(level.getServer()).playTicks();
            dimension = level.dimension().location().toString();
        }
        float hours = state.lastSiegeEndPlay() == HearthState.NEVER ? -1f
                : (float) state.playHoursSinceSiege(playNow);
        HearthGeometry.Range range = hearth.getLevel() instanceof ServerLevel own
                ? HearthRange.cylinder(own, hearth.getBlockPos()) : HearthRange.range();
        return new HearthStatePayload(reason, hearth.packedPos(), dimension, state.stage(), ceiling,
                state.smouldering(), state.comfort(), List.copyOf(state.comfortKinds()), state.character(),
                ceiling < HearthData.TIERS ? data.character().forStage(ceiling + 1) : 0,
                PoolRow.of(state, data), state.defenceStreak(), state.sieged(), state.siegePhase().ordinal(),
                hours, (float) range.radius(), range.below(), range.above(),
                HearthStatePayload.Extra.of(state, data, dev.amman.proficiency.config.ProficiencyConfig.hearthMinSpacing()),
                dev.amman.proficiency.hearth.stages.HearthAutoBuild.status(hearth));
    }

    /**
     * The panel for a plain campfire ({@link HearthStatePayload#CAMPFIRE}): stage 0, a fresh state, the live
     * data's numbers. Nothing is created or saved.
     */
    public static HearthStatePayload campfireState(ServerLevel level, net.minecraft.core.BlockPos pos) {
        HearthData data = Hearths.data();
        HearthState state = new HearthState();
        HearthGeometry.Range range = HearthRange.range();
        return new HearthStatePayload(HearthStatePayload.CAMPFIRE, pos.asLong(), level.dimension().location().toString(),
                0, 0, false, 0, List.of(), 0, data.character().forStage(1), PoolRow.of(state, data), 0, false, 0, -1f,
                (float) range.radius(), range.below(), range.above(),
                HearthStatePayload.Extra.of(state, data, dev.amman.proficiency.config.ProficiencyConfig.hearthMinSpacing()));
    }

    // ---- The HUD payload has two writers ---------------------------------------------------------

    private static final java.util.Set<UUID> HUD_DIRTY = ConcurrentHashMap.newKeySet();

    /**
     * Something the HUD shows changed (Tales banked or paid, say). The Effects lane owns sending
     * {@link HearthHudPayload}; it calls {@link #takeHudDirty} in its per-player second and sends when
     * this was set, so two lanes never build the payload two ways.
     */
    public static void markHudDirty(ServerPlayer player) {
        HUD_DIRTY.add(player.getUUID());
    }

    /** Whether the HUD needs a send; clears the mark. */
    public static boolean takeHudDirty(ServerPlayer player) {
        return HUD_DIRTY.remove(player.getUUID());
    }
}
