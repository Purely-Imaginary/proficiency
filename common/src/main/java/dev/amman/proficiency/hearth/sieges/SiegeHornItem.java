package dev.amman.proficiency.hearth.sieges;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * {@code proficiency:siege_horn} .. {@code siege_horn_5}: the five Siege Horns, one per siege level
 * (I Siege Horn, II War Horn, III Battle Horn, IV Doom Horn, V Horn of the Warhost). Each crafts from
 * the one before. A horn of level L needs a Hearth at ceiling {@link
 * dev.amman.proficiency.hearth.SiegeRules#ceilingForLevel}(L) and calls a siege of level L. Blown in
 * range of such a Hearth, it calls that siege for the
 * next nightfall at least {@code hornMinDelaySeconds} away, and is used up (docs/HEARTH-SPEC.md, "The
 * Siege Horn"). It skips the Warmth condition and the roll and needs only
 * {@code hornCooldownPlayHours} of play since the last siege there. The first horn at a Hearth is
 * what lets sieges come there on their own afterwards. A refusal says why on the action bar and keeps
 * the horn. Crafted from a goat horn, two gold ingots and an iron ingot ({@code recipe/siege_horn.json}).
 */
public class SiegeHornItem extends Item {

    /** The siege level this horn calls, 1..5. */
    private final int level;

    public SiegeHornItem(int level, Item.Properties properties) {
        super(properties);
        this.level = Math.max(1, Math.min(dev.amman.proficiency.hearth.SiegeRules.MAX_LEVEL, level));
    }

    public int level() {
        return level;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (SiegeService.blowHorn(server, this.level) != HornCheck.Result.OK) {
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        stack.consume(1, player);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(SiegeNotices.hornLevelLine(this.level).withStyle(ChatFormatting.GOLD));
        lines.add(SiegeNotices.hornFoesLine(this.level).withStyle(ChatFormatting.RED));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.proficiency.siege_horn.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
