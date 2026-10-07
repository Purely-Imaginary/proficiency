package dev.amman.proficiency;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.platform.ServerLifecycleHooks;
import dev.amman.proficiency.platform.hooks.FabricHooks;
import dev.amman.proficiency.platform.hooks.Subscribers;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Fabric entrypoint. Same wiring the NeoForge constructor did, in the same order. */
public class Proficiency implements ModInitializer {

    public static final String MOD_ID = "proficiency";
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        ProficiencyAttachments.init();
        dev.amman.proficiency.platform.EntityData.init();
        dev.amman.proficiency.item.ProficiencyItems.init();
        ProficiencyNetwork.register();
        NeoForgeConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.SERVER, ProficiencyConfig.SPEC);
        ServerLifecycleHooks.init();
        Subscribers.registerCommon();
        FabricHooks.init();
        // Tactician's side of Hammer and Anvil hooks into Charger's first blood.
        dev.amman.proficiency.event.TacticianEvents.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
