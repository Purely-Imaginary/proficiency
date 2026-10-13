package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.entity.EntityJoinLevelEvent;
import dev.amman.proficiency.platform.event.entity.EntityLeaveLevelEvent;
import dev.amman.proficiency.platform.event.entity.EntityMobGriefingEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.platform.event.entity.player.CanPlayerSleepEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sieges lane's game events on the shared bus, registered from {@link HearthSieges#init}: the
 * play clock, siege mobs dying, loading and griefing, no sleep in range while a siege runs, the panel
 * for a concerned player who logs in, and the relight with flint and steel (a {@code BLOCK_USE}
 * listener).
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
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < 20) {
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

    /** Goals are not saved: a siege mob gets its "go to the Hearth" goal back whenever it is loaded. */
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

    /** Siege mobs (and what they fire) never change blocks, whatever the game rule says. */
    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        if (event.canGrief() && !SiegeMobs.mayGrief(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    /**
     * "Sleeping is refused in range while a siege runs." Also while it is counting down and a bed would
     * sleep through the nightfall that starts it (a bed just before dusk, or a thunderstorm, on the
     * evening it is due): beds cannot stretch the warning any more than shorten it. A bed out of range
     * that skips the dusk anyway costs the siege a postponement ({@link SiegeService}).
     */
    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != null || !ProficiencyConfig.hearthEnabled()) {
            return;
        }
        ServerPlayer player = event.getEntity();
        ServerLevel level = player.serverLevel();
        BlockPos at = HearthRange.hearthAt(level, event.getPos());
        HearthBlockEntity hearth = at == null ? null : HearthRange.entity(level, at);
        if (hearth == null) {
            return;
        }
        HearthState state = hearth.state();
        if (state.siegePhase() == SiegeRules.Phase.ACTIVE) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            SiegeNotices.bar(player, "sleep", ChatFormatting.RED);
        } else if (state.siegePhase() == SiegeRules.Phase.COUNTDOWN
                && SiegeClock.bedSkipsStart(state.siegeDue(), level.getGameTime(), level.getDayTime(), state.nightBefore())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            SiegeNotices.bar(player, "sleep.countdown", ChatFormatting.GOLD);
        }
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
        held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        player.swing(hand, true);
        return InteractionResult.SUCCESS;
    }
}
