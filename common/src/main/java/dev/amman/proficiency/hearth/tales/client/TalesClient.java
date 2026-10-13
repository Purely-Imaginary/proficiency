package dev.amman.proficiency.hearth.tales.client;

import dev.amman.proficiency.hearth.net.HearthTalesPayload;

/**
 * The one door from the shared payload handler to the client-only panel. The shared
 * {@code HearthClient} interface is loaded on a dedicated server too, so it calls this class only
 * from a method a server never runs; the panel's own classes (GUI code) load on first use.
 */
public final class TalesClient {

    private TalesClient() {
    }

    public static void accept(HearthTalesPayload payload) {
        dev.amman.proficiency.client.TalesPayInHud.accept(payload);
    }
}
