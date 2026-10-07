package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ProficiencyNetwork {

    private ProficiencyNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Version 2: DiscoveryPayload gained a trailing string. A mismatched pair must fail at
        // negotiation, not in readUtf.
        // Optional channels, but do not read this as "the mod is optional on the client". It is
        // not: the mod registers items, and vanilla registry sync rejects a client that cannot
        // resolve proficiency:foresters_compass, long before any of these channels matter. Tested,
        // not assumed. What the optional flag still buys is that a send during a window where the
        // channel is not negotiated returns false instead of throwing out of an event handler.
        PayloadRegistrar registrar = event.registrar("2").optional();
        registrar.playToClient(
                SyncSkillsPayload.TYPE, SyncSkillsPayload.STREAM_CODEC, ProficiencyNetwork::onSync);
        registrar.playToClient(
                LevelUpPayload.TYPE, LevelUpPayload.STREAM_CODEC, ProficiencyNetwork::onLevelUp);
        registrar.playToClient(
                XpLogPayload.TYPE, XpLogPayload.STREAM_CODEC, ProficiencyNetwork::onXpLog);
        registrar.playToClient(
                XpFeedPayload.TYPE, XpFeedPayload.STREAM_CODEC, ProficiencyNetwork::onXpFeed);
        registrar.playToClient(
                DiscoveryPayload.TYPE, DiscoveryPayload.STREAM_CODEC, ProficiencyNetwork::onDiscovery);
        registrar.playToClient(
                VisitedPayload.TYPE, VisitedPayload.STREAM_CODEC, ProficiencyNetwork::onVisited);
        registrar.playToClient(
                CalledShotPayload.TYPE, CalledShotPayload.STREAM_CODEC, ProficiencyNetwork::onCalledShot);
        registrar.playToServer(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.STREAM_CODEC,
                ProficiencyNetwork::onActivate);
        registrar.playToServer(UnlockPerkPayload.TYPE, UnlockPerkPayload.STREAM_CODEC,
                ProficiencyNetwork::onUnlock);
    }

    private static void onSync(SyncSkillsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            player.getData(ProficiencyAttachments.SKILLS.get()).copyFrom(payload.skills());
        });
    }

    private static void onLevelUp(LevelUpPayload payload, IPayloadContext context) {
        // The reference to the client class resolves the first time this runs, which is only ever
        // on a client. Keep it that way: nothing client-side may be touched outside this lambda.
        context.enqueueWork(() -> {
            Skill skill = Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)];
            dev.amman.proficiency.client.SkillToasts.show(skill, payload.level());
        });
    }

    private static void onXpLog(XpLogPayload payload, IPayloadContext context) {
        // Client-only class, touched only inside the lambda, like the toast above.
        context.enqueueWork(() -> dev.amman.proficiency.client.ClientXpLog.accept(payload));
    }

    private static void onXpFeed(XpFeedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.amman.proficiency.client.XpFeedHud.accept(payload));
    }

    private static void onDiscovery(DiscoveryPayload payload, IPayloadContext context) {
        // Client-only class, touched only inside the lambda, like the toast above.
        context.enqueueWork(() -> dev.amman.proficiency.client.DiscoveryBanner.show(payload));
    }

    private static void onVisited(VisitedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.amman.proficiency.client.ClientVisited.accept(payload));
    }

    private static void onCalledShot(CalledShotPayload payload, IPayloadContext context) {
        // Client-only class, touched only inside the lambda, like the toast above.
        context.enqueueWork(() -> dev.amman.proficiency.client.CalledShotMarks.accept(payload));
    }

    private static void onActivate(ActivateAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
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

    private static void onUnlock(UnlockPerkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var talent = dev.amman.proficiency.perk.Talents.byKey(payload.perkKey());
            if (talent == null) {
                return;
            }
            var attempt = dev.amman.proficiency.perk.TalentService.tryInvest(player, talent);
            // A rank that went in is shown by the tree itself; only a refusal needs words, and on
            // the action bar so clicking five ranks does not flood chat.
            if (attempt.outcome() == dev.amman.proficiency.perk.TalentService.Outcome.MISSING_MATERIALS) {
                player.sendSystemMessage(attempt.message());
            } else {
                player.displayClientMessage(attempt.message(), true);
            }
            if (attempt.ok()) {
                sendFullSync(player);
            }
        });
    }

    public static void sendFullSync(ServerPlayer player) {
        if (!canReceive(player, SyncSkillsPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player, new SyncSkillsPayload(ProficiencyAttachments.of(player)));
    }

    public static void sendLevelUp(ServerPlayer player, Skill skill, int level) {
        if (!canReceive(player, LevelUpPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new LevelUpPayload(skill.ordinal(), level));
    }

    /** The debug feed's gains since the last sync tick, if the player has it on and earned any. */
    public static void sendXpFeed(ServerPlayer player) {
        var gains = dev.amman.proficiency.skill.SkillService.drainXpFeed(player);
        if (gains.isEmpty() || !canReceive(player, XpFeedPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new XpFeedPayload(java.util.List.copyOf(gains)));
    }

    /** The zone banner. A client without the channel simply sees nothing, and still gets the XP. */
    public static void sendDiscovery(ServerPlayer player, int kind, String name, String id,
            dev.amman.proficiency.skill.Skill skill, float xp) {
        sendDiscovery(player, kind, name, id, skill, xp, "");
    }

    /** As above, with the name of the player who led, for a convoy member's shared discovery. */
    public static void sendDiscovery(ServerPlayer player, int kind, String name, String id,
            dev.amman.proficiency.skill.Skill skill, float xp, String with) {
        if (!canReceive(player, DiscoveryPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new DiscoveryPayload(kind, name, id, skill.ordinal(), xp, with));
    }

    /** Tactician's Called Shot icon over a mob: {@code ticks} left, or 0 when the mark ended. */
    public static void sendCalledShot(ServerPlayer player, int entityId, int ticks, String marker) {
        if (!canReceive(player, CalledShotPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new CalledShotPayload(entityId, ticks, marker));
    }

    /** The recent-XP lines of the skills that changed since the last push. Called from the sync tick. */
    public static void sendXpLogIfChanged(ServerPlayer player) {
        var log = dev.amman.proficiency.skill.SkillService.xpLog(player);
        if (log == null) {
            return;
        }
        java.util.List<Integer> changed = log.takeDirty();
        if (changed.isEmpty() || !canReceive(player, XpLogPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
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
        if (!canReceive(player, VisitedPayload.TYPE)) {
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
            PacketDistributor.sendToPlayer(player, new VisitedPayload(change.full() && first,
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
    private static boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null
                && NetworkRegistry.hasChannel(player.connection, type.id());
    }
}
