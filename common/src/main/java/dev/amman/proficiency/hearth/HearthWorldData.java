package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The world's Hearth data, saved once (in the overworld's {@code data/proficiency_hearth_world.dat}):
 * <ul>
 * <li>the <b>play clock</b>: ticks of server time with at least one non-AFK player online, the unit
 * of siege cooldowns (docs/HEARTH-SPEC.md, "When a siege can happen"); the Sieges lane advances it;</li>
 * <li>the <b>Chronicle</b> register: page counter and per-page rating state ({@link ChronicleBook});</li>
 * <li>the <b>siege due times</b> per Hearth key ({@code "dimension|x,y,z"}), readable while the
 * Hearth's chunk is unloaded (the countdown panel of a player who is away, a restart).</li>
 * </ul>
 *
 * <p>The due times are a <b>mirror</b>, never a second source of truth: the one that counts is
 * {@link HearthState#siegeDue()} on the Hearth's block entity, which the Sieges lane writes. This
 * class copies it every Hearth second ({@link HearthBlockEntity}) and drops a Hearth's entry when
 * the Hearth goes ({@link HearthIndexData#forget}), so the two never disagree for longer than a
 * second while the Hearth is loaded. Lanes read {@link #siegeDue()}; only this package writes it.
 */
public final class HearthWorldData extends SavedData {

    static final String NAME = "proficiency_hearth_world";

    private long playTicks;
    private ChronicleBook chronicle = new ChronicleBook();
    private final Map<String, Long> siegeDue = new LinkedHashMap<>();

    public static HearthWorldData create() {
        return new HearthWorldData();
    }

    public static HearthWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        HearthWorldData data = new HearthWorldData();
        data.playTicks = Math.max(0L, tag.getLong("playTicks"));
        Tag book = tag.get("chronicle");
        if (book != null) {
            data.chronicle = ChronicleBook.CODEC.parse(NbtOps.INSTANCE, book)
                    .resultOrPartial(error -> Proficiency.LOG.error("Hearth world data: chronicle: {}", error))
                    .orElseGet(ChronicleBook::new);
        }
        CompoundTag due = tag.getCompound("siegeDue");
        for (String key : due.getAllKeys()) {
            data.siegeDue.put(key, due.getLong(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("playTicks", playTicks);
        ChronicleBook.CODEC.encodeStart(NbtOps.INSTANCE, chronicle)
                .resultOrPartial(error -> Proficiency.LOG.error("Hearth world data: chronicle: {}", error))
                .ifPresent(saved -> tag.put("chronicle", saved));
        CompoundTag due = new CompoundTag();
        siegeDue.forEach(due::putLong);
        tag.put("siegeDue", due);
        return tag;
    }

    private static final SavedData.Factory<HearthWorldData> FACTORY =
            new SavedData.Factory<>(HearthWorldData::create, HearthWorldData::load, null);

    public static HearthWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Ticks of play so far (the siege clock). */
    public long playTicks() {
        return playTicks;
    }

    /** Advances the play clock; the Sieges lane calls it while a non-AFK player is online. */
    public void addPlayTicks(long ticks) {
        if (ticks > 0) {
            playTicks += ticks;
            setDirty();
        }
    }

    /** The Chronicle register. Call {@link #setDirty()} after writing or rating a page. */
    public ChronicleBook chronicle() {
        return chronicle;
    }

    /** Every pending siege's due time by Hearth key, read-only: the mirror of each Hearth's {@link HearthState#siegeDue()}. */
    public Map<String, Long> siegeDue() {
        return java.util.Collections.unmodifiableMap(siegeDue);
    }

    /** One Hearth's mirrored due time, or {@link HearthState#NEVER} when nothing is pending there. */
    public long siegeDue(String hearthKey) {
        Long due = siegeDue.get(hearthKey);
        return due == null ? HearthState.NEVER : due;
    }

    /** Copies a Hearth's {@link HearthState#siegeDue()} (NEVER removes the entry). Package only: the block entity's tick. */
    void mirrorSiegeDue(String hearthKey, long due) {
        if (due == HearthState.NEVER) {
            forgetHearth(hearthKey);
            return;
        }
        Long before = siegeDue.put(hearthKey, due);
        if (before == null || before != due) {
            setDirty();
        }
    }

    /** The Hearth is gone: its mirrored entries go too. Package only: {@link HearthIndexData#forget}. */
    void forgetHearth(String hearthKey) {
        if (siegeDue.remove(hearthKey) != null) {
            setDirty();
        }
    }
}
