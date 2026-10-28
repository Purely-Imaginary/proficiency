package dev.amman.proficiency.client;

import dev.amman.proficiency.net.RecordsPayload;
import dev.amman.proficiency.skill.PlayerRecords;
import dev.amman.proficiency.skill.RecordMetric;
import dev.amman.proficiency.skill.RecordRules;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's personal records as the server last told this client (not part of the skill sync: the records have
 * their own optional packet). The Face and the classic skill screen read it; a record toast is queued from here.
 */
public final class ClientRecords {

    private static final PlayerRecords RECORDS = new PlayerRecords();
    /** False when the server runs with {@code records.enabled=false}: it said so with an empty login packet. */
    private static boolean enabled = true;

    private ClientRecords() {
    }

    public static void accept(RecordsPayload payload) {
        if (payload.rows().isEmpty() && payload.toastSkill() < 0) {
            enabled = false;
            RECORDS.reset();
            return;
        }
        enabled = true;
        for (RecordsPayload.Row row : payload.rows()) {
            if (row.skill() >= 0 && row.skill() < Skill.VALUES.length) {
                RECORDS.set(Skill.VALUES[row.skill()], new PlayerRecords.Entry(row.rate(), row.chain(), row.grant(),
                        row.extra()));
            }
        }
        RecordMetric metric = RecordMetric.byOrdinal(payload.toastMetric());
        if (payload.toastSkill() >= 0 && payload.toastSkill() < Skill.VALUES.length && metric != null) {
            RecordToasts.show(Skill.VALUES[payload.toastSkill()], metric, payload.toastValue());
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    public static PlayerRecords.Entry get(Skill skill) {
        return RECORDS.get(skill);
    }

    /** Test and demo hook: sets a skill's records directly. */
    public static void set(Skill skill, PlayerRecords.Entry entry) {
        enabled = true;
        RECORDS.set(skill, entry);
    }

    public static String number(float value) {
        if (value >= 100f) {
            return String.format(Locale.ROOT, "%,d", Math.round(value));
        }
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** The record lines of a Face card, in the order rate, chain, grant, extra; only the ones that exist. */
    public static List<String> lines(Skill skill) {
        PlayerRecords.Entry e = RECORDS.get(skill);
        List<String> lines = new ArrayList<>();
        if (e.rate() > 0f) {
            lines.add(Component.translatable("proficiency.records.rate", number(e.rate())).getString());
        }
        if (e.chain() > 0) {
            lines.add(Component.translatable("proficiency.records.chain", e.chain()).getString());
        }
        if (e.grant() > 0f) {
            lines.add(Component.translatable("proficiency.records.grant", number(e.grant())).getString());
        }
        if (e.extra() > 0) {
            String key = extraKey(skill);
            if (key != null) {
                lines.add(Component.translatable("proficiency.records." + key, e.extra()).getString());
            }
        }
        return lines;
    }

    /** The short parts of the classic screen's one-line summary, joined; empty when there are no records. */
    public static String summary(Skill skill) {
        PlayerRecords.Entry e = RECORDS.get(skill);
        List<String> parts = new ArrayList<>();
        if (e.rate() > 0f) {
            parts.add(Component.translatable("proficiency.records.short.rate", number(e.rate())).getString());
        }
        if (e.chain() > 0) {
            parts.add(Component.translatable("proficiency.records.short.chain", e.chain()).getString());
        }
        if (e.grant() > 0f) {
            parts.add(Component.translatable("proficiency.records.short.grant", number(e.grant())).getString());
        }
        String key = extraKey(skill);
        if (e.extra() > 0 && key != null) {
            parts.add(Component.translatable("proficiency.records.short." + key, e.extra()).getString());
        }
        return parts.isEmpty() ? "" : Component.translatable("proficiency.records.summary",
                String.join("  ·  ", parts)).getString();
    }

    /** "kills" or "catches" for the skills that keep a fourth record. */
    public static String extraKey(Skill skill) {
        return switch (RecordRules.extraKind(skill)) {
            case KILLS_PER_CHAIN -> "kills";
            case CATCHES_PER_MINUTE -> "catches";
            case NONE -> null;
        };
    }
}
