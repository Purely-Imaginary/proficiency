package dev.amman.proficiency.platform.client.event;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/** A screen was (re)built: add widgets to it. NeoForge's {@code ScreenEvent.Init.Post}, Fabric's {@code ScreenEvents.AFTER_INIT}. */
public abstract class ScreenInitEvent extends Event {

    private final Screen screen;
    private final Consumer<AbstractWidget> adder;

    protected ScreenInitEvent(Screen screen, Consumer<AbstractWidget> adder) {
        this.screen = screen;
        this.adder = adder;
    }

    public Screen getScreen() {
        return screen;
    }

    /** Adds a widget that is drawn, clicked and focusable like the screen's own. */
    public void addWidget(AbstractWidget widget) {
        adder.accept(widget);
    }

    public static class Post extends ScreenInitEvent {
        public Post(Screen screen, Consumer<AbstractWidget> adder) {
            super(screen, adder);
        }
    }
}
