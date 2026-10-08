package dev.amman.proficiency.platform.bus;

public enum Dist {
    CLIENT, DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }
}
