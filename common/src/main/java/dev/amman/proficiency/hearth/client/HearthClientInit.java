package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.net.HearthClients;
import dev.amman.proficiency.platform.bus.NeoForge;

/**
 * The client lane's start (docs/HEARTH-CONTRACT.md, lane "client"): installs its
 * {@code HearthClient} with {@code HearthClients.set(...)} and registers its HUD and tick listeners.
 * Owns the HUD glyph, the tracker (with the SkillHud/XpGainDots launch-point refactor), the panel and
 * its ledger tab, the range ring, the cues, the siege panel and the client payload handlers.
 * Called once from {@code Subscribers.registerClient}, on the client only.
 */
public final class HearthClientInit {

    private static boolean started;

    private HearthClientInit() {
    }

    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        HearthClients.set(new HearthClientImpl());
        NeoForge.EVENT_BUS.register(HearthClientEvents.class);
    }
}
