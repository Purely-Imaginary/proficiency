package dev.amman.proficiency.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** An item whose tooltip says what it is for, from {@code <description id>.desc}. */
public class DescribedItem extends Item {

    public DescribedItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /**
     * A described stew that hands the bowl back. 1.21 does this with the food component's
     * {@code usingConvertsTo(BOWL)}; 1.20.1 does it the way vanilla's own stews do (BowlFoodItem).
     */
    public static class Bowl extends DescribedItem {

        public Bowl(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (entity instanceof Player player && player.getAbilities().instabuild) {
                return left;
            }
            if (left.isEmpty()) {
                return new ItemStack(Items.BOWL);
            }
            if (entity instanceof Player player && !player.getInventory().add(new ItemStack(Items.BOWL))) {
                player.drop(new ItemStack(Items.BOWL), false);
            }
            return left;
        }
    }

    /** The same, for a sword. */
    public static class Sword extends SwordItem {

        public Sword(Tier tier, int attackDamageModifier, float attackSpeedModifier, Properties properties) {
            super(tier, attackDamageModifier, attackSpeedModifier, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
