package dev.amman.proficiency.hearth.tales;

import dev.amman.proficiency.hearth.BellRules;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The context bell (docs/HEARTH-SPEC.md, "Gathering"). The stage-V bell of a Hearth, the one in its
 * 7x7 check box, does one thing when rung, picked by the moment ({@link BellRules}): the alarm during a
 * siege or its countdown, the dinner bell within a minute of someone eating, the call home otherwise.
 * Ringing any other bell does nothing special.
 *
 * <p>A ring is recognised from vanilla's own result: a right click on a bell is noted
 * ({@link #noteClick}) with the player's bell-ring statistic, and when that statistic has gone up by
 * the end of the same tick, vanilla rang the bell (proper hit, not sneaking with an item, not
 * cancelled by a claim mod) and the Hearth's call is made. Redstone and arrows ring the bell too but
 * make no call: it is a call someone makes.
 */
public final class HearthBell {

    /** A right click on a bell, waiting for the end of the tick to see whether vanilla rang it. */
    private record Click(UUID player, ServerLevel level, BlockPos pos, int ringsBefore) {
    }

    private static final List<Click> CLICKS = new ArrayList<>();

    /**
     * What the bell's alarm needs from the siege: its living mobs, who is part of it, and the panel.
     * The Sieges lane implements it ({@code SiegeService.siegeMobs / concerned / showPanel}) and sets
     * {@link #siegeLink} from its init. Without one the alarm still works from what this lane can see:
     * mobs tagged {@code SpawnOrigin.SIEGE} in range glow, and every online player in survival or
     * adventure is told and pinged (the client reads the {@code ALARM} ping as "show the siege panel").
     */
    public interface SiegeLink {

        List<Mob> mobs(ServerLevel level, HearthBlockEntity hearth);

        Collection<ServerPlayer> concerned(ServerLevel level, HearthBlockEntity hearth);

        /** Sends the panel for the siege's current phase to these players (the alarm pops it up). */
        void showPanel(ServerLevel level, HearthBlockEntity hearth, Collection<ServerPlayer> players);
    }

    public static volatile SiegeLink siegeLink;

    private HearthBell() {
    }

    // ---- Recognising a ring ----------------------------------------------------------------------

    public static void noteClick(ServerPlayer player, ServerLevel level, BlockPos pos) {
        // A bell far from any Hearth is none of ours: no need to watch it.
        if (HearthRange.houseAt(level, pos) == null) {
            return;
        }
        synchronized (CLICKS) {
            CLICKS.add(new Click(player.getUUID(), level, pos.immutable(), rings(player)));
        }
    }

    /** End of a server tick: every noted click whose bell vanilla rang makes its call. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        List<Click> clicks;
        synchronized (CLICKS) {
            if (CLICKS.isEmpty()) {
                return;
            }
            clicks = new ArrayList<>(CLICKS);
            CLICKS.clear();
        }
        for (Click click : clicks) {
            ServerPlayer player = server.getPlayerList().getPlayer(click.player());
            if (player != null && rings(player) > click.ringsBefore()) {
                ring(player, click.level(), click.pos());
            }
        }
    }

    static void forget() {
        synchronized (CLICKS) {
            CLICKS.clear();
        }
    }

    private static int rings(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.BELL_RING));
    }

    // ---- The call ---------------------------------------------------------------------------------

    /**
     * The bell at {@code bellPos} was rung by {@code ringer}: makes the call the moment asks for, or
     * nothing when the bell is no Hearth's stage-V bell or the call is on its cooldown. Returns what
     * was rung and whether it went off.
     */
    public static BellRules.Ring ring(ServerPlayer ringer, ServerLevel level, BlockPos bellPos) {
        HearthBlockEntity hearth = HearthRange.houseEntityAt(level, bellPos);
        if (hearth == null) {
            return new BellRules.Ring(BellRules.Call.NONE, false);
        }
        BlockPos at = hearth.getBlockPos();
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        long now = level.getGameTime();
        long day = Math.floorDiv(level.getDayTime(), HearthData.TICKS_PER_DAY);
        boolean inBox = BellRules.inBox(bellPos.getX() - at.getX(), bellPos.getY() - at.getY(),
                bellPos.getZ() - at.getZ());
        boolean siege = state.siegePhase() != SiegeRules.Phase.IDLE;
        boolean ate = state.feast().someoneAteWithin(now, data);
        boolean feastDone = state.feast().feastedOn(day) || state.feast().dinnerOpen(now);
        BellRules.Ring ring = BellRules.ring(state.stage(), inBox, siege, ate, feastDone, now, state.lastAlarm(),
                state.lastComeHome(), data);
        if (ring.call() == BellRules.Call.NONE) {
            return ring;
        }
        if (!ring.ready()) {
            ringer.displayClientMessage(TalesText.bellCooldown(), true);
            return ring;
        }
        switch (ring.call()) {
            case ALARM -> alarm(ringer, level, hearth, bellPos, now, data);
            case DINNER -> dinner(ringer, level, hearth, bellPos, now, data);
            case COME_HOME -> comeHome(ringer, level, hearth, bellPos, now, data);
            default -> {
            }
        }
        hearth.setChanged();
        return ring;
    }

    /** Siege mobs in range glow, and everyone who is part of the siege gets the panel and the arrow home. */
    private static void alarm(ServerPlayer ringer, ServerLevel level, HearthBlockEntity hearth, BlockPos bell, long now,
            HearthData data) {
        hearth.state().setLastAlarm(now);
        BlockPos at = hearth.getBlockPos();
        int glow = ticks(data.bell().alarmGlowSeconds());
        HearthGeometry.Range range = HearthRange.range();
        AABB box = new AABB(at.getX() - range.radius(), at.getY() - range.below(), at.getZ() - range.radius(),
                at.getX() + range.radius() + 1, at.getY() + range.above() + 1, at.getZ() + range.radius() + 1);
        Set<Mob> raiders = new LinkedHashSet<>(level.getEntitiesOfClass(Mob.class, box, SpawnOrigin::isSiege));
        SiegeLink link = siegeLink;
        if (link != null) {
            raiders.addAll(link.mobs(level, hearth));
        }
        for (Mob mob : raiders) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, glow, 0, false, false));
        }
        TalesFx.alarm(level, bell);
        ringer.displayClientMessage(TalesText.bellAction("alarm"), true);
        int pingTicks = ticks(data.bell().alarmCooldown());
        Collection<ServerPlayer> audience = link != null ? link.concerned(level, hearth)
                : siegePlayers(level.getServer().getPlayerList().getPlayers());
        for (ServerPlayer player : audience) {
            tellAndPing(player, level, at, BellRules.Call.ALARM, pingTicks, ringer);
        }
        if (link != null) {
            link.showPanel(level, hearth, audience.stream().filter(p -> p.level() == level).toList());
        }
    }

    /** The siege's "everyone" when no siege is there to say: online players in survival or adventure. */
    private static List<ServerPlayer> siegePlayers(List<ServerPlayer> online) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : online) {
            if (partOfTheSiege(player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** The dinner window opens for two minutes and everyone online is told. */
    private static void dinner(ServerPlayer ringer, ServerLevel level, HearthBlockEntity hearth, BlockPos bell, long now,
            HearthData data) {
        hearth.state().feast().openDinner(now, data);
        TalesFx.dinner(level, bell);
        ringer.displayClientMessage(TalesText.bellAction("dinner"), true);
        int pingTicks = ticks(data.feast().dinnerBellSeconds());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            tellAndPing(player, level, hearth.getBlockPos(), BellRules.Call.DINNER, pingTicks, ringer);
        }
    }

    /** Everyone in the dimension sees the Hearth for a minute and may channel Homeward in five seconds; others hear of it in chat. */
    private static void comeHome(ServerPlayer ringer, ServerLevel level, HearthBlockEntity hearth, BlockPos bell,
            long now, HearthData data) {
        hearth.state().setLastComeHome(now);
        TalesFx.comeHome(level, bell);
        ringer.displayClientMessage(TalesText.bellAction("come_home"), true);
        int pingTicks = ticks(data.bell().comeHomeSeconds());
        BlockPos at = hearth.getBlockPos();
        String dimension = level.dimension().location().toString();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            boolean near = tellAndPing(player, level, at, BellRules.Call.COME_HOME, pingTicks, ringer);
            if (near) {
                // "Heard the come-home bell until T": the Homebuilder's Homeward channel reads this.
                HearthAttachments.of(player).heardBell(at.asLong(), dimension, now + pingTicks);
                if (hasCompassBoundTo(player, level, at)) {
                    TalesFx.compass(player);
                    player.displayClientMessage(TalesText.compassHum(), true);
                }
            } else {
                TalesFx.heardFar(player);
            }
        }
    }

    /**
     * Everyone online hears of the call in chat; players in the Hearth's dimension also get the ping
     * (the arrow, the siege panel). Returns whether this player is in that dimension.
     */
    private static boolean tellAndPing(ServerPlayer player, ServerLevel level, BlockPos hearth, BellRules.Call call,
            int ticks, ServerPlayer ringer) {
        boolean near = player.level() == level;
        Component ringerName = Component.literal(HearthTales.name(ringer));
        player.sendSystemMessage(TalesText.bellChat(call.id(), ringerName, !near));
        if (near) {
            HearthNet.sendPing(player, new HearthPingPayload(call.ordinal(), hearth.asLong(),
                    level.dimension().location().toString(), ticks));
        }
        return near;
    }

    /** The siege's "everyone": an online player in survival or adventure. */
    private static boolean partOfTheSiege(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    /** A compass the player carries that is bound to a lodestone at this Hearth (the stage-VI heart, under the fire). */
    public static boolean hasCompassBoundTo(ServerPlayer player, ServerLevel level, BlockPos hearth) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.COMPASS) || !CompassItem.isLodestoneCompass(stack)) {
                continue;
            }
            // 1.20.1 keeps the lodestone in the stack's NBT (LodestonePos, LodestoneDimension); a compass
            // whose lodestone is gone has lost its LodestonePos, so it reads as unbound, as 1.21.1's empty tracker.
            GlobalPos target = CompassItem.getLodestonePosition(stack.getTag());
            if (target == null) {
                continue;
            }
            if (target.dimension() == level.dimension()
                    && Math.abs(target.pos().getX() - hearth.getX()) <= 1
                    && Math.abs(target.pos().getZ() - hearth.getZ()) <= 1
                    && Math.abs(target.pos().getY() - hearth.getY()) <= 2) {
                return true;
            }
        }
        return false;
    }

    private static int ticks(double seconds) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(seconds * 20.0)));
    }
}
