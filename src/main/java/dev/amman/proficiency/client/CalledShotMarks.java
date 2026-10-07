package dev.amman.proficiency.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.net.CalledShotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tactician's Called Shot, seen from the client: a red crosshair floating over each marked mob's
 * head. The server sends it only to the marker and to players within 48 blocks of the mob. The
 * mob also glows red, but glow outlines can fail under shader packs (Iris + Complementary), so
 * this icon is drawn like a name tag, which shader packs keep. It shows through walls, faintly.
 *
 * <p>Client only. The icon goes when the server says the mark ended, when its time runs out
 * here, or when the mob dies or leaves.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class CalledShotMarks {

    private static final ResourceLocation TEXTURE = Proficiency.id("textures/misc/called_shot.png");
    /** Half the icon's width, in blocks. */
    private static final float HALF = 0.28f;
    /** How far above the mob's head. */
    private static final double LIFT = 0.55;

    private record Entry(long until, String marker) {
    }

    /** Entity id to the client game time the mark ends. */
    private static final Map<Integer, Entry> MARKS = new ConcurrentHashMap<>();

    private CalledShotMarks() {
    }

    public static void accept(CalledShotPayload payload) {
        var level = Minecraft.getInstance().level;
        if (payload.ticks() <= 0 || level == null) {
            MARKS.remove(payload.entityId());
            return;
        }
        MARKS.put(payload.entityId(), new Entry(level.getGameTime() + payload.ticks(), payload.marker()));
    }

    /** Whether this entity has a mark icon now. */
    public static boolean marked(int entityId) {
        return MARKS.containsKey(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (MARKS.isEmpty()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            MARKS.clear();
            return;
        }
        long now = level.getGameTime();
        MARKS.entrySet().removeIf(entry -> {
            var entity = level.getEntity(entry.getKey());
            return entry.getValue().until() <= now || !(entity instanceof LivingEntity living) || !living.isAlive();
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKS.clear();
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (MARKS.isEmpty()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Entry entry = MARKS.get(entity.getId());
        Minecraft minecraft = Minecraft.getInstance();
        if (entry == null || minecraft.level == null || !entity.isAlive()) {
            return;
        }
        long left = entry.until() - minecraft.level.getGameTime();
        if (left <= 0) {
            return;
        }
        // Fade out over the last second, and pulse gently so it catches the eye.
        float time = minecraft.level.getGameTime() + event.getPartialTick();
        float fade = Math.min(1f, left / 20f);
        float pulse = 1f + 0.08f * (float) Math.sin(time * 0.3f);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, entity.getBbHeight() + LIFT, 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(pulse, pulse, pulse);
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource buffers = event.getMultiBufferSource();
        // A faint pass that shows through walls, then the full one where it is in plain sight.
        quad(buffers.getBuffer(RenderType.textSeeThrough(TEXTURE)), matrix, (int) (90 * fade));
        quad(buffers.getBuffer(RenderType.text(TEXTURE)), matrix, (int) (255 * fade));
        pose.popPose();
    }

    /** One square facing the camera, drawn both ways round so no culling setting hides it. */
    private static void quad(VertexConsumer out, Matrix4f matrix, int alpha) {
        int light = LightTexture.FULL_BRIGHT;
        vertex(out, matrix, -HALF, HALF, 0f, 0f, alpha, light);
        vertex(out, matrix, -HALF, -HALF, 0f, 1f, alpha, light);
        vertex(out, matrix, HALF, -HALF, 1f, 1f, alpha, light);
        vertex(out, matrix, HALF, HALF, 1f, 0f, alpha, light);

        vertex(out, matrix, HALF, HALF, 1f, 0f, alpha, light);
        vertex(out, matrix, HALF, -HALF, 1f, 1f, alpha, light);
        vertex(out, matrix, -HALF, -HALF, 0f, 1f, alpha, light);
        vertex(out, matrix, -HALF, HALF, 0f, 0f, alpha, light);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float u, float v, int alpha,
            int light) {
        out.addVertex(matrix, x, y, 0f).setColor(255, 255, 255, alpha).setUv(u, v).setLight(light);
    }
}
