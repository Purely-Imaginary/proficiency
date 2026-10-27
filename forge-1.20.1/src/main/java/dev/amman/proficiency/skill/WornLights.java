package dev.amman.proficiency.skill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Whether a living entity wears a light in an accessory slot: a lantern on the belt from Accessorify or
 * Immersive Lanterns (Accessories, Pandowo), or any light in a Curios slot (Reclamation). Nightwalker treats
 * a worn light like a held one. Both APIs are called by reflection, so neither mod is a dependency; without
 * them (Lesznica) this is always false.
 */
final class WornLights {

    /** One accessory API: a static lookup of the entity's inventory and its {@code isEquipped(Predicate)}. */
    private record Api(Method lookup, Method isEquipped) {

        boolean wears(LivingEntity entity, Predicate<ItemStack> light) throws ReflectiveOperationException {
            Object inventory = unwrap(lookup.invoke(null, entity));
            return inventory != null && (boolean) isEquipped.invoke(inventory, light);
        }
    }

    private static boolean resolved;
    private static Api accessories;
    private static Api curios;

    private WornLights() {
    }

    /** Whether the entity wears a stack that {@code light} accepts in any accessory slot. */
    static boolean wears(LivingEntity entity, Predicate<ItemStack> light) {
        if (!resolved) {
            resolved = true;
            accessories = api("io.wispforest.accessories.api.AccessoriesCapability", "getOptionally",
                    "io.wispforest.accessories.api.AccessoriesCapability");
            curios = api("top.theillusivec4.curios.api.CuriosApi", "getCuriosInventory",
                    "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
        }
        return wears(accessories, entity, light) || wears(curios, entity, light);
    }

    private static boolean wears(Api api, LivingEntity entity, Predicate<ItemStack> light) {
        if (api == null) {
            return false;
        }
        try {
            return api.wears(entity, light);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Api api(String owner, String lookup, String inventory) {
        try {
            ClassLoader loader = WornLights.class.getClassLoader();
            Method find = Class.forName(owner, true, loader).getMethod(lookup, LivingEntity.class);
            Method equipped = Class.forName(inventory, true, loader).getMethod("isEquipped", Predicate.class);
            return new Api(find, equipped);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** An {@link Optional} or a Forge {@code LazyOptional} (through its {@code resolve()}) to its value, or null. */
    private static Object unwrap(Object found) throws ReflectiveOperationException {
        if (found instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        if (found == null) {
            return null;
        }
        Object resolved = found.getClass().getMethod("resolve").invoke(found);
        return resolved instanceof Optional<?> optional ? optional.orElse(null) : null;
    }
}
