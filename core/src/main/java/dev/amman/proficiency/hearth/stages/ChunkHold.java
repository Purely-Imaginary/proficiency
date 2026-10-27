package dev.amman.proficiency.hearth.stages;

/**
 * Whether a running self-build keeps the Hearth's chunk loaded (idea 16). Pure: the game says whether the
 * feature is on, whether a job runs and how long it has been stuck; this says keep or let go. A job that waits
 * longer than the limit (a chest nobody fills) lets the chunk go; it takes the chunk again the moment it can place
 * a block (a player came back and stocked it).
 */
public final class ChunkHold {

    public static final int DEFAULT_WAIT_LIMIT_MINUTES = 30;

    private ChunkHold() {
    }

    /**
     * @param enabled           {@code hearth.buildKeepsChunkLoaded}
     * @param running           a job is on (not stopped, not finished)
     * @param waitedSeconds     seconds the job has waited without placing a block; 0 or less while it is building
     * @param waitLimitMinutes  {@code hearth.buildChunkWaitLimitMinutes}
     */
    public static boolean keep(boolean enabled, boolean running, long waitedSeconds, int waitLimitMinutes) {
        if (!enabled || !running) {
            return false;
        }
        return waitedSeconds <= (long) Math.max(0, waitLimitMinutes) * 60L;
    }
}
