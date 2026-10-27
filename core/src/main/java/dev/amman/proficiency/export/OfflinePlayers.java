package dev.amman.proficiency.export;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Every player who has Proficiency data saved in a world, online or not, read from the world's
 * {@code playerdata/<uuid>.dat} without loading anyone as an entity. A file is parsed again only when its
 * modified time or size changed since the last scan, so the periodic export costs one directory listing.
 * Names come from the caller (the server's profile cache); the uuid is the fallback.
 */
public final class OfflinePlayers {

    private record Entry(long mtime, long size, PlayerSkills skills) {
    }

    private final Map<UUID, Entry> cache = new HashMap<>();

    /**
     * One row per player file that holds the mod's skills tag, marked offline, {@code seenMs} the file's modified
     * time and deaths 0 (the hub fills those in). A file that cannot be read or has no tag is skipped.
     * {@code names} may return null for an unknown player; the row then carries the uuid as its name.
     */
    public synchronized List<PlayerRow> scan(Path playerdata, Function<UUID, String> names) {
        List<PlayerRow> rows = new ArrayList<>();
        if (!Files.isDirectory(playerdata)) {
            cache.clear();
            return rows;
        }
        Set<UUID> present = new HashSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(playerdata, "*.dat")) {
            for (Path file : files) {
                UUID id = idOf(file);
                if (id == null) {
                    continue;
                }
                present.add(id);
                long mtime;
                long size;
                try {
                    mtime = Files.getLastModifiedTime(file).toMillis();
                    size = Files.size(file);
                } catch (IOException e) {
                    continue;
                }
                Entry entry = cache.get(id);
                if (entry == null || entry.mtime != mtime || entry.size != size) {
                    entry = new Entry(mtime, size, parse(file));
                    cache.put(id, entry);
                }
                if (entry.skills != null) {
                    String name = names == null ? null : names.apply(id);
                    rows.add(PlayerRow.of(id, name == null || name.isBlank() ? id.toString() : name, false,
                            mtime, entry.skills, 0));
                }
            }
        } catch (IOException e) {
            // An unreadable folder lists nobody this time; rows the hub already holds stay.
        }
        cache.keySet().retainAll(present);
        return rows;
    }

    private static PlayerSkills parse(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            JsonElement tag = OfflineNbt.skillsTag(in);
            if (tag == null) {
                return null;
            }
            Optional<PlayerSkills> parsed = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, tag).result();
            return parsed.orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static UUID idOf(Path file) {
        String name = file.getFileName().toString();
        try {
            return UUID.fromString(name.substring(0, name.length() - ".dat".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
