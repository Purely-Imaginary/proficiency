package dev.amman.proficiency.client;

import dev.amman.proficiency.net.VisitedPayload;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The client's copy of the visited set and the server's structure and dimension lists. */
public final class ClientVisited {

    private static final Set<String> VISITED = new HashSet<>();
    private static List<String> structures = List.of();
    private static List<String> dimensions = List.of();

    private ClientVisited() {
    }

    public static void accept(VisitedPayload payload) {
        if (payload.full()) {
            VISITED.clear();
            structures = payload.structures();
            dimensions = payload.dimensions();
        }
        VISITED.addAll(payload.keys());
    }

    /** Leaving a world ends the set; the next server must not open on the last one's places. */
    public static void clear() {
        VISITED.clear();
        structures = List.of();
        dimensions = List.of();
    }

    public static Set<String> visited() {
        return VISITED;
    }

    /** Empty until a full message arrives, or on a server without the channel. */
    public static List<String> structures() {
        return structures;
    }

    public static List<String> dimensions() {
        return dimensions;
    }
}
