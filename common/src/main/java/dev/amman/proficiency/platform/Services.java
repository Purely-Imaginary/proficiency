package dev.amman.proficiency.platform;

import java.util.ServiceLoader;

/** Loads the loader's {@link Platform} on first use, so unit tests that never ask need none. */
public final class Services {

    private Services() {
    }

    public static Platform platform() {
        return Lazy.PLATFORM;
    }

    public static ClientPlatform client() {
        return LazyClient.CLIENT;
    }

    static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getName()
                        + " implementation: the loader module must list one in META-INF/services"));
    }

    private static final class Lazy {
        static final Platform PLATFORM = load(Platform.class);
    }

    private static final class LazyClient {
        static final ClientPlatform CLIENT = load(ClientPlatform.class);
    }
}
