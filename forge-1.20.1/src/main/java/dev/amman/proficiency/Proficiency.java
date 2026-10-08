package dev.amman.proficiency;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Proficiency.MOD_ID)
public class Proficiency {

    public static final String MOD_ID = "proficiency";
    public static final Logger LOG = LogUtils.getLogger();

    public Proficiency() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext container = ModLoadingContext.get();
        modBus.addListener(ProficiencyAttachments::registerCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.entity.Entity.class,
                ProficiencyAttachments::attach);
        dev.amman.proficiency.item.ProficiencyItems.ITEMS.register(modBus);
        dev.amman.proficiency.item.ProficiencyItems.TABS.register(modBus);
        dev.amman.proficiency.item.ProficiencyEnchantments.ENCHANTMENTS.register(modBus);
        dev.amman.proficiency.compat.DropsModifier.SERIALIZERS.register(modBus);
        ProficiencyNetwork.register();
        // Tactician's side of Hammer and Anvil hooks into Charger's first blood.
        dev.amman.proficiency.event.TacticianEvents.init();
        container.registerConfig(ModConfig.Type.SERVER, ProficiencyConfig.SPEC);
        // A pack's own material lists (config/proficiency-materials.json), before any tree is shown.
        dev.amman.proficiency.perk.MaterialOverrides.load(FMLPaths.CONFIGDIR.get());
        modBus.addListener(Proficiency::onLoadComplete);

        if (FMLEnvironment.dist.isClient()) {
            // The client class resolves here and nowhere else, so a dedicated server never loads it.
            container.registerConfig(ModConfig.Type.CLIENT,
                    dev.amman.proficiency.config.ProficiencyClientConfig.SPEC);
            dev.amman.proficiency.client.ProficiencyClientSetup.init(modBus, container);
        }
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        dev.amman.proficiency.perk.MaterialOverrides.validate(BuiltInRegistries.ITEM::containsKey);
        // Dev aid: PROFICIENCY_DUMP_ITEMS=<file> writes every registered item id, one per line, so
        // a pack's id list for the override test comes from the real registry and not from guesses.
        String dump = System.getenv("PROFICIENCY_DUMP_ITEMS");
        if (dump != null && !dump.isBlank()) {
            try {
                java.nio.file.Files.write(java.nio.file.Path.of(dump), BuiltInRegistries.ITEM.keySet().stream()
                        .map(ResourceLocation::toString).sorted().toList());
                LOG.info("[proficiency] wrote {} item ids to {}", BuiltInRegistries.ITEM.keySet().size(), dump);
            } catch (java.io.IOException e) {
                LOG.warn("[proficiency] could not write the item id dump to {}", dump, e);
            }
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
