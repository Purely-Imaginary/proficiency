package dev.amman.proficiency.client;

import dev.amman.proficiency.item.FriendCompassItem;
import dev.amman.proficiency.net.CalledShotPayload;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.LevelUpPayload;
import dev.amman.proficiency.net.ProcFxPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.net.SyncSkillsPayload;
import dev.amman.proficiency.net.VisitedPayload;
import dev.amman.proficiency.net.XpFeedPayload;
import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.List;
import java.util.UUID;

/**
 * The client wiring both 1.21.1 loaders share. Each loader's client setup registers these with its
 * own API: the key mappings, a receiver per server-to-client payload, the compasses' needle
 * property and the leave-world reset. Every method runs on the client thread.
 */
public final class ClientWiring {

    /** Vanilla's compass needle property; both compasses feed it their own target. */
    public static final ResourceLocation ANGLE = ResourceLocation.withDefaultNamespace("angle");

    private ClientWiring() {
    }

    public static List<KeyMapping> keyMappings() {
        return List.of(ProficiencyClient.OPEN_SKILLS, ProficiencyClient.USE_ABILITY);
    }

    // ---- Server-to-client payloads ------------------------------------------------------------

    public static void onSync(SyncSkillsPayload payload, Player player) {
        ProficiencyNetwork.onSync(payload, player);
    }

    public static void onLevelUp(LevelUpPayload payload) {
        Skill skill = Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)];
        if (payload.stars() > 0) {
            SkillToasts.star(skill, payload.stars());
            return;
        }
        SkillToasts.show(skill, payload.level());
    }

    public static void onXpLog(XpLogPayload payload) {
        ClientXpLog.accept(payload);
    }

    public static void onXpFeed(XpFeedPayload payload) {
        XpFeedHud.accept(payload);
    }

    public static void onDiscovery(DiscoveryPayload payload) {
        DiscoveryBanner.show(payload);
    }

    public static void onVisited(VisitedPayload payload) {
        ClientVisited.accept(payload);
    }

    public static void onCalledShot(CalledShotPayload payload) {
        CalledShotMarks.accept(payload);
    }

    public static void onDeathRecap(DeathRecapPayload payload) {
        DeathRecapHud.accept(payload);
    }

    public static void onProcFx(ProcFxPayload payload) {
        ProcFxPlayer.accept(payload);
    }

    /**
     * Leaving a world. The recent-XP list is per session on the server; leaving a world ends it here
     * too, or the next server would open on the last one's lines. No Called Shot mark or queued
     * effect carries over either.
     */
    public static void onLeaveWorld() {
        ClientXpLog.clear();
        XpFeedHud.clear();
        DiscoveryBanner.clear();
        ClientVisited.clear();
        CalledShotMarks.clear();
        ProcFxPlayer.clear();
        HudState.reset();
    }

    // ---- Compass needles ----------------------------------------------------------------------

    /** Vanilla's needle maths, fed from the stack's lodestone tracker instead of a lodestone. */
    public static CompassItemPropertyFunction forestersCompassNeedle() {
        return new CompassItemPropertyFunction((level, stack, entity) -> {
            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            return tracker == null ? null : tracker.target().orElse(null);
        });
    }

    /**
     * The friend's live position when this client can see them (inside entity-tracking range), else
     * the server's coarse copy in the lodestone tracker. See {@link FriendCompassItem}.
     */
    public static CompassItemPropertyFunction friendCompassNeedle() {
        return new CompassItemPropertyFunction((level, stack, entity) -> {
            UUID id = FriendCompassItem.friend(stack);
            if (id != null && level != null) {
                Player friend = level.getPlayerByUUID(id);
                if (friend != null) {
                    return GlobalPos.of(level.dimension(), friend.blockPosition());
                }
            }
            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            return tracker == null ? null : tracker.target().orElse(null);
        });
    }
}
