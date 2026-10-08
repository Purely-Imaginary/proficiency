package dev.amman.proficiency.perk;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Requirement} against the item registry and a player's inventory. Ids resolve at use,
 * never at class-load, so a requirement for a mod that is not installed quietly disappears.
 */
public final class Requirements {

    private static final Map<String, TagKey<Item>> TAGS = new ConcurrentHashMap<>();

    private Requirements() {
    }

    @Nullable
    private static TagKey<Item> tag(Requirement requirement) {
        return requirement.tag() == null ? null
                : TAGS.computeIfAbsent(requirement.tag(), id -> TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
    }

    /** Null for a tag, and when the mod that owns this item is not installed. */
    @Nullable
    public static Item resolve(Requirement requirement) {
        if (requirement.tag() != null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(requirement.itemId()));
        return item == Items.AIR ? null : item;
    }

    /** Whether this requirement can be met at all in this instance. */
    public static boolean isAvailable(Requirement requirement) {
        return requirement.tag() != null || resolve(requirement) != null;
    }

    public static boolean matches(Requirement requirement, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        TagKey<Item> tag = tag(requirement);
        if (tag != null) {
            return stack.is(tag);
        }
        Item item = resolve(requirement);
        return item != null && stack.is(item);
    }

    public static int countIn(Requirement requirement, Inventory inventory) {
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(requirement, stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** Takes the items. Only called once {@link #countIn} has confirmed they are all there. */
    public static void takeFrom(Requirement requirement, Inventory inventory) {
        int remaining = requirement.count();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matches(requirement, stack)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
    }

    public static Component displayName(Requirement requirement) {
        if (requirement.tag() != null) {
            return Component.translatable("proficiency.tag." + requirement.path());
        }
        Item item = resolve(requirement);
        return item == null
                ? Component.literal(requirement.itemId())
                : Component.translatable(item.getDescriptionId());
    }
}
