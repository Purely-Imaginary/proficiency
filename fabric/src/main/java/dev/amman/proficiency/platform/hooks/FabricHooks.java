package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

/**
 * The NeoForge events that Fabric API already has a callback for, each checked against where
 * NeoForge fires its own:
 * <ul>
 * <li>logged in / out: NeoForge at the end of {@code PlayerList.placeNewPlayer} and the start of
 * {@code PlayerList.remove}; Fabric's JOIN/DISCONNECT fire at the same two points.</li>
 * <li>respawn, clone: end of {@code PlayerList.respawn} / {@code ServerPlayer.restoreFrom}; Fabric's
 * {@code alive} flag is NeoForge's {@code endConquered} and the inverse of {@code wasDeath}.</li>
 * <li>block break: NeoForge pre-cancels for the vanilla refusals (creative sword, adventure mode,
 * game master blocks) and no listener here receives canceled events; Fabric's BEFORE fires only
 * once those same checks have passed, so every listener sees exactly the same breaks.</li>
 * </ul>
 */
public final class FabricHooks {

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
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isSpectator()) {
                return InteractionResult.PASS;
            }
            var event = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos()));
            return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent((Level) level, pos, state, player)).isCanceled());
    }
}
