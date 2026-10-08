package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The two layers of every tooltip of the mod. The short layer says the one thing a player needs
 * right now in at most three plain lines and ends in a dim "Hold Shift for details"; the detailed
 * layer is the full text, organised top-down. Shift switches to it, and the client setting
 * {@code tooltip.alwaysDetailed} keeps it on and drops the hint.
 *
 * <p>The Shift state is read once per client tick ({@link #tick}) and never polled from a
 * tooltip: recipe viewers build tooltip text off the render thread.
 */
public final class TooltipDetail {

    private static final Component HINT = Component.translatable("proficiency.tooltip.details")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);

    private static volatile boolean shiftDown;
    /** Layout demo only: pretend Shift is down (true) or up (false); null reads the real key. */
    private static volatile Boolean forced;

    private TooltipDetail() {
    }

    /** Once per client tick, on the main thread. */
    static void tick() {
        shiftDown = Screen.hasShiftDown();
    }

    static void reset() {
        shiftDown = false;
    }

    static void force(Boolean state) {
        forced = state;
    }

    /** Whether tooltips show the detailed layer right now. */
    public static boolean detailed() {
        return ProficiencyClientConfig.tooltipAlwaysDetailed() || (forced != null ? forced : shiftDown);
    }

    /** Whether the hint line is shown: the short layer, and only when there is more to see. */
    public static boolean showHint() {
        return !detailed();
    }

    /** Adds the hint after the short layer's lines, when {@code hasMore}. */
    public static List<Component> withHint(List<Component> lines, boolean hasMore) {
        if (hasMore && showHint()) {
            lines.add(HINT);
        }
        return lines;
    }

    /** The short line when the language file has one, the full line otherwise. */
    public static Component shortOr(String shortKey, String fullKey) {
        return Component.translatable(net.minecraft.locale.Language.getInstance().has(shortKey) ? shortKey : fullKey);
    }

    public static Component hint() {
        return HINT;
    }
}
