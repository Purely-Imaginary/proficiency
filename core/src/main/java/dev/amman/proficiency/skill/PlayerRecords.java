package dev.amman.proficiency.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's personal records, per skill. A record, not progress: it is saved with the player, copied across a
 * respawn and never touched by a death. Only an operator's {@code /proficiency records ... reset} clears it.
 * The numbers are best-ever values; {@link RecordRules} and {@link RecordTracker} decide when one is beaten.
 */
public final class PlayerRecords {

    /** One skill's bests. */
    public record Entry(float rate, int chain, float grant, int extra) {

        public static final Entry EMPTY = new Entry(0f, 0, 0f, 0);

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("rate", 0f).forGetter(Entry::rate),
                Codec.INT.optionalFieldOf("chain", 0).forGetter(Entry::chain),
                Codec.FLOAT.optionalFieldOf("grant", 0f).forGetter(Entry::grant),
                Codec.INT.optionalFieldOf("extra", 0).forGetter(Entry::extra)
        ).apply(i, (rate, chain, grant, extra) -> new Entry(sane(rate), Math.max(0, chain), sane(grant),
                Math.max(0, extra))));

        public boolean isEmpty() {
            return rate <= 0f && chain <= 0 && grant <= 0f && extra <= 0;
        }

        public float value(RecordMetric metric) {
            return switch (metric) {
                case RATE -> rate;
                case CHAIN -> chain;
                case GRANT -> grant;
                case EXTRA -> extra;
            };
        }
    }

    public static final Codec<PlayerRecords> CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC)
            .xmap(PlayerRecords::fromMap, PlayerRecords::toMap);

    private final float[] rate = new float[Skill.VALUES.length];
    private final int[] chain = new int[Skill.VALUES.length];
    private final float[] grant = new float[Skill.VALUES.length];
    private final int[] extra = new int[Skill.VALUES.length];

    static float sane(float value) {
        return Float.isFinite(value) && value > 0f ? Math.min(value, 1.0e9f) : 0f;
    }

    public Entry get(Skill skill) {
        int i = skill.ordinal();
        return new Entry(rate[i], chain[i], grant[i], extra[i]);
    }

    public void set(Skill skill, Entry entry) {
        int i = skill.ordinal();
        rate[i] = sane(entry.rate());
        chain[i] = Math.max(0, entry.chain());
        grant[i] = sane(entry.grant());
        extra[i] = Math.max(0, entry.extra());
    }

    public float value(Skill skill, RecordMetric metric) {
        return get(skill).value(metric);
    }

    /**
     * Raises a record when {@code value} beats it.
     *
     * @return true when the stored best went up
     */
    public boolean raise(Skill skill, RecordMetric metric, float value) {
        int i = skill.ordinal();
        if (!Float.isFinite(value) || value <= 0f) {
            return false;
        }
        switch (metric) {
            case RATE -> {
                if (value > rate[i]) {
                    rate[i] = sane(value);
                    return true;
                }
            }
            case CHAIN -> {
                if (value > chain[i]) {
                    chain[i] = (int) Math.min(Integer.MAX_VALUE, value);
                    return true;
                }
            }
            case GRANT -> {
                if (value > grant[i]) {
                    grant[i] = sane(value);
                    return true;
                }
            }
            case EXTRA -> {
                if (value > extra[i]) {
                    extra[i] = (int) Math.min(Integer.MAX_VALUE, value);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isEmpty(Skill skill) {
        return get(skill).isEmpty();
    }

    public boolean isEmpty() {
        for (Skill skill : Skill.VALUES) {
            if (!isEmpty(skill)) {
                return false;
            }
        }
        return true;
    }

    /** Clears one skill's records. */
    public void reset(Skill skill) {
        set(skill, Entry.EMPTY);
    }

    public void reset() {
        for (Skill skill : Skill.VALUES) {
            reset(skill);
        }
    }

    public void copyFrom(PlayerRecords other) {
        System.arraycopy(other.rate, 0, rate, 0, rate.length);
        System.arraycopy(other.chain, 0, chain, 0, chain.length);
        System.arraycopy(other.grant, 0, grant, 0, grant.length);
        System.arraycopy(other.extra, 0, extra, 0, extra.length);
    }

    private Map<String, Entry> toMap() {
        Map<String, Entry> map = new HashMap<>();
        for (Skill skill : Skill.VALUES) {
            Entry entry = get(skill);
            if (!entry.isEmpty()) {
                map.put(skill.id(), entry);
            }
        }
        return map;
    }

    private static PlayerRecords fromMap(Map<String, Entry> map) {
        PlayerRecords records = new PlayerRecords();
        map.forEach((id, entry) -> {
            Skill skill = Skill.byId(id);
            if (skill != null) {
                records.set(skill, entry);
            }
        });
        return records;
    }
}
