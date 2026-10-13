package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthIndexStore;
import dev.amman.proficiency.hearth.HearthRange;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Workshop's station speed at the furnace tick (docs/HEARTH-SPEC.md, the boons table; build notes:
 * "Hearth positions cached per chunk so the furnace tick looks up 'in a Hearth's range' in O(1)").
 *
 * <p>Each Hearth's tick ({@link HearthWorkshop#hearthSecond}) publishes its station speed here while
 * someone is home, valid for 2 s. A station's tick (the two mixins) asks {@link #speed}: nothing at
 * all while no Hearth anywhere has a working Workshop, else one per-chunk index lookup
 * ({@code HearthRange.hearthAtPacked}, no allocation) and one map read. A broken Hearth leaves the
 * index at once; one unloaded, smouldering, switched off or left alone stops publishing and runs out
 * within 2 s, and {@link #prune} (once a second) drops what ran out. Server thread only.
 */
public final class HearthStations {

    /** A furnace, smoker or blast furnace, as {@code HearthFurnaceMixin} exposes it. */
    public interface Furnace {
        void proficiency$hearthMark();

        int proficiency$hearthMarked();

        int proficiency$cookingProgress();

        void proficiency$setCookingProgress(int progress);

        int proficiency$cookingTotalTime();

        int proficiency$litTime();

        void proficiency$setLitTime(int litTime);

        float proficiency$hearthCarry();

        void proficiency$setHearthCarry(float carry);
    }

    /** A brewing stand, as {@code HearthBrewingStandMixin} exposes it. */
    public interface Brewer {
        void proficiency$hearthMark();

        int proficiency$hearthMarked();

        int proficiency$brewTime();

        void proficiency$setBrewTime(int brewTime);

        float proficiency$hearthCarry();

        void proficiency$setHearthCarry(float carry);
    }

    /** How long one publication lasts: two Hearth seconds. */
    static final long VALID_TICKS = 40L;

    private record Active(double speed, long until) {
    }

    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Active>> ACTIVE = new HashMap<>();
    private static int count;

    private HearthStations() {
    }

    /** A Hearth's tick: its station speed now (0 removes it). */
    static void publish(ServerLevel level, long hearth, double speed, long now) {
        Long2ObjectOpenHashMap<Active> map = ACTIVE.get(level.dimension());
        if (!(speed > 0)) {
            if (map != null && map.remove(hearth) != null) {
                count--;
            }
            return;
        }
        if (map == null) {
            map = new Long2ObjectOpenHashMap<>();
            ACTIVE.put(level.dimension(), map);
        }
        if (map.put(hearth, new Active(speed, now + VALID_TICKS)) == null) {
            count++;
        }
    }

    /**
     * Once a server second: publications that ran out (a Hearth whose chunk unloaded, so it never
     * publishes its 0) drop out, so the "no Workshop anywhere" fast path in {@link #speed} comes back
     * when the last one goes. The maps hold a handful. Public for the GameTests.
     */
    public static void prune(MinecraftServer server) {
        if (count <= 0) {
            return;
        }
        var maps = ACTIVE.entrySet().iterator();
        while (maps.hasNext()) {
            var entry = maps.next();
            Long2ObjectOpenHashMap<Active> map = entry.getValue();
            ServerLevel level = server.getLevel(entry.getKey());
            long now = level == null ? Long.MAX_VALUE : level.getGameTime();
            var it = map.long2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().until() < now) {
                    it.remove();
                    count--;
                }
            }
            if (map.isEmpty()) {
                maps.remove();
            }
        }
    }

    /** Tests: how many Hearths publish a station speed now. */
    public static int published() {
        return count;
    }

    /** For tests: publish a speed for a Hearth position directly (valid {@code ticks}). */
    public static void publishForTest(ServerLevel level, BlockPos hearth, double speed, long ticks) {
        publish(level, hearth.asLong(), speed, level.getGameTime() + ticks - VALID_TICKS);
    }

    static void clear() {
        ACTIVE.clear();
        count = 0;
    }

    /** The station speed at a station's position: 0.40 = 40% faster; 0 almost always. */
    public static double speed(ServerLevel level, BlockPos pos) {
        if (count <= 0) {
            return 0.0;
        }
        Long2ObjectOpenHashMap<Active> map = ACTIVE.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return 0.0;
        }
        long hearth = HearthRange.hearthAtPacked(level, pos.getX(), pos.getY(), pos.getZ());
        if (hearth == HearthIndexStore.NONE) {
            return 0.0;
        }
        Active active = map.get(hearth);
        if (active == null || active.until() < level.getGameTime() || !ProficiencyConfig.hearthEnabled()) {
            // A run-out publication goes at the next prune (once a second).
            return 0.0;
        }
        return active.speed();
    }

    // ---- The two mixins call these ---------------------------------------------------------------

    /** After a furnace's vanilla tick: extra progress when it cooked one step and a Workshop is near. */
    public static void afterFurnace(Level level, BlockPos pos, Furnace furnace) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int progress = furnace.proficiency$cookingProgress();
        if (progress != furnace.proficiency$hearthMarked() + 1) {
            // Not cooking this tick, or it just finished: vanilla's business.
            return;
        }
        double speed = speed(server, pos);
        if (!(speed > 0)) {
            if (furnace.proficiency$hearthCarry() != 0f) {
                furnace.proficiency$setHearthCarry(0f);
            }
            return;
        }
        float carry = StationMath.carryAfter(furnace.proficiency$hearthCarry(), speed);
        int lit = furnace.proficiency$litTime();
        int add = StationMath.extra(carry,
                StationMath.furnaceRoom(progress, furnace.proficiency$cookingTotalTime(), lit));
        if (add > 0) {
            furnace.proficiency$setCookingProgress(progress + add);
            furnace.proficiency$setLitTime(lit - add);
        }
        furnace.proficiency$setHearthCarry(StationMath.carryLeft(carry, add));
    }

    /** After a brewing stand's vanilla tick: brews faster when it brewed one step and a Workshop is near. */
    public static void afterBrewer(Level level, BlockPos pos, Brewer brewer) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int brew = brewer.proficiency$brewTime();
        if (brew <= 0 || brew != brewer.proficiency$hearthMarked() - 1) {
            return;
        }
        double speed = speed(server, pos);
        if (!(speed > 0)) {
            if (brewer.proficiency$hearthCarry() != 0f) {
                brewer.proficiency$setHearthCarry(0f);
            }
            return;
        }
        float carry = StationMath.carryAfter(brewer.proficiency$hearthCarry(), speed);
        int add = StationMath.extra(carry, StationMath.brewRoom(brew));
        if (add > 0) {
            brewer.proficiency$setBrewTime(brew - add);
        }
        brewer.proficiency$setHearthCarry(StationMath.carryLeft(carry, add));
    }
}
