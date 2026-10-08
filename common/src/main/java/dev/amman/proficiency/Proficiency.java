package dev.amman.proficiency;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * The mod's id and the wiring both 1.21.1 loaders share. The loader entry points
 * ({@code ProficiencyNeoForge}, {@code ProficiencyFabric}) register what only they can register,
 * then call {@link #init}.
 */
public final class Proficiency {

    public static final String MOD_ID = "proficiency";
    public static final Logger LOG = LogUtils.getLogger();

    private Proficiency() {
    }

    /** After the loader registered items, attachments and payloads, and before any event fires. */
    public static void init() {
        dev.amman.proficiency.platform.Subscribers.registerCommon();
        // Tactician's side of Hammer and Anvil hooks into Charger's first blood.
        dev.amman.proficiency.event.TacticianEvents.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
