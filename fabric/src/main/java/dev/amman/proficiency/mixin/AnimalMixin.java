package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.living.BabyEntitySpawnEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge: BabyEntitySpawnEvent between choosing the offspring and spawning it. Canceled resets
 * both parents' love and age and spawns nothing; a replaced child is what spawns.
 */
@Mixin(Animal.class)
public abstract class AnimalMixin {

    @ModifyExpressionValue(method = "spawnChildFromBreeding", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/Animal;getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;"))
    private @Nullable AgeableMob proficiency$babySpawn(@Nullable AgeableMob child, ServerLevel level, Animal partner) {
        Animal self = (Animal) (Object) this;
        BabyEntitySpawnEvent event = NeoForge.EVENT_BUS.post(new BabyEntitySpawnEvent(self, partner, child));
        if (event.isCanceled()) {
            self.setAge(6000);
            partner.setAge(6000);
            self.resetLove();
            partner.resetLove();
            return null;
        }
        return event.getChild();
    }
}
