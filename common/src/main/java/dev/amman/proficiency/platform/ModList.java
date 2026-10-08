package dev.amman.proficiency.platform;

/** {@code ModList.get().isLoaded(id)}, answered by the loader. */
public final class ModList {

    private static final ModList INSTANCE = new ModList();

    private ModList() {
    }

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String id) {
        return Services.platform().isModLoaded(id);
    }
}
