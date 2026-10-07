package dev.amman.proficiency.perk;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A pile of something a perk wants handed over.
 *
 * <p>Items are named by id and resolved at use, never at class-load. Half of these ids belong to
 * Biomes We've Gone, the Aether or the Twilight Forest, and a requirement for a mod that is not
 * installed has to quietly disappear rather than crash the mod or, worse, make a perk impossible.
 */
public record Requirement(ResourceLocation itemId, @Nullable TagKey<Item> tag, int count) {

    public static Requirement of(String id, int count) {
        return new Requirement(ResourceLocation.parse(id), null, count);
    }

    public static Requirement ofTag(String tagPath, int count) {
        return new Requirement(null, ItemTags.create(ResourceLocation.parse(tagPath)), count);
    }

    /** Null when the mod that owns this item is not installed. */
    @Nullable
    public Item resolve() {
        if (tag != null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == Items.AIR ? null : item;
    }

    /** Whether this requirement can be met at all in this instance. */
    public boolean isAvailable() {
        return tag != null || resolve() != null;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag != null) {
            return stack.is(tag);
        }
        Item item = resolve();
        return item != null && stack.is(item);
    }

    public int countIn(Inventory inventory) {
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matches(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** Takes the items. Only called once {@link #countIn} has confirmed they are all there. */
    public void takeFrom(Inventory inventory) {
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matches(stack)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
    }

    public Component displayName() {
        if (tag != null) {
            return Component.translatable("proficiency.tag." + tag.location().getPath());
        }
        Item item = resolve();
        return item == null
                ? Component.literal(itemId.toString())
                : Component.translatable(item.getDescriptionId());
    }
}
