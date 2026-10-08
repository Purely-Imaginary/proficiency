package dev.amman.proficiency.platform.bus;

/** Where {@code NeoForge.EVENT_BUS.post(...)} still points. */
public final class NeoForge {

    public static final EventBus EVENT_BUS = new EventBus();

    private NeoForge() {
    }
}
