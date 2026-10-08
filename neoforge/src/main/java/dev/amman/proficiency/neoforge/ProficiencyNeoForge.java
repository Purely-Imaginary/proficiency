package dev.amman.proficiency.neoforge;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.item.ProficiencyItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge entry point: registers what only NeoForge can, then the shared wiring. */
@Mod(Proficiency.MOD_ID)
public final class ProficiencyNeoForge {

    public ProficiencyNeoForge(IEventBus modBus, ModContainer container) {
        NeoForgePlatform.ATTACHMENTS.register(modBus);
        // Class init hands every item and the tab to the deferred registers.
        ProficiencyItems.init();
        NeoForgePlatform.ITEMS.register(modBus);
        NeoForgePlatform.TABS.register(modBus);
        modBus.addListener(NeoForgeNetwork::register);
        Proficiency.init();
        NeoForgeEventBridge.register();
        container.registerConfig(ModConfig.Type.SERVER, ProficiencyConfig.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            // The client class resolves here and nowhere else, so a dedicated server never loads it.
            container.registerConfig(ModConfig.Type.CLIENT,
                    dev.amman.proficiency.config.ProficiencyClientConfig.SPEC);
            dev.amman.proficiency.neoforge.client.NeoForgeClientSetup.init(modBus, container);
        }
    }
}
