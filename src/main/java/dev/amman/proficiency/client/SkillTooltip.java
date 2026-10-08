package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

/**
 * Adds the skill an item trains to its vanilla tooltip. The item-to-skill mapping is the one the
 * ability key uses ({@link AbilityContext#forItem}); the numbers are the ones the tree header
 * shows ({@link SkillNumbers}). Placeable blocks are skipped, or every stone tooltip would carry a
 * Masonry block. The block is more than two lines, so the details wait behind Shift.
 *
 * <p>Tooltip text is also built by recipe-viewer search indexes, possibly off the render thread and
 * before the server has sent the player's skills. Those calls get nothing, and the Shift state is
 * the one read each client tick, never polled from here.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class SkillTooltip {

    /** The cached lines are rebuilt after this many ticks, or at once when the level changes. */
    private static final long CACHE_TICKS = 10;

    private static final Component HOLD_SHIFT = Component.translatable("proficiency.tooltip.hold_shift")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);

    private static volatile boolean shiftDown;
    private static final Component[][] CACHE = new Component[Skill.VALUES.length][];
    private static final int[] CACHE_LEVEL = new int[Skill.VALUES.length];
    private static final long[] CACHE_TICK = new long[Skill.VALUES.length];

    private SkillTooltip() {
    }

    /** Once per client tick, on the main thread. */
    static void tick() {
        shiftDown = Screen.hasShiftDown();
    }

    static void reset() {
        shiftDown = false;
        Arrays.fill(CACHE, null);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        append(event.getEntity(), event.getItemStack(), event.getToolTip());
    }

    static void append(Player player, ItemStack stack, List<Component> tip) {
        if (!ProficiencyClientConfig.tooltipSkillInfo() || player == null || stack.isEmpty()
                || stack.getItem() instanceof BlockItem || !RenderSystem.isOnRenderThread()
                || !ClientSync.isSynced() || Minecraft.getInstance().screen == null) {
            return;
        }
        Skill skill = AbilityContext.forItem(stack, player);
        if (skill == null) {
            return;
        }
        Component[] lines = lines(ProficiencyAttachments.of(player), skill, player.level().getGameTime());
        tip.add(lines[0]);
        if (shiftDown) {
            for (int i = 1; i < lines.length; i++) {
                tip.add(lines[i]);
            }
        } else {
            tip.add(HOLD_SHIFT);
        }
    }

    /** Header first, then the detail lines; rebuilt twice a second at most, not on every hover frame. */
    private static Component[] lines(PlayerSkills skills, Skill skill, long gameTime) {
        int slot = skill.ordinal();
        int level = skills.level(skill);
        Component[] cached = CACHE[slot];
        if (cached != null && CACHE_LEVEL[slot] == level && gameTime >= CACHE_TICK[slot]
                && gameTime - CACHE_TICK[slot] < CACHE_TICKS) {
            return cached;
        }
        List<Component> details = SkillNumbers.itemLines(skills, skill, gameTime);
        Component[] built = new Component[details.size() + 1];
        built[0] = Component.translatable("proficiency.tooltip.skill",
                Component.translatable(skill.translationKey()), level)
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category())));
        for (int i = 0; i < details.size(); i++) {
            built[i + 1] = details.get(i).copy().withStyle(ChatFormatting.GRAY);
        }
        CACHE[slot] = built;
        CACHE_LEVEL[slot] = level;
        CACHE_TICK[slot] = gameTime;
        return built;
    }
}
