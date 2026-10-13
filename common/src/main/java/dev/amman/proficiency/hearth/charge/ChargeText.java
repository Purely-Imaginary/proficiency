package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The charge lane's player-facing text: the tier-up line and the ledger table. Pool names wear the
 * pool's colour ({@link PoolColors}), so the chat table reads like the panel tab; the mod's own font
 * glyphs (U+E018 the full Warmth flame, U+E01E the house, U+E019 the siege keep) head the lines, drawn
 * white because a glyph carries its own colours.
 */
public final class ChargeText {

    static final String KEY = "proficiency.hearth.charge.";

    /** The Warmth glyph at full flame: the Hearth's mark on a tier-up line. */
    public static final String FLAME = "";
    /** The house glyph: the Hearth-keepers header. */
    public static final String HOUSE = "";
    /** The siege glyph, before the siege counts in a ledger hover. */
    public static final String SIEGE = "";

    /** Cells of a ledger bar; each is one "|", so the bar is 32 GUI pixels wide. */
    public static final int BAR_CELLS = 16;

    private ChargeText() {
    }

    public static MutableComponent glyph(String glyph) {
        return Component.literal(glyph).withStyle(ChatFormatting.WHITE);
    }

    /** A pool's name in its colour. */
    public static MutableComponent pool(HearthPool pool) {
        return Component.translatable(pool.translationKey())
                .withStyle(style -> style.withColor(TextColor.fromRgb(PoolColors.rgb(pool))));
    }

    /** A ledger source: a skill's name, or one of the Hearth's own sources (siege, Tales, Chronicle, Feasts). */
    public static MutableComponent source(@Nullable String source) {
        if (source == null || source.isEmpty()) {
            return Component.literal("?");
        }
        Skill skill = Skill.byId(source);
        if (skill != null) {
            return Component.translatable(skill.translationKey());
        }
        return switch (source) {
            case HearthLedger.SIEGE, HearthLedger.TALES, HearthLedger.CHRONICLE, HearthLedger.FEAST ->
                    Component.translatable(KEY + "source." + source);
            default -> Component.literal(source);
        };
    }

    /** Charge as players read it: one decimal under 10, whole numbers with spaced thousands above. */
    public static String number(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.abs(value) < 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%,d", Math.round(value)).replace(',', ' ');
    }

    /**
     * "Hearty reached III, carried by Rowan's Farming": the tier-up line to players in range. With
     * nobody's charge this week it names only the pool and the tier. The hover lists the week's top
     * three feeders of the pool.
     */
    public static MutableComponent tierUp(HearthPool pool, int tier, @Nullable KeeperRows.Top top,
            List<KeeperRows.Row> week) {
        MutableComponent line = top == null
                ? Component.translatable(KEY + "tier_up.alone", pool(pool), HearthRules.roman(tier))
                : Component.translatable(KEY + "tier_up", pool(pool), HearthRules.roman(tier),
                        Component.literal(top.name()).withStyle(ChatFormatting.WHITE), source(top.source()));
        MutableComponent out = Component.empty().append(glyph(FLAME)).append(" ").append(line)
                .withStyle(tier >= dev.amman.proficiency.hearth.HearthData.TIERS
                        ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
        if (!week.isEmpty()) {
            MutableComponent hover = Component.translatable(KEY + "tier_up.hover").withStyle(ChatFormatting.GRAY);
            int shown = 0;
            for (KeeperRows.Row row : week) {
                if (shown++ >= 3) {
                    break;
                }
                hover.append("\n").append(Component.translatable(KEY + "tier_up.hover_line",
                        Component.literal(row.name()).withStyle(ChatFormatting.WHITE),
                        Component.literal(number(row.total())).withStyle(ChatFormatting.YELLOW)));
            }
            out.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return out;
    }

    /** The table's header: house glyph, "Hearth-keepers", the pool (or every pool) and the window. */
    public static MutableComponent ledgerHeader(@Nullable HearthPool pool, HearthLedger.Window window) {
        Component which = pool == null ? Component.translatable(KEY + "ledger.every_pool") : pool(pool);
        Component when = Component.translatable(KEY + (window == HearthLedger.Window.WEEK
                ? "ledger.week" : "ledger.lifetime"));
        return Component.translatable(KEY + "ledger.header", glyph(HOUSE), which, when)
                .withStyle(ChatFormatting.GOLD);
    }

    /**
     * One player's line: place, name, total, a bar, and "mostly" their top three sources. For every
     * pool the bar is stacked in the pool colours (their share per pool); for one pool it is their
     * share of everyone's total in that pool. The hover gives the per-pool numbers and siege counts.
     */
    public static MutableComponent ledgerLine(int place, KeeperRows.Row row, @Nullable HearthPool pool,
            double everyone) {
        MutableComponent bar = Component.empty();
        if (pool == null) {
            int[] cells = KeeperRows.cells(row.perPool(), BAR_CELLS);
            for (HearthPool p : HearthPool.VALUES) {
                if (cells[p.ordinal()] > 0) {
                    bar.append(Component.literal("|".repeat(cells[p.ordinal()]))
                            .withStyle(style -> style.withColor(TextColor.fromRgb(PoolColors.rgb(p)))));
                }
            }
        } else {
            int filled = everyone > 0 ? (int) Math.round(Math.min(1.0, row.total() / everyone) * BAR_CELLS) : 0;
            filled = Math.max(row.total() > 0 ? 1 : 0, filled);
            bar.append(Component.literal("|".repeat(filled))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(PoolColors.rgb(pool)))));
            if (filled < BAR_CELLS) {
                bar.append(Component.literal("|".repeat(BAR_CELLS - filled)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        MutableComponent line = Component.translatable(KEY + "ledger.line", place,
                Component.literal(row.name()).withStyle(ChatFormatting.WHITE),
                Component.literal(number(row.total())).withStyle(ChatFormatting.YELLOW), bar);
        List<HearthLedger.SourceTotal> sources = row.sources();
        if (!sources.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < Math.min(3, sources.size()); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(source(sources.get(i).source()));
            }
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(KEY + "ledger.mostly", names).withStyle(ChatFormatting.GRAY));
        }
        MutableComponent hover = Component.literal(row.name()).withStyle(ChatFormatting.WHITE);
        for (HearthPool p : HearthPool.VALUES) {
            double value = row.perPool()[p.ordinal()];
            if (value > 0) {
                hover.append("\n").append(Component.translatable(KEY + "ledger.hover.pool", pool(p),
                        Component.literal(number(value)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
            }
        }
        if (row.siegeKills() > 0 || row.siegesDefended() > 0) {
            hover.append("\n").append(glyph(SIEGE)).append(" ").append(Component.translatable(KEY + "ledger.hover.siege",
                    row.siegeKills(), row.siegesDefended()).withStyle(ChatFormatting.GRAY));
        }
        return line.withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    public static MutableComponent ledgerEmpty(HearthLedger.Window window) {
        return Component.translatable(KEY + (window == HearthLedger.Window.WEEK
                ? "ledger.empty.week" : "ledger.empty.lifetime")).withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent ledgerTotal(double total) {
        return Component.translatable(KEY + "ledger.total",
                Component.literal(number(total)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY);
    }
}
