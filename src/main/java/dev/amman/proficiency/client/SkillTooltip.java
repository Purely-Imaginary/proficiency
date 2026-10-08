package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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
@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class SkillTooltip {

    /** The cached lines are rebuilt after this many ticks, or at once when the level changes. */
    private static final long CACHE_TICKS = 10;

    static void forceShift(Boolean state) {
        TooltipDetail.force(state);
    }

    /** One cache per layer: [0] short, [1] detailed. */
    private static final Component[][][] CACHE = new Component[2][Skill.VALUES.length][];
    private static final int[][] CACHE_LEVEL = new int[2][Skill.VALUES.length];
    private static final long[][] CACHE_TICK = new long[2][Skill.VALUES.length];

    private SkillTooltip() {
    }

    /** Once per client tick, on the main thread. */
    static void tick() {
        TooltipDetail.tick();
    }

    static void reset() {
        TooltipDetail.reset();
        Arrays.fill(CACHE[0], null);
        Arrays.fill(CACHE[1], null);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        append(event.getEntity(), event.getItemStack(), event.getToolTip());
    }

    /** Widest a tooltip line of ours gets before it wraps. */
    private static final int WRAP_WIDTH = 240;

    static void append(Player player, ItemStack stack, List<Component> tip) {
        if (player == null || stack.isEmpty() || !RenderSystem.isOnRenderThread()
                || Minecraft.getInstance().screen == null) {
            return;
        }
        // The mod's own items (compasses, the mace, the stews) describe themselves in long lines;
        // the vanilla tooltip wraps them on some loaders and not on others, so wrap them here.
        if (TextFit.isOurs(stack)) {
            List<Component> wrapped = TextFit.wrapComponents(Minecraft.getInstance().font, new java.util.ArrayList<>(tip),
                    Math.max(120, Math.min(WRAP_WIDTH, Minecraft.getInstance().screen.width - 40)));
            tip.clear();
            tip.addAll(wrapped);
        }
        if (!ProficiencyClientConfig.tooltipSkillInfo() || stack.getItem() instanceof BlockItem
                || !ClientSync.isSynced()) {
            return;
        }
        Skill skill = AbilityContext.forItem(stack, player);
        if (skill == null) {
            return;
        }
        boolean detailed = TooltipDetail.detailed();
        Component[] lines = lines(ProficiencyAttachments.of(player), skill, player.level().getGameTime(), detailed);
        // Our lines wrap to the window: a vanilla tooltip does not wrap on every loader, and a long
        // translation ran off the screen edge.
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int room = Math.max(120, Math.min(WRAP_WIDTH, Minecraft.getInstance().screen.width - 40));
        List<Component> mine = new java.util.ArrayList<>();
        mine.addAll(Arrays.asList(lines));
        TooltipDetail.withHint(mine, true);
        tip.addAll(TextFit.wrapComponents(font, mine, room));
    }

    /** Header first, then the layer's lines; rebuilt twice a second at most, not on every hover frame. */
    private static Component[] lines(PlayerSkills skills, Skill skill, long gameTime, boolean detailed) {
        int layer = detailed ? 1 : 0;
        int slot = skill.ordinal();
        int level = skills.level(skill);
        Component[] cached = CACHE[layer][slot];
        if (cached != null && CACHE_LEVEL[layer][slot] == level && gameTime >= CACHE_TICK[layer][slot]
                && gameTime - CACHE_TICK[layer][slot] < CACHE_TICKS) {
            return cached;
        }
        List<Component> details = SkillNumbers.itemLines(skills, skill, gameTime, detailed);
        Component[] built = new Component[details.size() + 1];
        built[0] = Component.translatable("proficiency.tooltip.skill",
                Component.translatable(skill.translationKey()), level)
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category())));
        for (int i = 0; i < details.size(); i++) {
            built[i + 1] = details.get(i).copy().withStyle(ChatFormatting.GRAY);
        }
        CACHE[layer][slot] = built;
        CACHE_LEVEL[layer][slot] = level;
        CACHE_TICK[layer][slot] = gameTime;
        return built;
    }
}
