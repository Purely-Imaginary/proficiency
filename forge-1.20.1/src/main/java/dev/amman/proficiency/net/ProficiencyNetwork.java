package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
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
import dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload;
import dev.amman.proficiency.homebuilder.net.HomebuilderNet;
import dev.amman.proficiency.homebuilder.net.HomebuilderPayload;
import dev.amman.proficiency.net.codec.StreamCodec;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The mod's one Forge {@link SimpleChannel}, carrying master's payloads with master's own
 * encoders (see {@link StreamCodec}), the Hearth's and Homebuilder's included. NeoForge 1.21 registers each payload as its own channel;
 * Forge 1.20.1 multiplexes them by index over one, so the index order below is the wire format.
 */
public final class ProficiencyNetwork {

    /**
     * Version 2: DiscoveryPayload gained a trailing string. Version 3: the sync packet carries the
     * running frenzies and the death recap packet exists. Version 4: the proc effect packet. Version 5:
     * Mastery stars. Version 6: rested XP. Version 7: the Hearth's payloads (hearth_state, hearth_tracker,
     * hearth_gain, hearth_ledger, siege_panel, hearth_ping, hearth_hud, hearth_tales, hearth_request) and
     * Homebuilder's (homebuilder, homebuilder_action), appended after the older ones, plus the Hearth's
     * block, block entity and items. Version 8: Expertise (the sync packet carries its ranks; the
     * expertise payload, appended last). Version 9: the crit talents and the Leaping Blade synergy
     * in the shared talent table. Version 10: the sync packet carries Seam Sense's Motherlode charge.
     * Version 13: the Jade plugin's look request and reply (this line only: Jade 11 has no server data for
     * a block without a block entity). Version 14: the tempo packet (the HUD's Tempo indicator), appended last.
     * Version 15: the sync packet ends with the picked Codex title (a string, "" for none).
     * The personal records packet (1.37.0) is appended last and carries no number: it is sent only to a peer that has it
     * ({@link #canReceive}), so an older client joins and shows no records.
     * (The milestone packet, the 10/25/50/75/100 ceremonies, is appended last with no version bump: cosmetic, and Forge 1.20.1 is single-player here.)
     * The bonus loot packet (1.38.0) is appended last with no version bump; its sender asks canReceive first.
     * A mismatched pair must fail at negotiation, not in readUtf.
     */
    public static final String PROTOCOL = "15";

