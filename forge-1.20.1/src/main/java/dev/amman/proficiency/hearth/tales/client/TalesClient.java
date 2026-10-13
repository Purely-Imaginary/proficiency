package dev.amman.proficiency.hearth.tales.client;

import dev.amman.proficiency.hearth.net.HearthTalesPayload;

/**
 * The one door from the payload handler to the client-only tales panel ({@code HearthClient.onTales}
 * calls it by default). The shared {@code HearthClient} interface is loaded on a dedicated server
 * too, so it calls this class only from a method a server never runs.
 */
public final class TalesClient {

    private TalesClient() {
    }

    public static void accept(HearthTalesPayload payload) {
        dev.amman.proficiency.client.TalesPayInHud.accept(payload);
    }
}
