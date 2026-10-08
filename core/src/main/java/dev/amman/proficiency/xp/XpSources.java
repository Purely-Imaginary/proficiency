package dev.amman.proficiency.xp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The table the game reads. The server side installs a freshly loaded one at start and on every
 * /reload ({@link #install}). Until then, and on a client that never loads a server, the mod's
 * own defaults file stands in, so the answer is never "no rules".
 */
public final class XpSources {

    /** Where the mod's own rules live, inside its jar and in the datapack layout. */
    public static final String DEFAULTS_RESOURCE = "/data/proficiency/proficiency/xp_sources/defaults.json";

    private static volatile XpTable current;
    private static volatile XpTable fallback;

    private XpSources() {
    }

    public static XpTable table() {
        XpTable table = current;
        return table != null ? table : defaults();
    }

    public static void install(XpTable table) {
        current = table;
    }

    /** Forgets the installed table; the defaults answer again. */
    public static void clear() {
        current = null;
    }

    /** The mod's own defaults, parsed once from the jar. */
    public static XpTable defaults() {
        XpTable table = fallback;
        if (table == null) {
            synchronized (XpSources.class) {
                table = fallback;
                if (table == null) {
                    table = XpSourcesLoader.load(List.of(new XpSourcesLoader.SourceFile(
                            "proficiency:xp_sources/defaults", readDefaults())),
                            XpSourcesLoader.Registries.ANY).table;
                    fallback = table;
                }
            }
        }
        return table;
    }

    /** The text of the defaults file in this jar, or an empty object if the jar lacks it. */
    public static String readDefaults() {
        try (InputStream in = XpSources.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            return in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{}";
        }
    }
}
