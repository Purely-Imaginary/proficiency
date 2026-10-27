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
        // The Hearth: every lane registers its listeners (docs/HEARTH-CONTRACT.md).
        dev.amman.proficiency.hearth.Hearth.init();
        // The advancement tab listens for the Hearth's milestones.
        dev.amman.proficiency.skill.ProficiencyAdvancements.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
