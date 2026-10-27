package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import dev.amman.proficiency.event.PlayerLifecycleEvents;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.stages.HearthBreaking;
import dev.amman.proficiency.hearth.stages.HearthPresence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The game events the Hearth hooks are called from, on Forge's own bus (common's
 * {@code HearthCommonEvents}; 1.20.1 has no shared bus, so these are Forge's events), plus the
 * Hearth's own plumbing: the data reload, the gain flush, the range cache, the index sweep, the
 * sneak-hold count ({@code stages.HearthBreaking}), carrying the player's Hearth data across a respawn,
 * and forgetting a player's per-session entries on logout.
 * The two hooks that are not here are called directly: {@code onGrant} from
 * {@code SkillService.grantOnce} and {@code onPlayerSecond} from {@code PlayerLifecycleEvents}. The
 * request hook is called from {@code ProficiencyNetwork} when a {@code hearth_request} arrives.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
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
        dev.amman.proficiency.hearth.stages.HearthChunks.restore(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        HearthReload.check(event.getServer());
        HearthBreaking.tickHolds(event.getServer());
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
        dev.amman.proficiency.hearth.stages.HearthChunks.forgetAll();
        HearthBreaking.forgetAll();
    }

    /**
     * The port's incoming-damage event ({@code compat.LivingIncomingDamageEvent}): the same spot in
     * {@code hurt} as NeoForge's, so a listener that changes the amount changes the hit before armour.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide) {
            HearthHooks.onIncomingDamage(event);
        }
    }

    /**
     * Lowest, like the break: a placement a claim mod cancels (and reverts) never reaches the lanes.
     * Forge posts a multi-block placement (a bed, a door) as its subclass {@code EntityMultiPlaceEvent},
     * which arrives here once, at the clicked position, as on NeoForge.
     */
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

    /** Food only: what the stack would feed this player ({@code IForgeItemStack.getFoodProperties}). */
    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().getFoodProperties(player) != null) {
            HearthHooks.onItemEaten(player, event.getItem());
        }
    }

    /**
     * Lowest, and not for canceled clicks (Forge's default): every other mod's handler has had its say
     * first, so a claim mod that refuses a non-member's click (FTB Chunks) stops the Hearth hooks too.
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

    /**
     * A real sleep only: the timer reached 100 before the player got up. Forge posts its wake-up event
     * at the head of {@code Player.stopSleepInBed}, while the player still sleeps, as NeoForge does.
     */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isSleepingLongEnough()) {
            HearthHooks.onSleep(player);
        }
    }

    /**
     * A new player object: carry the Hearth data across (respawn or leaving the End), then charge
     * what a death costs. The old body's data is read through {@link PlayerLifecycleEvents#readOldCap}
     * (Forge invalidates its capabilities before this event).
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PlayerHearth carried = PlayerLifecycleEvents.readOldCap(event, HearthAttachments.HEARTH);
        if (carried == null) {
            Proficiency.LOG.error("No Hearth data on the old body of {}; nothing carried",
                    event.getEntity().getGameProfile().getName());
            return;
        }
        PlayerHearth fresh = HearthAttachments.of(event.getEntity());
        fresh.copyFrom(carried);
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            double lost = fresh.clearOnDeath();
            HearthHooks.onDeath(player, fresh, lost);
        }
    }

    /**
     * The per-player entries that live only while the player is on: the HUD dirty mark and queued gains
     * ({@code HearthNet}) and the ledger request's rate limit ({@code HearthCharge}; on 1.21.1 the charge
     * lane's {@code ChargeEvents} forgets it, here the foundation does, since the request is ported).
     */
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
        HearthCharge.forget(event.getEntity().getUUID());
    }
}
