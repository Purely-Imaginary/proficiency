package dev.amman.proficiency.hearth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Hearth's milestones, announced by the lane that makes them happen and heard by whoever cares
 * (the Homebuilder skill pays XP for each, docs/HOMEBUILDER-SPEC.md). Fire with
 * {@code HearthEvents.STAGE_SEALED.fire(new StageSealed(...))}; listen with
 * {@code HearthEvents.STAGE_SEALED.add(event -> ...)} from an init class.
 */
public final class HearthEvents {

    /** A stage was sealed with its offering; lighting the Hearth is stage 1. Fired by Stages. */
    public record StageSealed(ServerLevel level, BlockPos hearth, ServerPlayer player, int stage) {
    }

    /** A Comfort kind is present at this Hearth for the first time; {@code placer} when known. Fired by Comfort. */
    public record ComfortKindSeen(ServerLevel level, BlockPos hearth, @Nullable UUID placer, String kind) {
    }

    /** A Character block id is present at this Hearth for the first time; {@code placer} when known. Fired by Comfort. */
    public record CharacterIdSeen(ServerLevel level, BlockPos hearth, @Nullable UUID placer, String blockId) {
    }

    /** A siege was defended: siege kills per player, and every non-AFK player who was in range. Fired by Sieges. */
    public record SiegeDefended(ServerLevel level, BlockPos hearth, Map<UUID, Integer> kills, List<ServerPlayer> present,
            int siegeLevel) {

        /** Without the siege's level (1..5); 0 reads as the lowest. */
        public SiegeDefended(ServerLevel level, BlockPos hearth, Map<UUID, Integer> kills, List<ServerPlayer> present) {
            this(level, hearth, kills, present, 0);
        }
    }

    /** A Feast started or someone joined it: the eaters it warmed. Fired by Tales. */
    public record Feast(ServerLevel level, BlockPos hearth, List<ServerPlayer> eaters) {
    }

    /** A player's Tales were paid in at this Hearth. Fired by Tales. */
    public record TalesPaid(ServerLevel level, BlockPos hearth, ServerPlayer player, double tales) {
    }

    /** A Chronicle page was written (the flat part paid). Fired by Tales. */
    public record ChronicleWritten(ServerLevel level, BlockPos hearth, ServerPlayer author, long pageId, double tales) {
    }

    /**
     * A pool reached a tier it had not been told before (charge, the ceiling or Comfort lifted it).
     * {@code topFeeder} and {@code topSource} are the week's top feeder of the pool and their biggest
     * source (a skill id or a ledger source), both null when nobody fed it this week. Fired by Charge.
     */
    public record TierUp(ServerLevel level, BlockPos hearth, dev.amman.proficiency.hearth.HearthPool pool, int from,
            int to, @Nullable UUID topFeeder, @Nullable String topSource) {
    }

    public static final Signal<TierUp> TIER_UP = new Signal<>("tierUp");

    public static final Signal<StageSealed> STAGE_SEALED = new Signal<>("stageSealed");
    public static final Signal<ComfortKindSeen> COMFORT_KIND_SEEN = new Signal<>("comfortKindSeen");
    public static final Signal<CharacterIdSeen> CHARACTER_ID_SEEN = new Signal<>("characterIdSeen");
    public static final Signal<SiegeDefended> SIEGE_DEFENDED = new Signal<>("siegeDefended");
    public static final Signal<Feast> FEAST = new Signal<>("feast");
    public static final Signal<TalesPaid> TALES_PAID = new Signal<>("talesPaid");
    public static final Signal<ChronicleWritten> CHRONICLE_WRITTEN = new Signal<>("chronicleWritten");

    private HearthEvents() {
    }

    /** One milestone kind: add listeners, fire events. A listener that throws is logged and skipped. */
    public static final class Signal<T> {

        private final HearthHooks.Hook<Consumer<T>> hook;

        Signal(String name) {
            this.hook = new HearthHooks.Hook<>(name);
        }

        public void add(Consumer<T> listener) {
            hook.add(listener);
        }

        public boolean remove(Consumer<T> listener) {
            return hook.remove(listener);
        }

        public void fire(T event) {
            hook.each(listener -> listener.accept(event));
        }
    }
}
