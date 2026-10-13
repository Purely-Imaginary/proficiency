package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Siege Trophy's item: the tier lives in the vanilla {@code block_state} component, which a
 * {@link BlockItem} applies to the placed block by itself, so a trophy hangs with the look of the
 * ceiling that won it (docs/HEARTH-SPEC.md, "Defended"). The day it was won rides along as custom
 * data for the tooltip. Breaking it keeps the tier (the loot table copies the state back).
 */
public class SiegeTrophyItem extends BlockItem {

    static final String DAY = "proficiency_siege_day";

    public SiegeTrophyItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** A trophy of {@code tier} (3..6), won on in-game day {@code day} (0 or less for none). */
    public static ItemStack of(int tier, long day) {
        ItemStack stack = new ItemStack(HearthBlocks.SIEGE_TROPHY_ITEM.get());
        int t = Math.max(3, Math.min(6, tier));
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(SiegeTrophyBlock.TIER, t));
        if (day > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(DAY, day);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    /** The tier a trophy stack carries (III when it carries none). */
    public static int tier(ItemStack stack) {
        BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
        Integer tier = state == null ? null : state.get(SiegeTrophyBlock.TIER);
        return tier == null ? 3 : tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(SiegeNotices.tierName(tier(stack)).withStyle(ChatFormatting.GOLD));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(DAY)) {
            long day = data.copyTag().getLong(DAY);
            lines.add(Component.translatable(SiegeNotices.KEY + "trophy.day", day).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(SiegeNotices.KEY + "trophy.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
