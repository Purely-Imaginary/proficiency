package dev.amman.proficiency.platform.client.event;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

public abstract class RenderGuiEvent extends Event {

    private final GuiGraphics guiGraphics;
    private final DeltaTracker partialTick;

    protected RenderGuiEvent(GuiGraphics guiGraphics, DeltaTracker partialTick) {
        this.guiGraphics = guiGraphics;
        this.partialTick = partialTick;
    }

    public GuiGraphics getGuiGraphics() {
        return guiGraphics;
    }

    public DeltaTracker getPartialTick() {
        return partialTick;
    }

    /** After the vanilla HUD, as Fabric's {@code HudRenderCallback}. */
    public static class Post extends RenderGuiEvent {
        public Post(GuiGraphics guiGraphics, DeltaTracker partialTick) {
            super(guiGraphics, partialTick);
        }
    }
}
