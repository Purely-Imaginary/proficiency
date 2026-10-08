package dev.amman.proficiency.fabric;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.platform.hooks.FabricHooks;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;

/** Fabric entry point. Same wiring as NeoForge's, in the same order. */
public final class ProficiencyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPlatform.init();
        ProficiencyItems.init();
        FabricNetwork.register();
        NeoForgeConfigRegistry.INSTANCE.register(Proficiency.MOD_ID, ModConfig.Type.SERVER, ProficiencyConfig.SPEC);
        FabricServerLifecycle.init();
        Proficiency.init();
        FabricHooks.init();
    }
}
