package dev.amman.proficiency.client;

/** Whether the server has sent this player's skills since joining. Client only. */
public final class ClientSync {

    private static volatile boolean synced;

    private ClientSync() {
    }

    public static boolean isSynced() {
        return synced;
    }

    public static void markSynced() {
        synced = true;
    }

    public static void reset() {
        synced = false;
    }
}
