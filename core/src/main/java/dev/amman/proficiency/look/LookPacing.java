package dev.amman.proficiency.look;

/**
 * The timing rules of the Forge 1.20.1 look question (1.36.1), with no Minecraft in them.
 *
 * <p>The server keeps a token bucket per player instead of a fixed gap: a short burst (a sweep of the
 * crosshair across a row of blocks) is answered at once, a sustained flood is answered "retry" instead of
 * being dropped without a word, so the client never waits out a silence. The client asks again at once when
 * its target or its held item changes, and otherwise on a refresh.
 */
public final class LookPacing {

    /** The reply tag key a throttled request gets instead of data: the client asks again a tick later. */
    public static final String RETRY = "retry";

    /** Questions answered in one burst. */
    public static final double BURST = 6.0;
    /** Tokens back per server tick: 0.5 is ten answers a second while someone keeps asking. */
    public static final double REFILL_PER_TICK = 0.5;

    /** The least gap between two client questions (one per tick at most). */
    public static final long CLIENT_MIN_GAP = 1;
    /** A paying block is asked again this often while you keep looking. */
    public static final long REFRESH = 10;
    /** An empty answer (dirt for a stick) is asked again only this often. */
    public static final long QUIET = 20;
    /** A question with no reply for this long is asked again. */
    public static final long PATIENCE = 40;

    private LookPacing() {
    }

    /** One player's bucket. Not thread-safe: use it on the server thread. */
    public static final class Bucket {
        private double tokens = BURST;
        private long last = Long.MIN_VALUE;

        /** True when a question at server tick {@code now} may be answered with data. */
        public boolean take(long now) {
            if (last == Long.MIN_VALUE || now < last) {
                tokens = Math.min(tokens, BURST);
            } else {
                tokens = Math.min(BURST, tokens + (now - last) * REFILL_PER_TICK);
            }
            last = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /**
     * Whether the client sends a question this tick.
     *
     * @param changed   the target block or the held item is not the one the cache was made for (the caller
     *                  has already dropped the cache)
     * @param hasData   an answer for this target is cached
     * @param empty     that answer is empty
     * @param inFlight  a question is out and not yet overdue
     */
    public static boolean shouldAsk(boolean changed, boolean hasData, boolean empty, long now, long answeredAt,
                                    long sentAt, boolean inFlight) {
        boolean spaced = sentAt == Long.MIN_VALUE || now < sentAt || now - sentAt >= CLIENT_MIN_GAP;
        if (!spaced) {
            return false;
        }
        if (changed || !hasData) {
            return !inFlight || changed;
        }
        return !inFlight && now - answeredAt >= (empty ? QUIET : REFRESH);
    }
}
