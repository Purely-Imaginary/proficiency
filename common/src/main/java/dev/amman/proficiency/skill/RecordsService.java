package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.net.RecordsPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Personal records, the game side: each paid grant feeds a per-skill {@link RecordTracker}; a beaten best is stored
 * in the player's {@link PlayerRecords} (saved, never wiped by a death) and reaches the client in a small optional
 * packet, at most every 5 s per player, plus at once with a "new best" toast when the rules allow one.
 */
public final class RecordsService {

    /** The least gap between two record packets that carry no toast. */
    static final long SEND_GAP_TICKS = 100;

    private static final Map<UUID, Map<Skill, RecordTracker>> TRACKERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Skill>> DIRTY = new ConcurrentHashMap<>();
    /** The outcome of each player's last tracked grant, for the GameTests (a toast is a packet nobody can read). */
    private static final Map<UUID, RecordTracker.Outcome> LAST_TOAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SEND = new ConcurrentHashMap<>();

    private RecordsService() {
    }

    /**
     * Called by the grant after the XP is paid. A command grant or a teaching payout is not play and never counts.
     *
     * @param amount the XP the player actually got
     */
    public static void onGrant(ServerPlayer player, Skill skill, @Nullable String source, float amount, boolean play) {
        onGrant(player, skill, source, amount, play, player.level().getGameTime());
    }

    /** As above at an explicit game time: the GameTests use it to cross the first minute and the cooldown. */
    public static void onGrant(ServerPlayer player, Skill skill, @Nullable String source, float amount, boolean play,
            long now) {
        if (!play || !ProficiencyConfig.recordsEnabled()) {
            return;
        }
        try {
            PlayerRecords records = ProficiencyAttachments.of(player).records();
            RecordTracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                    .computeIfAbsent(skill, s -> new RecordTracker());
            boolean kill = source != null && source.startsWith(KillXp.KILL_PREFIX);
            boolean catchLike = skill == Skill.FISHING && (source == null
                    || !source.startsWith(FirstTimeKinds.FIRST_PREFIX));
            RecordTracker.Outcome outcome = tracker.onGrant(records, skill, now, amount,
                    Tempo.chainActions(player, skill), kill, catchLike);
            if (outcome.changed()) {
                DIRTY.computeIfAbsent(player.getUUID(), id -> java.util.Collections.synchronizedSet(
                        EnumSet.noneOf(Skill.class))).add(skill);
            }
            LAST_TOAST.put(player.getUUID(), outcome);
            if (outcome.toast() != null) {
                // The toast carries the row, so the Face is right by the time anyone looks.
                send(player, List.of(row(records, skill)), skill.ordinal(), outcome.toast().ordinal(),
                        outcome.toastValue());
                DIRTY.getOrDefault(player.getUUID(), Set.of()).remove(skill);
                LAST_SEND.put(player.getUUID(), now);
            }
        } catch (RuntimeException ignored) {
            // A record is a courtesy: nothing in here may cost the grant that called it.
        }
    }

    /** Server tick, per player: flushes beaten bests to the client, at most every 5 seconds. */
    public static void tick(ServerPlayer player) {
        Set<Skill> dirty = DIRTY.get(player.getUUID());
        if (dirty == null || dirty.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_SEND.get(player.getUUID());
        if (last != null && now >= last && now - last < SEND_GAP_TICKS) {
            return;
        }
        PlayerRecords records = ProficiencyAttachments.of(player).records();
        List<RecordsPayload.Row> rows = new ArrayList<>();
        synchronized (dirty) {
            for (Skill skill : dirty) {
                rows.add(row(records, skill));
            }
            dirty.clear();
        }
        LAST_SEND.put(player.getUUID(), now);
        send(player, rows, -1, 0, 0f);
    }

    /** Login, and after an operator changed the records: every skill's row, so the client replaces its copy. */
    public static void sendAll(ServerPlayer player) {
        PlayerRecords records = ProficiencyAttachments.of(player).records();
        List<RecordsPayload.Row> rows = new ArrayList<>();
        for (Skill skill : Skill.VALUES) {
            rows.add(row(records, skill));
        }
        DIRTY.remove(player.getUUID());
        send(player, rows, -1, 0, 0f);
    }

    /** The outcome of the player's last tracked grant (a toast metric, or none). Test hook. */
    public static RecordTracker.Outcome lastOutcome(UUID player) {
        return LAST_TOAST.getOrDefault(player, RecordTracker.Outcome.NOTHING);
    }

    public static void forget(UUID player) {
        LAST_TOAST.remove(player);
        TRACKERS.remove(player);
        DIRTY.remove(player);
        LAST_SEND.remove(player);
    }

    private static RecordsPayload.Row row(PlayerRecords records, Skill skill) {
        PlayerRecords.Entry e = records.get(skill);
        return new RecordsPayload.Row(skill.ordinal(), e.rate(), e.chain(), e.grant(), e.extra());
    }

    private static void send(ServerPlayer player, List<RecordsPayload.Row> rows, int toastSkill, int toastMetric,
            float toastValue) {
        ProficiencyNetwork.sendRecords(player, rows, toastSkill, toastMetric, toastValue);
    }
}
