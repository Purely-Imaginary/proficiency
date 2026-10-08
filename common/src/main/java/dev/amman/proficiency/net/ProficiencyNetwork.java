package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import dev.amman.proficiency.platform.Services;
import dev.amman.proficiency.platform.net.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ProficiencyNetwork {

    private ProficiencyNetwork() {
    }

    /**
     * Network protocol 5. NeoForge negotiates it as the channel version; Fabric has no channel
     * version, so it rides on the sync packet instead ({@link SyncSkillsPayload#VERSIONED_CODEC}).
     * Version 2: DiscoveryPayload gained a trailing string. Version 3: the sync packet carries the
     * running frenzies and the death recap packet exists. Version 4: the proc effect packet. Version 5: Mastery stars (the sync packet
     * carries stars and the overflow bar, the level-up packet a star count).
     */
    public static final int PROTOCOL = 6;

    /** Client side, on the client thread: the server's numbers replace ours. */
    public static void onSync(SyncSkillsPayload payload, Player player) {
        ProficiencyAttachments.of(player).copyFrom(payload.skills());
        dev.amman.proficiency.client.ClientSync.markSynced();
    }

    /** To every client within 48 blocks of the player or of the effect, the player included. */
    public static void sendProcFx(net.minecraft.server.level.ServerLevel level, ServerPlayer player,
            ProcFxPayload payload) {
        double range = 48.0 * 48.0;
        for (ServerPlayer near : level.players()) {
            boolean close = near.distanceToSqr(player) <= range
                    || payload.hasFocus() && near.distanceToSqr(payload.fx(), payload.fy(), payload.fz()) <= range;
            if (close && canReceive(near, ProcFxPayload.TYPE)) {
                PacketDistributor.sendToPlayer(near, payload);
            }
        }
    }

    /** Server thread: the ability key. */
    public static void onActivate(ActivateAbilityPayload payload, ServerPlayer player) {
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
    }

    /** Server thread: a click on a talent node. */
    public static void onUnlock(UnlockPerkPayload payload, ServerPlayer player) {
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
        PacketDistributor.sendToPlayer(player, new LevelUpPayload(skill.ordinal(), level, 0));
    }

    /** A Mastery star: the level-up packet with the star count, so the HUD plays the lighter moment. */
    public static void sendStar(ServerPlayer player, Skill skill, int stars) {
        if (!canReceive(player, LevelUpPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new LevelUpPayload(skill.ordinal(), dev.amman.proficiency.skill.SkillMath.MAX_LEVEL, stars));
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

    /** What the death just wiped, for the recap panel. Skipped when there was nothing to show. */
    public static void sendDeathRecap(ServerPlayer player, DeathRecapPayload payload) {
        if (payload.isEmpty() || !canReceive(player, DeathRecapPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
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
        return player.connection != null && Services.platform().canSend(player, type);
    }
}
