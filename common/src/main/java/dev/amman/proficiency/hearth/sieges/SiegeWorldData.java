package dev.amman.proficiency.hearth.sieges;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Sieges lane's world data (overworld {@code data/proficiency_hearth_sieges.dat}): for every
 * siege counting down or running, who it concerns and what its panel last showed, so a concerned
 * player who logs in far from home gets the left-side panel back while the Hearth's chunk is
 * unloaded (docs/HEARTH-SPEC.md, "Warning"). The due time itself is the Hearth's
 * ({@code HearthState.siegeDue}), mirrored by the foundation in {@code HearthWorldData}; a watch
 * whose Hearth no longer has one there is dropped when it is next read.
 */
public final class SiegeWorldData extends SavedData {

    static final String NAME = "proficiency_hearth_sieges";

    /** One siege's panel facts, keyed by the Hearth key ({@code "dimension|x,y,z"}). */
    public static final class Watch {
        final String dimension;
        final long pos;
        final Set<UUID> concerned = new LinkedHashSet<>();
        int phase;
        int wave;
        int waves;
        int left;
        int douse;
        long started;

        Watch(String dimension, long pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        public Set<UUID> concerned() {
            return Collections.unmodifiableSet(concerned);
        }

        public int phase() {
            return phase;
        }

        public int douse() {
            return douse;
        }
    }

    private final Map<String, Watch> watches = new LinkedHashMap<>();

    public static SiegeWorldData create() {
        return new SiegeWorldData();
    }

    public static SiegeWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiegeWorldData data = new SiegeWorldData();
        CompoundTag all = tag.getCompound("watches");
        for (String key : all.getAllKeys()) {
            CompoundTag w = all.getCompound(key);
            Watch watch = new Watch(w.getString("dim"), w.getLong("pos"));
            watch.phase = w.getInt("phase");
            watch.wave = w.getInt("wave");
            watch.waves = w.getInt("waves");
            watch.left = w.getInt("left");
            watch.douse = w.getInt("douse");
            watch.started = w.getLong("started");
            for (Tag id : w.getList("concerned", Tag.TAG_INT_ARRAY)) {
                watch.concerned.add(NbtUtils.loadUUID(id));
            }
            data.watches.put(key, watch);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        watches.forEach((key, watch) -> {
            CompoundTag w = new CompoundTag();
            w.putString("dim", watch.dimension);
            w.putLong("pos", watch.pos);
            w.putInt("phase", watch.phase);
            w.putInt("wave", watch.wave);
            w.putInt("waves", watch.waves);
            w.putInt("left", watch.left);
            w.putInt("douse", watch.douse);
            w.putLong("started", watch.started);
            ListTag ids = new ListTag();
            for (UUID id : watch.concerned) {
                ids.add(NbtUtils.createUUID(id));
            }
            w.put("concerned", ids);
            all.put(key, w);
        });
        tag.put("watches", all);
        return tag;
    }

    private static final SavedData.Factory<SiegeWorldData> FACTORY =
            new SavedData.Factory<>(SiegeWorldData::create, SiegeWorldData::load, null);

    public static SiegeWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Map<String, Watch> watches() {
        return Collections.unmodifiableMap(watches);
    }

    @Nullable
    public Watch watch(String key) {
        return watches.get(key);
    }

    /** The watch of a Hearth, made when missing. */
    Watch open(String key, String dimension, long pos) {
        Watch watch = watches.get(key);
        if (watch == null || watch.pos != pos || !watch.dimension.equals(dimension)) {
            watch = new Watch(dimension, pos);
            watches.put(key, watch);
        }
        setDirty();
        return watch;
    }

    /** Adds a concerned player; true when they were not on the list. */
    boolean concern(String key, UUID player) {
        Watch watch = watches.get(key);
        if (watch != null && watch.concerned.add(player)) {
            setDirty();
            return true;
        }
        return false;
    }

    void update(String key, int phase, int wave, int waves, int left, long started) {
        Watch watch = watches.get(key);
        if (watch != null) {
            watch.phase = phase;
            watch.wave = wave;
            watch.waves = waves;
            watch.left = left;
            watch.started = started;
            setDirty();
        }
    }

    /** What the waves panel last showed: mobs left and the douse meter in percent (the login copy). */
    void shown(String key, int left, int douse) {
        Watch watch = watches.get(key);
        if (watch != null && (watch.left != left || watch.douse != douse)) {
            watch.left = left;
            watch.douse = douse;
            setDirty();
        }
    }

    void close(String key) {
        if (watches.remove(key) != null) {
            setDirty();
        }
    }
}
