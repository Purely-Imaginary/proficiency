package dev.amman.proficiency.item;

import dev.amman.proficiency.Proficiency;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Runescribe's two enchantments. On 1.21 they are data ({@code data/proficiency/enchantment}); 1.20.1
 * has no data-driven enchantments, so they are registered classes with the same numbers: max
 * level, weight 1 (very rare, which is also the 1.21 anvil cost of 4 for a book), the min and max
 * enchanting costs, and the same item sets. Like on master they sit in no enchanting table, loot or
 * trade pool: a Runescribe book is the only source. What they do is in {@link SpecialItemEvents}.
 */
public final class ProficiencyEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Proficiency.MOD_ID);

    /** Heal 1 per level on a melee hit. 1.21: #enchantable/sharp_weapon (swords and axes), max II. */
    public static final RegistryObject<Enchantment> LIFEDRINKER = ENCHANTMENTS.register("lifedrinker",
            () -> new Bookonly(EnchantmentCategory.WEAPON, 2, 20, 50) {
                @Override
                public boolean canEnchant(ItemStack stack) {
                    return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
                            || super.canEnchant(stack);
                }
            });

    /** Block drops go to your inventory. 1.21: #enchantable/mining, max I. */
    public static final RegistryObject<Enchantment> MAGNETISM = ENCHANTMENTS.register("magnetism",
            () -> new Bookonly(EnchantmentCategory.DIGGER, 1, 15, 50));

    private ProficiencyEnchantments() {
    }

    private static class Bookonly extends Enchantment {

        private final int maxLevel;
        private final int minBase;
        private final int maxBase;

        Bookonly(EnchantmentCategory category, int maxLevel, int minBase, int maxBase) {
            super(Rarity.VERY_RARE, category, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
            this.maxLevel = maxLevel;
            this.minBase = minBase;
            this.maxBase = maxBase;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public int getMinCost(int level) {
            return minBase + 10 * (level - 1);
        }

        @Override
        public int getMaxCost(int level) {
            return maxBase + (maxLevel > 1 ? 10 * (level - 1) : 0);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }

        @Override
        public boolean isDiscoverable() {
            return false;
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return false;
        }
    }
}
