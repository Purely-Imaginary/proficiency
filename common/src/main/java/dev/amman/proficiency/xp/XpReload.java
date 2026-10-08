package dev.amman.proficiency.xp;

import dev.amman.proficiency.Proficiency;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code data/<namespace>/proficiency/xp_sources/*.json} from the server's datapacks and
 * installs the merged table. Loader neutral: instead of a reload listener per loader it notices
 * that the server swapped its resource manager (which /reload does) and loads again.
 *
 * <p>Order: this mod's own files first, then every other namespace alphabetically, and inside a
 * namespace by path. A datapack that ships a file under the same path as another one replaces it,
 * as every datapack file does.
 */
public final class XpReload {

    private static final String FOLDER = "proficiency/xp_sources";

    private static volatile ResourceManager last;

    private XpReload() {
    }

    /** Cheap enough for every tick: one reference comparison. */
    public static void check(MinecraftServer server) {
        if (server.getResourceManager() != last) {
            reload(server);
        }
    }

    public static void forget() {
        last = null;
        XpSources.clear();
    }

    public static XpSourcesLoader.Result reload(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        last = manager;
        List<XpSourcesLoader.SourceFile> files = new ArrayList<>();
        Map<ResourceLocation, Resource> found = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        List<ResourceLocation> ids = new ArrayList<>(found.keySet());
        ids.sort(Comparator
                .comparing((ResourceLocation id) -> !Proficiency.MOD_ID.equals(id.getNamespace()))
                .thenComparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath));
        for (ResourceLocation id : ids) {
            String name = id.getNamespace() + ":" + id.getPath().substring("proficiency/".length(),
                    id.getPath().length() - ".json".length());
            try (InputStream in = found.get(id).open()) {
                files.add(new XpSourcesLoader.SourceFile(name,
                        new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                Proficiency.LOG.error("XP sources: cannot read {}: {}", name, e.toString());
            }
        }
        XpSourcesLoader.Result result = XpSourcesLoader.load(files, new Oracle(server));
        // A pack that wiped the defaults away (or a broken jar) must not leave the mod paying nothing.
        XpSources.install(result.table);
        for (XpSourcesLoader.Message message : result.messages) {
            if (message.level() == XpSourcesLoader.Level.ERROR) {
                Proficiency.LOG.error("XP sources: {}", message.text());
            } else {
                Proficiency.LOG.warn("XP sources: {}", message.text());
            }
        }
        Proficiency.LOG.info("XP sources: {}", result.summary());
        return result;
    }

    /** The registries of a running server. */
    private record Oracle(MinecraftServer server) implements XpSourcesLoader.Registries {

        private Registry<?> registry(String name) {
            return switch (name) {
                case "block" -> BuiltInRegistries.BLOCK;
                case "item" -> BuiltInRegistries.ITEM;
                case "entity" -> BuiltInRegistries.ENTITY_TYPE;
                case "structure" -> server.registryAccess().registryOrThrow(Registries.STRUCTURE);
                case "biome" -> server.registryAccess().registryOrThrow(Registries.BIOME);
                default -> null;
            };
        }

        @Override
        public boolean knowsId(String registryName, String id) {
            Registry<?> registry = registry(registryName);
            ResourceLocation location = ResourceLocation.tryParse(id);
            return registry == null || location == null || registry.containsKey(location);
        }

        @Override
        public boolean knowsTag(String registryName, String tag) {
            if (XpBuiltin.isBuiltin(tag)) {
                return XpBuiltin.known(tag);
            }
            Registry<?> registry = registry(registryName);
            ResourceLocation location = ResourceLocation.tryParse(tag);
            return registry == null || location == null
                    || registry.getTagNames().anyMatch(key -> key.location().equals(location));
        }
    }
}
