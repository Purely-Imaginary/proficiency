package dev.amman.proficiency.client;

import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.net.LevelUpPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.net.SyncSkillsPayload;
import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.platform.hooks.FabricClientHooks;
import dev.amman.proficiency.platform.hooks.Subscribers;
import dev.amman.proficiency.skill.Skill;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.neoforged.fml.config.ModConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Client-only wiring: key mappings, the two server-to-client payloads, the compass needle and the
 * client listeners.
 */
public final class ProficiencyClientSetup implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ProficiencyClient.OPEN_SKILLS);
        KeyBindingHelper.registerKeyBinding(ProficiencyClient.USE_ABILITY);
        // The client file config/proficiency-client.toml. NeoForge also gets a Mods-screen page for
        // it; Fabric has none without Mod Menu, which the pack does not ship.
        NeoForgeConfigRegistry.INSTANCE.register(dev.amman.proficiency.Proficiency.MOD_ID,
                ModConfig.Type.CLIENT, dev.amman.proficiency.config.ProficiencyClientConfig.SPEC);

        ClientPlayNetworking.registerGlobalReceiver(SyncSkillsPayload.TYPE,
                (payload, context) -> ProficiencyNetwork.onSync(payload, context.player()));
        ClientPlayNetworking.registerGlobalReceiver(LevelUpPayload.TYPE, (payload, context) -> {
            Skill skill = Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)];
            SkillToasts.show(skill, payload.level());
        });
        ClientPlayNetworking.registerGlobalReceiver(XpLogPayload.TYPE,
                (payload, context) -> ClientXpLog.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.amman.proficiency.net.XpFeedPayload.TYPE,
                (payload, context) -> XpFeedHud.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.amman.proficiency.net.DiscoveryPayload.TYPE,
                (payload, context) -> DiscoveryBanner.show(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.amman.proficiency.net.VisitedPayload.TYPE,
                (payload, context) -> ClientVisited.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(dev.amman.proficiency.net.CalledShotPayload.TYPE,
                (payload, context) -> CalledShotMarks.accept(payload));
        // The recent-XP list is per session on the server; leaving a world ends it here too, or
        // the next server would open on the last one's lines.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientXpLog.clear();
            XpFeedHud.clear();
            DiscoveryBanner.clear();
            ClientVisited.clear();
            CalledShotMarks.clear();
        });

        // Vanilla's needle maths, fed from the stack's lodestone tracker instead of a lodestone.
        // Items are registered in the common initializer, which Fabric runs before this one.
        FabricModelPredicateProviderRegistry.register(ProficiencyItems.FORESTERS_COMPASS.get(),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));
        // The friend's live position when this client can see them (inside entity-tracking
        // range), else the server's coarse copy in the lodestone tracker. See FriendCompassItem.
        FabricModelPredicateProviderRegistry.register(ProficiencyItems.FRIEND_COMPASS.get(),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    java.util.UUID id = dev.amman.proficiency.item.FriendCompassItem.friend(stack);
                    if (id != null && level != null) {
                        net.minecraft.world.entity.player.Player friend = level.getPlayerByUUID(id);
                        if (friend != null) {
                            return net.minecraft.core.GlobalPos.of(level.dimension(), friend.blockPosition());
                        }
                    }
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));

        Subscribers.registerClient();
        FabricClientHooks.init();
    }
}
