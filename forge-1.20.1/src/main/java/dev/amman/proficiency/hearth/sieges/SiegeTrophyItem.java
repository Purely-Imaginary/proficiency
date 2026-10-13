package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Siege Trophy's item on Forge 1.20.1. The tier lives in the vanilla {@code BlockStateTag}
 * (1.21.1's {@code block_state} component), which {@link BlockItem} applies to the placed block by
 * itself, so a trophy hangs with the look of the ceiling that won it. The loot table's
 * {@code copy_state} writes the same tag back when it is broken. The day it was won rides along under
 * {@code ProficiencyData} for the tooltip.
 *
 * <p>Foundation part: {@link #of}, {@link #tier} and {@link #day}. The tooltip (tier name, day, how to
 * hang it, from {@code SiegeNotices}) is forge-extras', as common's.
 */
public class SiegeTrophyItem extends BlockItem {

    /** Where the day sits inside the stack's {@code ProficiencyData} compound ({@code item.ItemData}'s). */
    static final String DAY = "proficiency_siege_day";
    private static final String STATE_TAG = BlockItem.BLOCK_STATE_TAG;
    private static final String DATA = dev.amman.proficiency.item.ItemData.CUSTOM;

    public SiegeTrophyItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** A trophy of {@code tier} (3..6), won on in-game day {@code day} (0 or less for none). */
    public static ItemStack of(int tier, long day) {
        ItemStack stack = new ItemStack(HearthBlocks.SIEGE_TROPHY_ITEM.get());
        int t = Math.max(3, Math.min(6, tier));
        stack.getOrCreateTagElement(STATE_TAG).putString(SiegeTrophyBlock.TIER.getName(), Integer.toString(t));
        if (day > 0) {
            stack.getOrCreateTagElement(DATA).putLong(DAY, day);
        }
        return stack;
    }

    /** The tier a trophy stack carries (III when it carries none or a value out of range). */
    public static int tier(ItemStack stack) {
        CompoundTag state = stack.getTagElement(STATE_TAG);
        if (state == null || !state.contains(SiegeTrophyBlock.TIER.getName())) {
            return 3;
        }
        return SiegeTrophyBlock.TIER.getValue(state.getString(SiegeTrophyBlock.TIER.getName())).orElse(3);
    }

    /** The in-game day the trophy was won, or 0 when it carries none. */
    public static long day(ItemStack stack) {
        CompoundTag data = stack.getTagElement(DATA);
        return data == null ? 0L : data.getLong(DAY);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(SiegeNotices.tierName(tier(stack)).withStyle(ChatFormatting.GOLD));
        long day = day(stack);
        if (day > 0) {
            lines.add(Component.translatable(SiegeNotices.KEY + "trophy.day", day).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(SiegeNotices.KEY + "trophy.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
