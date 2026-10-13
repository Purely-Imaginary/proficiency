package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthIndexStore;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The charge lane's own shared-bus listeners (registered from {@link HearthCharge#init}).
 *
 * <p>The kill context: a kill's XP line names only the victim's type ({@code kill|<type>}), but the
 * Hearth must know whether that victim was a siege mob or a raider in an active raid. The kill XP is
 * paid from a {@code LivingDeathEvent} listener at normal priority, so this lane notes the victim at
 * the highest priority and forgets it at the lowest: a kill line paid in between belongs to it.
 * Deaths can nest (a proc that kills a second mob), so the notes are a stack.
 *
 * <p>Both facts are taken here, before any other listener runs: a siege mob counts only for the
 * Hearth whose range it died in, and only while that Hearth is under siege (a siege that ends on
 * its last kill still pays for it). A raider counts only if it was a member of an active raid when
 * it died. {@code Raider.die} leaves the raid before {@code LivingEntity.die} posts the event, so
 * the raid is read from {@link RaidMember}, which {@code HearthRaiderMixin} fills at the head of
 * {@code Raider.die}; a raider merely standing near a raid (a witch farm next to a raid farm) is an
 * ordinary kill.
 */
public final class ChargeEvents {

    /**
     * What a raider was a member of as it began to die. Implemented on {@code Raider} by
     * {@code HearthRaiderMixin} (common, listed in both loaders' {@code proficiency.mixins.json}).
     */
    public interface RaidMember {
        @Nullable
        Raid proficiency$raidAtDeath();
    }

    /**
     * One death being handled: what died, when, and why it might feed the Hearth.
     *
     * @param siegeHearth the packed position of the Hearth under siege whose range a siege mob died
     *                    in, or {@link HearthIndexStore#NONE}
     */
    record Kill(int entityId, String type, long tick, long siegeHearth, boolean raid, Vec3 where,
            @Nullable BlockPos raidCentre) {
    }

    private static final Deque<Kill> KILLS = new ArrayDeque<>();
    private static final int MAX_DEPTH = 32;

    private ChargeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeathFirst(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player) {
            return;
        }
        long now = level.getGameTime();
        // A note an exception left behind is from an earlier tick: drop those.
        while (!KILLS.isEmpty() && KILLS.peekLast().tick() != now) {
            KILLS.pollLast();
        }
        if (KILLS.size() >= MAX_DEPTH) {
            KILLS.clear();
        }
        Raid raid = activeRaid(victim);
        KILLS.push(new Kill(victim.getId(), victim.getType().getDescriptionId(), now, siegeHearth(level, victim),
                raid != null, victim.position(), raid == null ? null : raid.getCenter()));
    }

    /**
     * The Hearth a siege mob died attacking: the one whose range holds it, while that Hearth's siege
     * is running. {@link HearthIndexStore#NONE} for anything else.
     */
    static long siegeHearth(ServerLevel level, LivingEntity victim) {
        if (!SpawnOrigin.isSiege(victim)) {
            return HearthIndexStore.NONE;
        }
        BlockPos at = HearthRange.hearthAt(level, victim.blockPosition());
        HearthBlockEntity hearth = at == null ? null : HearthRange.entity(level, at);
        boolean underSiege = hearth != null && hearth.state().siegePhase() == SiegeRules.Phase.ACTIVE;
        boolean inRange = at != null && HearthRange.inRange(level, at, victim.position());
        return ChargeRules.siegeFeeds(true, underSiege, inRange) ? at.asLong() : HearthIndexStore.NONE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeathLast(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof Player) {
            return;
        }
        while (!KILLS.isEmpty()) {
            Kill top = KILLS.pop();
            if (top.entityId() == victim.getId()) {
                break;
            }
        }
    }

    /** The death being handled right now, if its victim is of this type; null otherwise. */
    @Nullable
    static Kill current(String type, long now) {
        Kill top = KILLS.peek();
        return top != null && top.tick() == now && top.type().equals(type) ? top : null;
    }

    /**
     * The active raid a dying raider is a member of: still set (a raid that never counted it, as in
     * a test), else the one {@code Raider.die} just left. Null for anything else, and never the raid
     * that merely happens to be nearby.
     */
    @Nullable
    static Raid activeRaid(LivingEntity victim) {
        if (!(victim instanceof Raider raider)) {
            return null;
        }
        Raid raid = raider.getCurrentRaid();
        if (raid == null && victim instanceof RaidMember member) {
            raid = member.proficiency$raidAtDeath();
        }
        return raid != null && raid.isActive() && !raid.isOver() ? raid : null;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ChargeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChargeGrants.forget(event.getEntity().getUUID());
        HearthCharge.forget(event.getEntity().getUUID());
    }
}
