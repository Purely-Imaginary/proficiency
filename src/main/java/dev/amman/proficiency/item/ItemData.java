package dev.amman.proficiency.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Forge 1.20.1 stand-ins for the two 1.21 item components this mod writes.
 *
 * <ul>
 *   <li>{@code minecraft:custom_data} is the compound {@value #CUSTOM} in the stack's NBT, so it can
 *       never clash with vanilla's own keys (Damage, Enchantments, display).</li>
 *   <li>{@code minecraft:lodestone_tracker} (the compass needle's target) is the compound
 *       {@value #TARGET}. The client's needle reads it through {@code ProficiencyClientSetup}.</li>
 * </ul>
 */
public final class ItemData {

    public static final String CUSTOM = "ProficiencyData";
    public static final String TARGET = "ProficiencyTarget";

    private ItemData() {
    }

    /** A copy of the custom data, empty when there is none (like {@code CustomData.copyTag}). */
    public static CompoundTag custom(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(CUSTOM, Tag.TAG_COMPOUND)
                ? root.getCompound(CUSTOM).copy() : new CompoundTag();
    }

    public static boolean hasCustom(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(CUSTOM, Tag.TAG_COMPOUND);
    }

    /** Replaces the custom data; an empty tag removes it. */
    public static void setCustom(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            removeCustom(stack);
        } else {
            stack.getOrCreateTag().put(CUSTOM, tag.copy());
        }
    }

    public static void removeCustom(ItemStack stack) {
        remove(stack, CUSTOM);
    }

    @Nullable
    public static GlobalPos target(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TARGET, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = root.getCompound(TARGET);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) {
            return null;
        }
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                BlockPos.of(tag.getLong("pos")));
    }

    public static boolean hasTarget(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(TARGET, Tag.TAG_COMPOUND);
    }

    public static void setTarget(ItemStack stack, GlobalPos target) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", target.dimension().location().toString());
        tag.putLong("pos", target.pos().asLong());
        stack.getOrCreateTag().put(TARGET, tag);
    }

    public static void clearTarget(ItemStack stack) {
        remove(stack, TARGET);
    }

    private static void remove(ItemStack stack, String key) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(key)) {
            return;
        }
        root.remove(key);
        if (root.isEmpty()) {
            stack.setTag(null);
        }
    }
}
