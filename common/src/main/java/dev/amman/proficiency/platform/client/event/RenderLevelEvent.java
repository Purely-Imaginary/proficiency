package dev.amman.proficiency.platform.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.client.Camera;

/**
 * The world render pass, for the few things the mod draws in the world itself (the Hearth's build guide).
 * One event per frame, after the translucent terrain and the particles. The pose stack is the loader's
 * world stack: translate by {@code -camera position} to draw at world coordinates.
 */
public abstract class RenderLevelEvent extends Event {

    private final PoseStack poseStack;
    private final Camera camera;

    protected RenderLevelEvent(PoseStack poseStack, Camera camera) {
        this.poseStack = poseStack;
        this.camera = camera;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Camera getCamera() {
        return camera;
    }

    /** After the translucent terrain: NeoForge's AFTER_PARTICLES stage, Fabric's AFTER_TRANSLUCENT. */
    public static class Post extends RenderLevelEvent {
        public Post(PoseStack poseStack, Camera camera) {
            super(poseStack, camera);
        }
    }
}
