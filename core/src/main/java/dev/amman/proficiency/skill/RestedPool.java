package dev.amman.proficiency.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One skill's rested XP: a pool that fills while you are away from the skill and is spent as a
 * bonus on later grants in it. Pure data, no Minecraft, so every rule is unit tested.
 *
 * <p>The pool has two kinds of part. The idle part came from resting alone and belongs to nobody.
 * A taught part came from a teacher who was nearby and using the skill, and remembers that
 * teacher's UUID, so when the student spends it the teacher can be paid. Spending takes taught
 * parts first, oldest first, then the idle part. The total never goes above the cap the caller
 * passes in.
 */
public final class RestedPool {

    /** Distinct teachers one pool remembers. More than this is a crowd, not a class. */
    public static final int MAX_PARTS = 8;

    /** One teacher's part of the pool. */
    public record Part(UUID teacher, float xp) {
    }

    /** What a {@link #spend} took: the total and the taught parts it came from. */
    public record Spent(float total, List<Part> taught) {
        public static final Spent NONE = new Spent(0f, List.of());
    }

    /** The saved form of one part. The teacher is a UUID string; a bad one drops the part. */
    public record PartState(String teacher, float xp) {
        public static final Codec<PartState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("teacher").forGetter(PartState::teacher),
                Codec.FLOAT.optionalFieldOf("xp", 0f).forGetter(PartState::xp)
        ).apply(i, PartState::new));
    }

    /** The saved form of a pool. Both fields are optional, so an old or hand-edited save loads. */
    public record State(float idle, List<PartState> taught) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("idle", 0f).forGetter(State::idle),
                PartState.CODEC.listOf().optionalFieldOf("taught", List.of()).forGetter(State::taught)
        ).apply(i, State::new));
    }

    private float idle;
    private final List<Part> taught = new ArrayList<>(2);

    public RestedPool() {
    }

    private static float sane(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }

    public float total() {
        float sum = idle;
        for (Part part : taught) {
            sum += part.xp();
        }
        return sum;
    }

    public boolean isEmpty() {
        return idle <= 0f && taught.isEmpty();
    }

    public float idle() {
        return idle;
    }

    public List<Part> taught() {
        return List.copyOf(taught);
    }

    /** What the pool still has room for under {@code cap}. */
    public float room(float cap) {
        return Math.max(0f, cap - total());
    }

    /** Adds rest. Returns what was added (less than asked when the cap is near). */
    public float fillIdle(float amount, float cap) {
        float add = Math.min(sane(amount), room(cap));
        if (add > 0f) {
            idle += add;
        }
        return Math.max(0f, add);
    }

    /**
     * Adds XP a teacher filled. Returns what was added. A teacher not yet in the pool takes a new
     * part, and when {@link #MAX_PARTS} are taken a newcomer adds nothing.
     */
    public float fillTaught(UUID teacher, float amount, float cap) {
        float add = Math.min(sane(amount), room(cap));
        if (!(add > 0f) || teacher == null) {
            return 0f;
        }
        for (int i = 0; i < taught.size(); i++) {
            if (taught.get(i).teacher().equals(teacher)) {
                taught.set(i, new Part(teacher, taught.get(i).xp() + add));
                return add;
            }
        }
        if (taught.size() >= MAX_PARTS) {
            return 0f;
        }
        taught.add(new Part(teacher, add));
        return add;
    }

    /** Takes up to {@code want} out of the pool: taught parts first, oldest first, then idle. */
    public Spent spend(float want) {
        float left = sane(want);
        if (!(left > 0f) || isEmpty()) {
            return Spent.NONE;
        }
        float total = 0f;
        List<Part> from = new ArrayList<>(2);
        for (int i = 0; i < taught.size() && left > 0f; ) {
            Part part = taught.get(i);
            float take = Math.min(left, part.xp());
            left -= take;
            total += take;
            from.add(new Part(part.teacher(), take));
            if (take >= part.xp()) {
                taught.remove(i);
            } else {
                taught.set(i, new Part(part.teacher(), part.xp() - take));
                i++;
            }
        }
        float takeIdle = Math.min(left, idle);
        idle -= takeIdle;
        total += takeIdle;
        return new Spent(total, from);
    }

    /** Cuts the pool down to {@code cap}: the idle part first, then the newest taught parts. */
    public void clampTo(float cap) {
        double over = (double) total() - Math.max(0f, cap);
        if (!(over > 0.0)) {
            return;
        }
        double cut = Math.min(over, idle);
        idle = (float) (idle - cut);
        over -= cut;
        for (int i = taught.size() - 1; i >= 0 && over > 0.0; i--) {
            Part part = taught.get(i);
            double take = Math.min(over, part.xp());
            over -= take;
            if (take >= part.xp()) {
                taught.remove(i);
            } else {
                taught.set(i, new Part(part.teacher(), (float) (part.xp() - take)));
            }
        }
    }

    /** Empties the pool, teacher parts included. Returns the XP that was in it. */
    public float clear() {
        float lost = total();
        idle = 0f;
        taught.clear();
        return lost;
    }

    public RestedPool copy() {
        RestedPool copy = new RestedPool();
        copy.idle = idle;
        copy.taught.addAll(taught);
        return copy;
    }

    public State toState() {
        List<PartState> parts = new ArrayList<>(taught.size());
        for (Part part : taught) {
            parts.add(new PartState(part.teacher().toString(), part.xp()));
        }
        return new State(idle, parts);
    }

    /** Builds a pool from a saved one, dropping broken parts instead of failing the load. */
    public static RestedPool fromState(State state, float cap) {
        RestedPool pool = new RestedPool();
        pool.idle = sane(state.idle());
        for (PartState part : state.taught()) {
            try {
                UUID teacher = UUID.fromString(part.teacher());
                float xp = sane(part.xp());
                if (xp > 0f && pool.taught.size() < MAX_PARTS) {
                    pool.taught.add(new Part(teacher, xp));
                }
            } catch (IllegalArgumentException ignored) {
                // A part with a broken teacher id is a lost part, not a lost save.
            }
        }
        pool.clampTo(cap);
        return pool;
    }

    /** A pool that only knows its total: what the client is sent. */
    public static RestedPool ofTotal(float total) {
        RestedPool pool = new RestedPool();
        pool.idle = sane(total);
        return pool;
    }
}
