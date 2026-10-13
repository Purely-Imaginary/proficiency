package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.SiegeRules;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * The Sieges lane's game events on Forge's bus (common's {@code SiegeEvents}), registered from
 * {@link HearthSieges#init}: the play clock, siege mobs dying, loading and griefing, no sleep in range
 * while a siege runs, the panel for a concerned player who logs in, and the relight with flint and
 * steel (a {@code BLOCK_USE} listener). 1.20.1 has no shared bus, so these are Forge's own events:
 * {@code EntityLeaveLevelEvent} (the removal reason is read off the entity, set before the level stops
 * tracking it), {@code EntityMobGriefingEvent} (a result, not a flag) and {@code PlayerSleepInBedEvent}
 * (asked at the head of {@code ServerPlayer.startSleepInBed}, before vanilla's own checks).
 */
public final class SiegeEvents {

    private static int ticks;

    private SiegeEvents() {
    }

    /**
     * The play clock (docs/HEARTH-SPEC.md, "When a siege can happen"): server time with at least one
     * non-AFK player online, the unit of siege cooldowns, in {@code HearthWorldData}.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20) {
            return;
        }
        ticks = 0;
        MinecraftServer server = event.getServer();
        if (ProficiencyConfig.hearthEnabled() && anyoneActive(server)) {
            HearthWorldData.get(server).addPlayTicks(20);
        }
    }

    static boolean anyoneActive(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        long window = ProficiencyConfig.streakActiveWindowTicks();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && !SkillTools.isFakePlayer(player)
                    && ProficiencyAttachments.of(player).isActive(now, window)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SiegeService.forget();
        ticks = 0;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && SiegeMobs.isSiegeMob(entity)) {
            SiegeService.onSiegeMobDeath(entity, event.getSource());
        }
    }

    /**
     * A siege mob left its level: unloaded with its chunk it still stands; otherwise the siege takes it
     * off its roster (converted, blown up, or gone without a player's hand, see {@link SiegeService#onSiegeMobLeave}).
     */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Mob mob
                && SiegeMobs.isSiegeMob(mob)) {
            SiegeService.onSiegeMobLeave(mob, level);
        }
    }

    /**
     * Goals are not saved: a siege mob gets its "go to the Hearth" goal back whenever it is loaded.
     * ({@code Mob.goalSelector} is public on Forge 1.20.1: Forge's own access transformer opens it.)
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !SiegeMobs.isSiegeMob(mob)) {
            return;
        }
        long hearth = SiegeMobs.hearthOf(mob);
        if (hearth == dev.amman.proficiency.hearth.HearthIndexStore.NONE) {
            return;
        }
        SiegeMobs.disarm(mob);
        if (!SiegeGoal.has(mob)) {
            mob.goalSelector.addGoal(1, new SiegeGoal(mob, BlockPos.of(hearth), SiegeMobs.siegeIdOf(mob)));
        }
    }

    /**
     * Siege mobs (and what they fire) never change blocks, whatever the game rule says. Forge asks this
     * for every per-entity griefing check (explosions, farmland, door breaking, endermen, ...).
     */
    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        if (event.getResult() != Event.Result.DENY && !SiegeMobs.mayGrief(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * "Sleeping is refused in range while a siege runs." Also while it is counting down and a bed would
     * sleep through the nightfall that starts it (a bed just before dusk, or a thunderstorm, on the
     * evening it is due): beds cannot stretch the warning any more than shorten it. A bed out of range
     * that skips the dusk anyway costs the siege a postponement ({@link SiegeService}).
     *
     * <p>Forge asks before vanilla's own checks (night, monsters, distance): a refusal from here shows
     * this line instead of vanilla's. NeoForge's {@code CanPlayerSleepEvent} and Fabric's
     * {@code ALLOW_SLEEPING} (the head of {@code Player.startSleepInBed}, which the server player calls
     * last) both come after those checks and after vanilla sets the respawn point. So a bed refused here
     * still becomes the spawn when vanilla would have made it one ({@link #keepSpawn}), and a player who
     * dies in the siege comes back at that bed, as on the 1.21.1 lines (fix round, 2026-10-10).
     */
    @SubscribeEvent
    public static void onCanSleep(PlayerSleepInBedEvent event) {
        if (event.getResultStatus() != null || !ProficiencyConfig.hearthEnabled()
                || !(event.getEntity() instanceof ServerPlayer player) || event.getPos() == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos at = HearthRange.hearthAt(level, event.getPos());
        HearthBlockEntity hearth = at == null ? null : HearthRange.entity(level, at);
        if (hearth == null) {
            return;
        }
        HearthState state = hearth.state();
        if (state.siegePhase() == SiegeRules.Phase.ACTIVE) {
            keepSpawn(player, event.getPos());
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            SiegeNotices.bar(player, "sleep", ChatFormatting.RED);
        } else if (state.siegePhase() == SiegeRules.Phase.COUNTDOWN
                && SiegeClock.bedSkipsStart(state.siegeDue(), level.getGameTime(), level.getDayTime(), state.nightBefore())) {
            keepSpawn(player, event.getPos());
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            SiegeNotices.bar(player, "sleep.countdown", ChatFormatting.GOLD);
        }
    }

    /**
     * What 1.20.1's {@code ServerPlayer.startSleepInBed} does after this event and before its night and
     * monster checks: the bed becomes the respawn point when the player is awake and alive, the
     * dimension is natural, the bed is within reach (3 blocks across, 2 up or down, either half) and
     * nothing suffocating stands over it. Every other case is one vanilla refuses before the spawn is
     * set, so it is left alone. A bed block without a facing (a modded bed) is left alone, as NeoForge
     * leaves it.
     */
    static void keepSpawn(ServerPlayer player, BlockPos bed) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(bed);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING) || player.isSleeping() || !player.isAlive()
                || !level.dimensionType().natural()) {
            return;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos other = bed.relative(facing.getOpposite());
        if (!reachable(player, bed) && !reachable(player, other)) {
            return;
        }
        BlockPos above = bed.above();
        if (suffocates(level, above) || suffocates(level, above.relative(facing.getOpposite()))) {
            return;
        }
        player.setRespawnPosition(level.dimension(), bed, player.getYRot(), false, true);
    }

    /** {@code ServerPlayer.isReachableBedBlock}. */
    private static boolean reachable(ServerPlayer player, BlockPos pos) {
        Vec3 bottom = Vec3.atBottomCenterOf(pos);
        return Math.abs(player.getX() - bottom.x()) <= 3.0D && Math.abs(player.getY() - bottom.y()) <= 2.0D
                && Math.abs(player.getZ() - bottom.z()) <= 3.0D;
    }

    /** The opposite of {@code Player.freeAt}. */
    private static boolean suffocates(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isSuffocating(level, pos);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ProficiencyConfig.hearthEnabled()) {
            SiegeService.onLogin(player);
        }
    }

    /**
     * A Hearth broken with a siege pending or running: the siege goes with it (leftovers vanish, the
     * concerned players' panels clear). A Hearth lit again later starts with no horn heard.
     */
    static void onBlockBroken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (state.is(HearthBlocks.HEARTH.get()) && level.getBlockEntity(pos) instanceof HearthBlockEntity hearth
                && hearth.state().siegePhase() != SiegeRules.Phase.IDLE) {
            SiegeService.cancelBroken(level, pos, hearth, level.getGameTime());
        }
    }

    /** Flint and steel on a smouldering Hearth relights it; first in line, so no other click on the Hearth takes it. */
    static InteractionResult onBlockUse(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
            InteractionHand hand, ItemStack held) {
        if (!held.is(Items.FLINT_AND_STEEL) || !state.is(HearthBlocks.HEARTH.get())
                || !(level.getBlockEntity(pos) instanceof HearthBlockEntity hearth) || !hearth.state().smouldering()) {
            return InteractionResult.PASS;
        }
        SiegeService.relight(level, pos, hearth, player);
        held.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
        player.swing(hand, true);
        return InteractionResult.SUCCESS;
    }
}
