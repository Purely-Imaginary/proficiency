package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.net.HearthClients;
import net.minecraftforge.common.MinecraftForge;

/**
 * The client lane's start (docs/HEARTH-CONTRACT.md, lane "client"): installs its
 * {@code HearthClient} with {@code HearthClients.set(...)} and registers its HUD and tick listeners.
 * Owns the HUD glyph, the tracker (with the SkillHud/XpGainDots launch-point refactor), the panel and
 * its ledger tab, the range ring, the cues, the siege panel and the client payload handlers.
 * Called once from {@code ProficiencyClientSetup.init}, on the client only (Forge 1.20.1).
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
        MinecraftForge.EVENT_BUS.register(HearthClientEvents.class);
    }
}
