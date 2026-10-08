package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
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
 * The mod's one Forge {@link SimpleChannel}, carrying master's nine payloads with master's own
 * encoders (see {@link StreamCodec}). NeoForge 1.21 registers each payload as its own channel;
 * Forge 1.20.1 multiplexes them by index over one, so the index order below is the wire format.
 */
public final class ProficiencyNetwork {

    /**
     * Version 2: DiscoveryPayload gained a trailing string. Version 3: the sync packet carries the
     * running frenzies and the death recap packet exists. Version 4: the proc effect packet. A mismatched pair must fail at
     * negotiation, not in readUtf.
     */
    private static final String PROTOCOL = "6";

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
        context.enqueueWork(() -> dev.amman.proficiency.client.DiscoveryBanner.show(payload));
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
        context.enqueueWork(() -> dev.amman.proficiency.client.ProcFxPlayer.accept(payload));
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

    /** Client to server: the ability key and the wheel. */
    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
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

    public static void sendLevelUp(ServerPlayer player, Skill skill, int level) {
        if (!canReceive(player)) {
            return;
        }
        send(player, new LevelUpPayload(skill.ordinal(), level, 0));
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
        if (!canReceive(player)) {
            return;
        }
        send(player,
                new DiscoveryPayload(kind, name, id, skill.ordinal(), xp, with));
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
    private static boolean canReceive(ServerPlayer player) {
        // A GameTest mock player's connection has no netty channel at all.
        return player.connection != null && player.connection.connection.channel() != null
                && CHANNEL.isRemotePresent(player.connection.connection);
    }
}
