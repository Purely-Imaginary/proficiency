package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code data/<namespace>/proficiency/hearth/*.json} from the server's datapacks and installs
 * the merged {@link HearthData} ({@link Hearths#install}). Loader neutral, like the XP sources: it
 * notices that the server swapped its resource manager (which {@code /reload} does) and loads again.
 * Order ({@link HearthDataLoader#ORDER}): the shipped {@code proficiency:hearth/default} first, then
 * this mod's other files by path, then every other namespace alphabetically, each by path.
 */
public final class HearthReload {

    private static final String FOLDER = "proficiency/hearth";

    private static volatile ResourceManager last;
    private static volatile HearthDataLoader.Result lastResult;

    private HearthReload() {
    }

    /** Cheap enough for every tick: one reference comparison. */
    public static void check(MinecraftServer server) {
        if (server.getResourceManager() != last) {
            reload(server);
        }
    }

    public static void forget() {
        last = null;
        lastResult = null;
        Hearths.reset();
    }

    /** What the last load found (files, problems), or null before the first. */
    public static HearthDataLoader.Result lastResult() {
        return lastResult;
    }

    public static HearthDataLoader.Result reload(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        last = manager;
        List<HearthDataLoader.SourceFile> files = new ArrayList<>();
        Map<ResourceLocation, Resource> found = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (ResourceLocation id : found.keySet()) {
            String name = id.getNamespace() + ":" + id.getPath().substring("proficiency/".length(),
                    id.getPath().length() - ".json".length());
            try (InputStream in = found.get(id).open()) {
                files.add(new HearthDataLoader.SourceFile(name, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                Proficiency.LOG.error("Hearth data: cannot read {}: {}", name, e.toString());
            }
        }
        HearthDataLoader.Result result = HearthDataLoader.load(HearthDataLoader.ordered(files));
        Hearths.install(result.data());
        lastResult = result;
        for (HearthDataLoader.Message message : result.messages()) {
            if (message.level() == HearthDataLoader.Level.ERROR) {
                Proficiency.LOG.error("Hearth data: {}", message.text());
            } else {
                Proficiency.LOG.warn("Hearth data: {}", message.text());
            }
        }
        Proficiency.LOG.info("Hearth data: {}", result.summary());
        return result;
    }
}
