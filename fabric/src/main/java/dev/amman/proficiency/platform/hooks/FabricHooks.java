package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import dev.amman.proficiency.platform.event.entity.EntityLeaveLevelEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.event.level.ChunkLoadEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The NeoForge events that Fabric API already has a callback for, each checked against where
 * NeoForge fires its own:
 * <ul>
 * <li>logged in / out: NeoForge at the end of {@code PlayerList.placeNewPlayer} and the start of
 * {@code PlayerList.remove}; Fabric's JOIN/DISCONNECT fire at the same two points.</li>
 * <li>respawn, clone: end of {@code PlayerList.respawn} / {@code ServerPlayer.restoreFrom}; Fabric's
 * {@code alive} flag is NeoForge's {@code endConquered} and the inverse of {@code wasDeath}.</li>
 * <li>entity leaves / chunk loads: NeoForge's {@code EntityLeaveLevelEvent} and {@code ChunkEvent.Load} fire when
 * the entity or chunk leaves or finishes loading; Fabric API's {@code ENTITY_UNLOAD} and {@code CHUNK_LOAD} fire
 * at the same two points (server side only, which is all a handler here acts on).</li>
 * <li>block break: NeoForge pre-cancels for the vanilla refusals (creative sword, adventure mode,
 * game master blocks) and no listener here receives canceled events; Fabric's BEFORE fires only
 * once those same checks have passed, so every listener sees exactly the same breaks.</li>
 * <li>right click on a block: NeoForge runs each priority among other mods' listeners, so a
 * {@code LOWEST} listener comes after every claim mod (Architectury, which FTB Chunks uses, listens at
 * HIGH). Fabric callbacks run in registration order instead, so here {@code HIGHEST} to {@code LOW}
 * run in the default phase and {@code LOWEST} in a phase after it ({@link #LATE}): a claim mod's
 * refusal in the default phase ends the click before the {@code LOWEST} listeners (the Hearth hooks)
 * are asked. Both phases share one event, so a cancel before {@code LOWEST} skips those listeners as
 * NeoForge would, and a canceled click ends there even when its result was left at PASS.</li>
 * </ul>
 */
public final class FabricHooks {

    /** The phase after every default-phase {@code UseBlockCallback}: the shared bus's LOWEST runs there. */
    public static final ResourceLocation LATE = ResourceLocation.fromNamespaceAndPath("proficiency", "late");

    private static final EventPriority[] BEFORE_LOWEST = {EventPriority.HIGHEST, EventPriority.HIGH,
            EventPriority.NORMAL, EventPriority.LOW};
    private static final EventPriority[] ONLY_LOWEST = {EventPriority.LOWEST};

    private FabricHooks() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(handler.player)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(newPlayer, alive)));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(newPlayer, oldPlayer, !alive)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(
                        player, origin.dimension(), destination.dimension())));
        ServerTickEvents.END_SERVER_TICK.register(server ->
                NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(server)));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(entity, level)));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                NeoForge.EVENT_BUS.post(new ChunkLoadEvent(level, chunk)));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                NeoForge.EVENT_BUS.post(new RegisterCommandsEvent(dispatcher, selection, context)));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player.isSpectator()) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, hand));
            return event.isCanceled()
                    ? InteractionResultHolder.fail(player.getItemInHand(hand))
                    : InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                rightClickBlock(player, hand, hit, BEFORE_LOWEST, false));
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        UseBlockCallback.EVENT.register(LATE, (player, level, hand, hit) ->
                rightClickBlock(player, hand, hit, ONLY_LOWEST, true));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent((Level) level, pos, state, player)).isCanceled());
        // Before vanilla's own checks (NeoForge asks after them); null lets vanilla decide.
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
                player instanceof net.minecraft.server.level.ServerPlayer server
                        ? NeoForge.EVENT_BUS.post(new dev.amman.proficiency.platform.event.entity.player
                                .CanPlayerSleepEvent(server, pos, null)).getProblem()
                        : null);
    }

    /**
     * The click the default phase posted, kept for the {@link #LATE} phase of the same click: NeoForge
     * posts one event through every priority, so a cancel (and anything else a listener set on it)
     * before {@code LOWEST} must still be on the event the {@code LOWEST} listeners get. Per thread,
     * since the client fires {@code UseBlockCallback} too.
     */
    private static final ThreadLocal<Click> PENDING = new ThreadLocal<>();

    private record Click(Player player, InteractionHand hand, BlockHitResult hit,
            PlayerInteractEvent.RightClickBlock event) {
    }

    /**
     * One shared {@code RightClickBlock} for the given priorities. A cancel answers the click as NeoForge
     * does: with its cancellation result, and a cancel that left the result at PASS ends the click
     * there (FAIL), where Fabric would otherwise ask the next callback and then vanilla.
     */
    private static InteractionResult rightClickBlock(Player player, InteractionHand hand, BlockHitResult hit,
            EventPriority[] priorities, boolean late) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        PlayerInteractEvent.RightClickBlock event = null;
        if (late) {
            Click click = PENDING.get();
            PENDING.remove();
            if (click != null && click.player() == player && click.hand() == hand && click.hit() == hit) {
                event = click.event();
            }
        }
        for (EventPriority priority : priorities) {
            if (!NeoForge.EVENT_BUS.hasListeners(PlayerInteractEvent.RightClickBlock.class, priority)) {
                continue;
            }
            if (event == null) {
                event = new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos());
            }
            NeoForge.EVENT_BUS.post(priority, event);
        }
        if (event == null || !event.isCanceled()) {
            if (!late) {
                if (event != null) {
                    PENDING.set(new Click(player, hand, hit, event));
                } else {
                    PENDING.remove();
                }
            }
            return InteractionResult.PASS;
        }
        if (!late) {
            PENDING.remove();
        }
        InteractionResult result = event.getCancellationResult();
        return result == InteractionResult.PASS ? InteractionResult.FAIL : result;
    }
}
