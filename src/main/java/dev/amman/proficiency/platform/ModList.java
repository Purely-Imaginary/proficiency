package dev.amman.proficiency.platform;

import net.fabricmc.loader.api.FabricLoader;

/** {@code ModList.get().isLoaded(id)}, answered by Fabric Loader. */
public final class ModList {

    private static final ModList INSTANCE = new ModList();

    private ModList() {
    }

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }
}
