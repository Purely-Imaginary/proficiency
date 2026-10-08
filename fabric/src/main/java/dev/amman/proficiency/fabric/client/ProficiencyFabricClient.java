package dev.amman.proficiency.fabric.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.client.ClientWiring;
import dev.amman.proficiency.client.OverlayFallback;
import dev.amman.proficiency.client.SkillTooltip;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.net.CalledShotPayload;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.LevelUpPayload;
import dev.amman.proficiency.net.ProcFxPayload;
import dev.amman.proficiency.net.SyncSkillsPayload;
import dev.amman.proficiency.net.VisitedPayload;
import dev.amman.proficiency.net.XpFeedPayload;
import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.platform.Subscribers;
import dev.amman.proficiency.platform.hooks.FabricClientHooks;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.config.ModConfig;

/**
 * Client-only wiring on Fabric: key mappings, the server-to-client payloads, the compass needles
 * and the client listeners.
 */
public final class ProficiencyFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientWiring.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
        // The client file config/proficiency-client.toml. NeoForge also gets a Mods-screen page for
        // it; Fabric has none without Mod Menu, which the pack does not ship.
        NeoForgeConfigRegistry.INSTANCE.register(Proficiency.MOD_ID, ModConfig.Type.CLIENT,
                ProficiencyClientConfig.SPEC);

        ClientPlayNetworking.registerGlobalReceiver(SyncSkillsPayload.TYPE,
                (payload, context) -> ClientWiring.onSync(payload, context.player()));
        ClientPlayNetworking.registerGlobalReceiver(LevelUpPayload.TYPE,
                (payload, context) -> ClientWiring.onLevelUp(payload));
        ClientPlayNetworking.registerGlobalReceiver(XpLogPayload.TYPE,
                (payload, context) -> ClientWiring.onXpLog(payload));
        ClientPlayNetworking.registerGlobalReceiver(XpFeedPayload.TYPE,
                (payload, context) -> ClientWiring.onXpFeed(payload));
        ClientPlayNetworking.registerGlobalReceiver(DiscoveryPayload.TYPE,
                (payload, context) -> ClientWiring.onDiscovery(payload));
        ClientPlayNetworking.registerGlobalReceiver(VisitedPayload.TYPE,
                (payload, context) -> ClientWiring.onVisited(payload));
        ClientPlayNetworking.registerGlobalReceiver(CalledShotPayload.TYPE,
                (payload, context) -> ClientWiring.onCalledShot(payload));
        ClientPlayNetworking.registerGlobalReceiver(ProcFxPayload.TYPE,
                (payload, context) -> ClientWiring.onProcFx(payload));
        ClientPlayNetworking.registerGlobalReceiver(DeathRecapPayload.TYPE,
                (payload, context) -> ClientWiring.onDeathRecap(payload));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                !OverlayFallback.reroute(message, overlay));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientWiring.onLeaveWorld());
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                SkillTooltip.append(Minecraft.getInstance().player, stack, lines));

        // Items are registered in the common initializer, which Fabric runs before this one.
        FabricModelPredicateProviderRegistry.register(ProficiencyItems.FORESTERS_COMPASS.get(),
                ClientWiring.ANGLE, ClientWiring.forestersCompassNeedle());
        FabricModelPredicateProviderRegistry.register(ProficiencyItems.FRIEND_COMPASS.get(),
                ClientWiring.ANGLE, ClientWiring.friendCompassNeedle());

        Subscribers.registerClient();
        FabricClientHooks.init();
    }
}
