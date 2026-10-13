package dev.amman.proficiency.hearth.charge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code /proficiency hearth ledger [pool|all] [week|lifetime]}: the Hearth-keepers table in chat,
 * for the Hearth whose range the player stands in. Operators can read any loaded Hearth with
 * {@code /proficiency hearth ledger at <pos> [pool|all] [week|lifetime]} (the Hearth at that
 * position, or the one whose range holds it). The week is the default window, every pool the
 * default pool; a lone {@code week} or {@code lifetime} reads every pool.
 */
final class ChargeCommands {

    static final String ALL = "all";
    static final String WEEK = "week";
    static final String LIFETIME = "lifetime";
    /** Rows the chat table prints at most. */
    static final int MAX_LINES = 10;

    private static final SuggestionProvider<CommandSourceStack> POOLS = (context, builder) ->
            SharedSuggestionProvider.suggest(Stream.concat(Stream.of(ALL, WEEK, LIFETIME),
                    Stream.of(HearthPool.VALUES).map(HearthPool::id)), builder);
    private static final SuggestionProvider<CommandSourceStack> WINDOWS = (context, builder) ->
            SharedSuggestionProvider.suggest(Stream.of(WEEK, LIFETIME), builder);

    private ChargeCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("proficiency").then(Commands.literal("hearth").then(ledgerNode())));
    }

    static LiteralArgumentBuilder<CommandSourceStack> ledgerNode() {
        return Commands.literal("ledger")
                .executes(context -> ledger(context, false))
                .then(Commands.literal("at")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> ledger(context, true))
                                .then(poolArgument(true))))
                .then(poolArgument(false));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> poolArgument(boolean at) {
        return Commands.argument("pool", StringArgumentType.word()).suggests(POOLS)
                .executes(context -> ledger(context, at))
                .then(Commands.argument("window", StringArgumentType.word()).suggests(WINDOWS)
                        .executes(context -> ledger(context, at)));
    }

    private static int ledger(CommandContext<CommandSourceStack> context, boolean at) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String poolText = argument(context, "pool");
        String windowText = argument(context, "window");
        HearthPool pool = null;
        HearthLedger.Window window = HearthLedger.Window.WEEK;
        if (poolText != null) {
            String word = poolText.toLowerCase(Locale.ROOT);
            if (WEEK.equals(word) || LIFETIME.equals(word)) {
                window = WEEK.equals(word) ? HearthLedger.Window.WEEK : HearthLedger.Window.LIFETIME;
            } else if (!ALL.equals(word)) {
                pool = HearthPool.byId(word);
                if (pool == null) {
                    source.sendFailure(Component.translatable(ChargeText.KEY + "ledger.unknown_pool", poolText,
                            String.join(", ", poolIds())));
                    return 0;
                }
            }
        }
        if (windowText != null) {
            String word = windowText.toLowerCase(Locale.ROOT);
            if (WEEK.equals(word)) {
                window = HearthLedger.Window.WEEK;
            } else if (LIFETIME.equals(word)) {
                window = HearthLedger.Window.LIFETIME;
            } else {
                source.sendFailure(Component.translatable(ChargeText.KEY + "ledger.unknown_window", windowText));
                return 0;
            }
        }

        ServerLevel level = source.getLevel();
        HearthBlockEntity hearth;
        if (at) {
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            hearth = HearthRange.entity(level, pos);
            if (hearth == null) {
                hearth = HearthRange.hearthEntityAt(level, pos);
            }
            if (hearth == null) {
                source.sendFailure(Component.translatable(ChargeText.KEY + "ledger.no_hearth",
                        pos.getX() + " " + pos.getY() + " " + pos.getZ()));
                return 0;
            }
        } else {
            hearth = here(source);
            if (hearth == null) {
                source.sendFailure(Component.translatable(ChargeText.KEY + "ledger.not_home"));
                return 0;
            }
        }
        print(source, hearth, pool, window);
        return 1;
    }

    /** The Hearth whose range the command's player stands in (the index first, then the in-range flag). */
    @Nullable
    private static HearthBlockEntity here(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        HearthBlockEntity hearth = HearthRange.hearthEntityAt(player.serverLevel(), player.blockPosition());
        if (hearth != null && HearthRange.inRange(player.serverLevel(), hearth.getBlockPos(), player.position())) {
            return hearth;
        }
        return HearthRange.homeEntity(player);
    }

    static void print(CommandSourceStack source, HearthBlockEntity hearth, @Nullable HearthPool pool,
            HearthLedger.Window window) {
        HearthLedger ledger = HearthCharge.rollToToday(hearth);
        List<KeeperRows.Row> rows = KeeperRows.of(ledger, pool, window, MAX_LINES);
        double everyone = ledger.grandTotal(pool, window);
        source.sendSuccess(() -> ChargeText.ledgerHeader(pool, window), false);
        if (rows.isEmpty()) {
            source.sendSuccess(() -> ChargeText.ledgerEmpty(window), false);
            return;
        }
        for (int i = 0; i < Math.min(MAX_LINES, rows.size()); i++) {
            int place = i + 1;
            KeeperRows.Row row = rows.get(i);
            source.sendSuccess(() -> ChargeText.ledgerLine(place, row, pool, everyone), false);
        }
        source.sendSuccess(() -> ChargeText.ledgerTotal(everyone), false);
    }

    @Nullable
    private static String argument(CommandContext<CommandSourceStack> context, String name) {
        try {
            return StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> poolIds() {
        List<String> ids = new ArrayList<>();
        ids.add(ALL);
        for (HearthPool pool : HearthPool.VALUES) {
            ids.add(pool.id());
        }
        return ids;
    }
}
