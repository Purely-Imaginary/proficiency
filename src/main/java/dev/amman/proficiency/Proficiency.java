package dev.amman.proficiency;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Proficiency.MOD_ID)
public class Proficiency {

    public static final String MOD_ID = "proficiency";
    public static final Logger LOG = LogUtils.getLogger();

    public Proficiency(IEventBus modBus, ModContainer container) {
        ProficiencyAttachments.TYPES.register(modBus);
        dev.amman.proficiency.item.ProficiencyItems.ITEMS.register(modBus);
        dev.amman.proficiency.item.ProficiencyItems.TABS.register(modBus);
        modBus.addListener(ProficiencyNetwork::register);
        // Tactician's side of Hammer and Anvil hooks into Charger's first blood.
        dev.amman.proficiency.event.TacticianEvents.init();
        container.registerConfig(ModConfig.Type.SERVER, ProficiencyConfig.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            // The client class resolves here and nowhere else, so a dedicated server never loads it.
            container.registerConfig(ModConfig.Type.CLIENT,
                    dev.amman.proficiency.config.ProficiencyClientConfig.SPEC);
            dev.amman.proficiency.client.ProficiencyClientSetup.init(modBus, container);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
