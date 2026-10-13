package dev.amman.proficiency.hearth;

/**
 * Starts the Hearth: registers every lane's listeners. Called once from {@code Proficiency.init}
 * on both loaders, after the registries and before any event fires. Each lane owns its init class
 * and adds its listeners there (docs/HEARTH-CONTRACT.md); the client lane's
 * {@code HearthClientInit} is started from the client entry points instead.
 */
public final class Hearth {

    private static boolean started;

    private Hearth() {
    }

    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        dev.amman.proficiency.hearth.charge.HearthCharge.init();
        dev.amman.proficiency.hearth.stages.HearthStages.init();
        dev.amman.proficiency.hearth.comfort.HearthComfort.init();
        dev.amman.proficiency.hearth.effects.HearthEffects.init();
        dev.amman.proficiency.hearth.tales.HearthTales.init();
        dev.amman.proficiency.hearth.sieges.HearthSieges.init();
        dev.amman.proficiency.homebuilder.HomebuilderInit.init();
        // Last on HEARTH_TICK: the block's glow and feeding colour follow what the lanes did this second.
        HearthLook.init();
        // The client lane's operator-only test command (fake payloads; changes nothing on the server).
        dev.amman.proficiency.hearth.client.HearthDemoCommand.init();
    }
}
