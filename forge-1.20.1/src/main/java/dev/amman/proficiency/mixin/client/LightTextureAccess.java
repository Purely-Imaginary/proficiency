package dev.amman.proficiency.mixin.client;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets Dark Sight ask for a new light map when its strength changes. Vanilla rebuilds the light
 * map every tick, but a light-map cache (BadOptimizations, in the Better MC pack) rebuilds it only
 * when the time or a vanilla effect changes, so a Dark Sight change would otherwise wait.
 */
@Mixin(LightTexture.class)
public interface LightTextureAccess {

    @Accessor("updateLightTexture")
    void proficiency$setUpdateLightTexture(boolean update);
}