    /**
     * Accepts a peer without the channel. Do not read this as "the mod is optional on the
     * client": it is not, the mod registers items and Forge's registry sync refuses a client that
     * lacks them. What it still buys is that a send to a peer without the channel is skipped
     * (see {@link #canReceive}) instead of throwing out of whatever event handler granted XP.
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Proficiency.id("main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private static int index;

    private ProficiencyNetwork() {
    }

    public static void register() {
        toClient(SyncSkillsPayload.class, SyncSkillsPayload.STREAM_CODEC, ProficiencyNetwork::onSync);
        toClient(LevelUpPayload.class, LevelUpPayload.STREAM_CODEC, ProficiencyNetwork::onLevelUp);
        toClient(XpLogPayload.class, XpLogPayload.STREAM_CODEC, ProficiencyNetwork::onXpLog);
        toClient(XpFeedPayload.class, XpFeedPayload.STREAM_CODEC, ProficiencyNetwork::onXpFeed);
        toClient(DiscoveryPayload.class, DiscoveryPayload.STREAM_CODEC, ProficiencyNetwork::onDiscovery);
        toClient(VisitedPayload.class, VisitedPayload.STREAM_CODEC, ProficiencyNetwork::onVisited);
        toClient(CalledShotPayload.class, CalledShotPayload.STREAM_CODEC, ProficiencyNetwork::onCalledShot);
        toClient(DeathRecapPayload.class, DeathRecapPayload.STREAM_CODEC, ProficiencyNetwork::onDeathRecap);
        toClient(ProcFxPayload.class, ProcFxPayload.STREAM_CODEC, ProficiencyNetwork::onProcFx);
        toServer(ActivateAbilityPayload.class, ActivateAbilityPayload.STREAM_CODEC,
                ProficiencyNetwork::onActivate);
        toServer(UnlockPerkPayload.class, UnlockPerkPayload.STREAM_CODEC, ProficiencyNetwork::onUnlock);
        // Protocol 7, the Hearth and Homebuilder (docs/HEARTH-FORGE-CONTRACT.md, "Payloads"). Appended:
        // the index order is the wire format, so the older payloads keep their numbers.
        registerHearth();
        // Protocol 8, Expertise. Appended after the Hearth's, so every older payload keeps its index.
        toServer(ExpertisePayload.class, ExpertisePayload.STREAM_CODEC, ProficiencyNetwork::onExpertise);
        // Protocol 13, the optional Jade plugin's block question and its answer. Appended last.
        toServer(LookRequestPayload.class, LookRequestPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = context.getSender();
            context.enqueueWork(() -> {
                if (player != null) {
                    dev.amman.proficiency.look.LookServer.onRequest(player, payload);
                }
            });
        });
        toClient(LookReplyPayload.class, LookReplyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.amman.proficiency.jade.LookClient.accept(payload)));
        // Protocol 14, the Tempo indicator.
        toClient(TempoPayload.class, TempoPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.amman.proficiency.client.TempoBadge.accept(payload)));
        // The personal records (1.37.0): optional, no protocol bump. Appended last.
        toClient(RecordsPayload.class, RecordsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.amman.proficiency.client.ClientRecords.accept(payload)));
        // The milestone ceremonies (cosmetic, appended last, no protocol bump).
        toClient(MilestonePayload.class, MilestonePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.amman.proficiency.client.MilestoneCeremony.start(
                        Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)], payload.tier(),
                        payload.procUnlock(), payload.abilityUnlock())));
        // Bonus loot flying to the hotbar (1.38.0, no protocol bump). Appended last.
        toClient(BonusLootPayload.class, BonusLootPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.amman.proficiency.client.HudFlights.acceptBonus(payload)));
        // The white Tempo flare of a vanilla jump crit (1.49.0, no protocol bump). Appended last.
        toClient(TempoCritPayload.class, TempoCritPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> dev.amman.proficiency.client.TempoBadge.acceptCrit(payload.skillOrdinal())));
    }

    /**
     * The Hearth's payloads. Client handlers run on the client thread and go to the installed
     * {@code HearthClient} ({@code HearthClients}, a no-op until the client lane sets its own) or to
     * {@code HomebuilderNet.client()}; requests go to {@code HearthHooks.REQUEST} and
     * {@code HomebuilderEvents.onAction} on the server thread.
     */
    private static void registerHearth() {
        toClient(HearthStatePayload.class, HearthStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onState(payload)));
        toClient(HearthTrackerPayload.class, HearthTrackerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onTracker(payload)));
        toClient(HearthGainPayload.class, HearthGainPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onGains(payload)));
        toClient(HearthLedgerPayload.class, HearthLedgerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onLedger(payload)));
        toClient(dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.class,
                dev.amman.proficiency.hearth.net.HearthBuildPlanPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onBuildPlan(payload)));
        toClient(SiegePanelPayload.class, SiegePanelPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onSiegePanel(payload)));
        toClient(HearthPingPayload.class, HearthPingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onPing(payload)));
        toClient(HearthHudPayload.class, HearthHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.hud(payload)));
        toClient(HearthTalesPayload.class, HearthTalesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HearthClients.get().onTales(payload)));
        toServer(HearthRequestPayload.class, HearthRequestPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = context.getSender();
            context.enqueueWork(() -> {
                if (player != null) {
                    HearthHooks.onRequest(player, payload);
                }
            });
        });
        toClient(HomebuilderPayload.class, HomebuilderPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> HomebuilderNet.client().accept(payload)));
        toServer(HomebuilderActionPayload.class, HomebuilderActionPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = context.getSender();
            context.enqueueWork(() -> {
                if (player != null) {
                    dev.amman.proficiency.homebuilder.HomebuilderEvents.onAction(player, payload);
                }
            });
        });
    }

    private static <T> void toClient(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
            BiConsumer<T, NetworkEvent.Context> handler) {
        message(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static <T> void toServer(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
            BiConsumer<T, NetworkEvent.Context> handler) {
        message(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    private static <T> void message(Class<T> type, StreamCodec<FriendlyByteBuf, T> codec,
            BiConsumer<T, NetworkEvent.Context> handler, NetworkDirection direction) {
        CHANNEL.registerMessage(index++, type, (payload, buf) -> codec.encode(buf, payload),
                codec::decode, (payload, context) -> {
                    NetworkEvent.Context ctx = context.get();
                    handler.accept(payload, ctx);
                    ctx.setPacketHandled(true);
                }, Optional.of(direction));
    }

    private static void onSync(SyncSkillsPayload payload, NetworkEvent.Context context) {
        // Client-only class, touched only inside the lambda: a dedicated server never loads it.
        context.enqueueWork(() -> dev.amman.proficiency.client.ClientSync.apply(payload.skills()));
    }

    private static void onLevelUp(LevelUpPayload payload, NetworkEvent.Context context) {
        // The reference to the client class resolves the first time this runs, which is only ever
        // on a client. Keep it that way: nothing client-side may be touched outside this lambda.
        context.enqueueWork(() -> {
            Skill skill = Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)];
            if (payload.stars() > 0) {
                dev.amman.proficiency.client.SkillToasts.star(skill, payload.stars());
                return;
            }
            dev.amman.proficiency.client.SkillToasts.show(skill, payload.level());
        });
    }

    private static void onXpLog(XpLogPayload payload, NetworkEvent.Context context) {
        // Client-only class, touched only inside the lambda, like the toast above.
        context.enqueueWork(() -> dev.amman.proficiency.client.ClientXpLog.accept(payload));
    }

    private static void onXpFeed(XpFeedPayload payload, NetworkEvent.Context context) {
        context.enqueueWork(() -> dev.amman.proficiency.client.XpFeedHud.accept(payload));
    }

    private static void onDiscovery(DiscoveryPayload payload, NetworkEvent.Context context) {
        // Client-only class, touched only inside the lambda, like the toast above.
        context.enqueueWork(() -> {
            if (payload.kind() == DiscoveryPayload.CODEX) {
                int index = payload.skill();
                if (index >= 0 && index < dev.amman.proficiency.skill.Skill.VALUES.length) {
                    dev.amman.proficiency.client.SkillToasts.codex(
                            dev.amman.proficiency.skill.Skill.VALUES[index], Math.round(payload.xp()));
                }
                return;
            }
            dev.amman.proficiency.client.DiscoveryBanner.show(payload);
        });
    }

    private static void onVisited(VisitedPayload payload, NetworkEvent.Context context) {
        context.enqueueWork(() -> dev.amman.proficiency.client.ClientVisited.accept(payload));
    }

    private static void onCalledShot(CalledShotPayload payload, NetworkEvent.Context context) {
        // Client-only class, touched only inside the lambda, like the toast above.
        context.enqueueWork(() -> dev.amman.proficiency.client.CalledShotMarks.accept(payload));
    }

    private static void onDeathRecap(DeathRecapPayload payload, NetworkEvent.Context context) {
        context.enqueueWork(() -> dev.amman.proficiency.client.DeathRecapHud.accept(payload));
    }

    private static void onProcFx(ProcFxPayload payload, NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            dev.amman.proficiency.client.ProcFxPlayer.accept(payload);
            dev.amman.proficiency.client.ProcCallout.accept(payload);
        });
    }

    /** To every client within 48 blocks of the player or of the effect, the player included. */
    public static void sendProcFx(net.minecraft.server.level.ServerLevel level, ServerPlayer player,
            ProcFxPayload payload) {
        double range = 48.0 * 48.0;
        for (ServerPlayer near : level.players()) {
            boolean close = near.distanceToSqr(player) <= range
                    || payload.hasFocus() && near.distanceToSqr(payload.fx(), payload.fy(), payload.fz()) <= range;
            if (close && canReceive(near)) {
                send(near, payload);
            }
        }
    }

    private static void onActivate(ActivateAbilityPayload payload, NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            int ordinal = payload.skillOrdinal();
            if (ordinal < 0 || ordinal >= Skill.VALUES.length) {
                return;
            }
            Skill skill = Skill.VALUES[ordinal];
            var refusal = dev.amman.proficiency.skill.ActiveService.activate(player, skill);
            if (refusal != null) {
                player.displayClientMessage(refusal, true);
            } else {
                sendFullSync(player);
            }
        });
    }

    private static void onUnlock(UnlockPerkPayload payload, NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            var talent = dev.amman.proficiency.perk.Talents.byKey(payload.perkKey());
            if (talent == null) {
                return;
            }
            var attempt = dev.amman.proficiency.perk.TalentService.tryInvest(player, talent);
            // A rank that went in is shown by the tree itself; only a refusal needs words, and on
            // the action bar so clicking five ranks does not flood chat.
            if (attempt.outcome() == dev.amman.proficiency.perk.TalentOutcome.MISSING_MATERIALS) {
                player.sendSystemMessage(attempt.message());
            } else {
                player.displayClientMessage(attempt.message(), true);
            }
            if (attempt.ok()) {
                sendFullSync(player);
            }
        });
    }

    private static void onExpertise(ExpertisePayload payload, NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            dev.amman.proficiency.perk.TalentService.Attempt attempt;
            if (payload.action() == ExpertisePayload.RESPEC) {
                attempt = dev.amman.proficiency.perk.ExpertiseService.respec(player);
                player.sendSystemMessage(attempt.message());
            } else {
                var node = dev.amman.proficiency.perk.Expertise.byId(payload.node());
                if (node == null) {
                    return;
                }
                attempt = dev.amman.proficiency.perk.ExpertiseService.tryInvest(player, node);
                player.displayClientMessage(attempt.message(), true);
            }
            if (attempt.ok()) {
                sendFullSync(player);
            }
        });
    }

    /** Client to server: the ability key and the wheel. */
    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    /**
     * Server to one client, skipped when that client lacks the channel (or is a GameTest mock player):
     * the Hearth's and Homebuilder's sends go through here.
     */
    public static void sendIfPresent(ServerPlayer player, Object payload) {
        if (canReceive(player)) {
            send(player, payload);
        }
    }

    private static void send(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendFullSync(ServerPlayer player) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new SyncSkillsPayload(ProficiencyAttachments.of(player)));
    }

    /** The Tempo chain of the skill the last action fed; the caller decides when ({@code TempoSync}). */
    public static void sendTempo(ServerPlayer player, Skill skill, int steps, int maxSteps, int permille,
            int windowTicks) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new TempoPayload(skill.ordinal(), steps, maxSteps, permille, windowTicks));
    }

    /** Personal records: the given rows and an optional toast. See {@link RecordsPayload}. */
    public static void sendRecords(ServerPlayer player, java.util.List<RecordsPayload.Row> rows, int toastSkill,
            int toastMetric, float toastValue) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new RecordsPayload(rows, toastSkill, toastMetric, toastValue));
    }

    /** A vanilla jump crit lengthened a weapon chain's window (no protocol bump). */
    public static void sendTempoCrit(ServerPlayer player, Skill skill) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new TempoCritPayload(skill.ordinal()));
    }

    /** The bonus items a skill's luck added this tick (no protocol bump); a client without the channel simply sees no flight. */
    public static void sendBonusLoot(ServerPlayer player, BonusLootPayload payload) {
        if (payload.entries().isEmpty() || !canReceive(player)) {
            return;
        }
        send(player, payload);
    }

    public static void sendLevelUp(ServerPlayer player, Skill skill, int level) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new LevelUpPayload(skill.ordinal(), level, 0));
    }

    /** A milestone ceremony (tier 10, 25, 50, 75 or 100), sent after the level-up packet. */
    public static void sendMilestone(ServerPlayer player, Skill skill, int tier) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new MilestonePayload(skill.ordinal(), tier,
                dev.amman.proficiency.config.ProficiencyConfig.procUnlockLevel(),
                dev.amman.proficiency.skill.ActiveService.unlockLevel()));
    }

    /** A Mastery star: the level-up packet with the star count, so the HUD plays the lighter moment. */
    public static void sendStar(ServerPlayer player, Skill skill, int stars) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new LevelUpPayload(skill.ordinal(), dev.amman.proficiency.skill.SkillMath.MAX_LEVEL, stars));
    }

    /** What the death just wiped, for the recap panel. Skipped when there was nothing to show. */
    public static void sendDeathRecap(ServerPlayer player, DeathRecapPayload payload) {
        if (payload.isEmpty() || !canReceive(player)) {
            return;
        }
        send(player, payload);
    }

    /** The debug feed's gains since the last sync tick, if the player has it on and earned any. */
    public static void sendXpFeed(ServerPlayer player) {
        var gains = dev.amman.proficiency.skill.SkillService.drainXpFeed(player);
        if (gains.isEmpty() || !canReceive(player)) {
            return;
        }
        send(player, new XpFeedPayload(java.util.List.copyOf(gains)));
    }

    /** The zone banner. A client without the channel simply sees nothing, and still gets the XP. */
    public static void sendDiscovery(ServerPlayer player, int kind, String name, String id,
            dev.amman.proficiency.skill.Skill skill, float xp) {
        sendDiscovery(player, kind, name, id, skill, xp, "");
    }

    /** As above, with the name of the player who led, for a convoy member's shared discovery. */
    public static void sendDiscovery(ServerPlayer player, int kind, String name, String id,
            dev.amman.proficiency.skill.Skill skill, float xp, String with) {
        if (kind == DiscoveryPayload.FIRST || kind == DiscoveryPayload.BIOME || kind == DiscoveryPayload.DIMENSION
                || kind == DiscoveryPayload.STRUCTURE) {
            dev.amman.proficiency.haul.HaulService.onFound(player);
        }
        if (!canReceive(player)) {
            return;
        }
        send(player,
                new DiscoveryPayload(kind, name, id, skill.ordinal(), xp, with));
    }

    /** Co-op XP streams (1.45.0): on their own optional channel, to a client that has it ({@link CoopNetwork}). */
    public static void sendCoop(ServerPlayer player, dev.amman.proficiency.coop.CoopBatch batch) {
        CoopNetwork.send(player, batch);
    }

    /** Tactician's Called Shot icon over a mob: {@code ticks} left, or 0 when the mark ended. */
    public static void sendCalledShot(ServerPlayer player, int entityId, int ticks, String marker) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new CalledShotPayload(entityId, ticks, marker));
    }

    /** The recent-XP lines of the skills that changed since the last push. Called from the sync tick. */
    public static void sendXpLogIfChanged(ServerPlayer player) {
        var log = dev.amman.proficiency.skill.SkillService.xpLog(player);
        if (log == null) {
            return;
        }
        java.util.List<Integer> changed = log.takeDirty();
        if (changed.isEmpty() || !canReceive(player)) {
            return;
        }
        send(player,
                XpLogPayload.of(log, changed, System.currentTimeMillis()));
    }

    /** Longest key list in one message; a full send of a big set is cut into several. */
    private static final int VISITED_CHUNK = 1500;

    /** What each online player's client already holds of the visited set. Server thread only. */
    private static final java.util.Map<java.util.UUID, VisitedDelta> VISITED_SENT =
            new java.util.HashMap<>();

    /**
     * The journal's visited set: everything on the first call for a player, then only additions.
     * Called from the sync tick and at login. A full send also carries the structure and dimension
     * lists the client cannot get from its own registries.
     */
    public static void sendVisitedIfChanged(ServerPlayer player) {
        if (!canReceive(player)) {
            return;
        }
        var change = VISITED_SENT.computeIfAbsent(player.getUUID(), id -> new VisitedDelta())
                .next(ProficiencyAttachments.of(player).visitedKeys());
        if (change == null) {
            return;
        }
        java.util.List<String> structures = java.util.List.of();
        java.util.List<String> dimensions = java.util.List.of();
        var server = player.getServer();
        if (change.full() && server != null) {
            structures = server.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).keySet().stream()
                    .map(id -> dev.amman.proficiency.event.StructureIds.canonical(id.toString()))
                    .distinct().sorted().toList();
            dimensions = server.levelKeys().stream()
                    .map(key -> key.location().toString()).sorted().toList();
        }
        var keys = change.keys();
        int from = 0;
        do {
            int to = Math.min(keys.size(), from + VISITED_CHUNK);
            boolean first = from == 0;
            send(player, new VisitedPayload(change.full() && first,
                    java.util.List.copyOf(keys.subList(from, to)),
                    first ? structures : java.util.List.of(),
                    first ? dimensions : java.util.List.of()));
            from = to;
        } while (from < keys.size());
    }

    /** Forget what the client has, so the next send is a full one. Login and logout call this. */
    public static void forgetVisited(java.util.UUID player) {
        VISITED_SENT.remove(player);
    }

    /**
     * Sending a payload the far end never negotiated throws, which would tear the exception straight
     * out of whatever event handler was granting the XP. Ask first.
     */
    public static boolean canReceive(ServerPlayer player) {
        // A GameTest mock player's connection has no netty channel at all.
        return player.connection != null && player.connection.connection.channel() != null
                && CHANNEL.isRemotePresent(player.connection.connection);
    }
}
