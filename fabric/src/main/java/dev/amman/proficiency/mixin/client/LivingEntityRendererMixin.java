package dev.amman.proficiency.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.amman.proficiency.client.CalledShotMarks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's RenderLivingEvent.Post: the end of LivingEntityRenderer.render. Draws Called Shot's icon. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void proficiency$calledShot(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        CalledShotMarks.render(entity, partialTick, pose, buffers);
    }
}
