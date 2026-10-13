package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.stages.HearthPresence;
import dev.amman.proficiency.platform.Services;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEntityUseItemEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerWakeUpEvent;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.event.server.ServerStartedEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * The game events the Hearth hooks are called from, on the shared bus, plus the Hearth's own
 * plumbing: the data reload, the gain flush, the range cache, the index sweep and carrying the
 * player's Hearth data across a respawn.
 * The two hooks that are not here are called directly: {@code onGrant} from
 * {@code SkillService.grantOnce} and {@code onPlayerSecond} from {@code PlayerLifecycleEvents}.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class HearthCommonEvents {

    /** Mirrored shares go out with the skill sync, every 10 ticks. */
    private static final int GAIN_FLUSH_TICKS = 10;

    private static int ticks;
    private static int seconds;
    private static int sweep;

    private HearthCommonEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HearthRange.refreshRange();
        HearthReload.reload(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HearthReload.check(event.getServer());
        if (++ticks >= GAIN_FLUSH_TICKS) {
            ticks = 0;
            HearthNet.flushGains(event.getServer());
        }
        if (++seconds >= 20) {
            seconds = 0;
            HearthRange.refreshRange();
        }
        if (++sweep >= HearthIndexData.SWEEP_TICKS) {
            sweep = 0;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                HearthIndexData.get(level).prune(level);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HearthReload.forget();
        HearthPresence.forgetAll();
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide) {
            HearthHooks.onIncomingDamage(event);
        }
    }

    /** Lowest, like the break: a placement a claim mod cancels (and reverts) never reaches the lanes. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            HearthHooks.onBlockPlaced(level, event.getPos(), event.getPlacedBlock(), event.getEntity());
        }
    }

    /** Lowest, so a claim mod or another handler that cancels the break has had its say. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer player) {
            HearthHooks.onBlockBroken(level, event.getPos(), event.getState(), player);
        }
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player
                && Services.platform().food(event.getItem(), player) != null) {
            HearthHooks.onItemEaten(player, event.getItem());
        }
    }

    /**
     * Lowest, and not for canceled clicks: every other mod's handler has had its say first, so a claim
     * mod that refuses a non-member's click (FTB Chunks) stops the Hearth hooks too. Fabric runs this
     * priority in a late {@code UseBlockCallback} phase for the same reason ({@code FabricHooks}).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            InteractionResult result = HearthHooks.onBlockUse(player, level, event.getPos(),
                    level.getBlockState(event.getPos()), event.getHand(), event.getItemStack());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        }
    }

    /** A real sleep only: the timer reached 100 before the player got up. */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isSleepingLongEnough()) {
            HearthHooks.onSleep(player);
        }
    }

    /**
     * A new player object: carry the Hearth data across (respawn or leaving the End), then charge
     * what a death costs. The loader does not copy this attachment on death, so this is the one place.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PlayerHearth fresh = HearthAttachments.of(event.getEntity());
        fresh.copyFrom(HearthAttachments.of(event.getOriginal()));
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            double lost = fresh.clearOnDeath();
            HearthHooks.onDeath(player, fresh, lost);
        }
    }

    /**
     * A player who logs in at a Hearth gets its state at once ({@link HearthPresence#joined}): the saved
     * in-range flag keeps a relog at home silent, and the new client still needs the Hearth's numbers.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthPresence.joined(player);
        }
    }

    /** The same for a respawn (after {@link #onClone} carried the data across): a bed by the fire is home. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthPresence.joined(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HearthNet.forget(event.getEntity().getUUID());
        HearthPresence.forget(event.getEntity().getUUID());
    }
}
