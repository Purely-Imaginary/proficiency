package dev.amman.proficiency.platform.client.event;

import dev.amman.proficiency.platform.bus.Event;

public abstract class ClientTickEvent extends Event {

    public static class Post extends ClientTickEvent {
    }
}
