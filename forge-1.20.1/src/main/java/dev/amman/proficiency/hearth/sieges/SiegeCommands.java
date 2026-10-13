package dev.amman.proficiency.hearth.sieges;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.SiegeRules;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /proficiency hearth siege status|call|start|end <outcome>|clear}, operators only (level 2),
 * at the Hearth whose range holds the command's position. For tests and admins: {@code status}
 * prints the siege fields and the condition with its parts; {@code call} calls a siege as a horn would
 * (no item, no cooldown, it does set "first horn heard"); {@code start} begins a counting-down siege
 * now, night or not; {@code end} ends a running one with that outcome; {@code clear} cancels quietly.
 */
public final class SiegeCommands {

    private static final String K = SiegeNotices.KEY + "command.";

    private SiegeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("proficiency")
                .then(Commands.literal("hearth")
                        .then(Commands.literal("siege")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("status").executes(SiegeCommands::status))
                                .then(Commands.literal("call").executes(context -> call(context, 0))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, SiegeRules.MAX_LEVEL))
                                                .executes(context -> call(context,
                                                        IntegerArgumentType.getInteger(context, "level")))))
                                .then(Commands.literal("start").executes(SiegeCommands::start))
                                .then(Commands.literal("clear").executes(SiegeCommands::clear))
                                .then(Commands.literal("end")
                                        .then(Commands.argument("outcome", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("defended", "doused", "dawn", "abandoned", "unearned"), builder))
                                                .executes(SiegeCommands::end))))));
    }

    private static HearthBlockEntity hearth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos at = HearthRange.hearthAt(level, BlockPos.containing(source.getPosition()));
        HearthBlockEntity hearth = at == null ? null : HearthRange.entity(level, at);
        if (hearth == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.translatable(K + "no_hearth")).create();
        }
        return hearth;
    }

    private static List<ServerPlayer> online(CommandSourceStack source) {
        return source.getServer().getPlayerList().getPlayers();
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        HearthBlockEntity hearth = hearth(context);
        HearthState state = hearth.state();
        HearthData data = Hearths.data();
        long now = level.getGameTime();
        SiegeRules.Facts f = SiegeService.facts(level, hearth.getBlockPos(), hearth, now, online(source));
        long playNow = HearthWorldData.get(source.getServer()).playTicks();
        double hours = state.playHoursSinceSiege(playNow);
        String due = state.siegeDue() == HearthState.NEVER ? "-"
                : SiegeClock.seconds(SiegeClock.expectedStart(state.siegeDue(), now, level.getDayTime()) - now) + " s";
        source.sendSuccess(() -> Component.translatable(K + "status.phase", state.siegePhase().name(), due,
                state.siegeByHorn(), state.postponed(), state.wave(), data.siege().waves(),
                DouseMeter.percent(state.douse(), data.siege())), false);
        String since = Double.isInfinite(hours) ? "-" : String.format(Locale.ROOT, "%.2f", hours);
        source.sendSuccess(() -> Component.translatable(K + "status.hearth", state.sieged(), state.defenceStreak(),
                since, state.ceiling(data)), false);
        String chance = String.format(Locale.ROOT, "%.2f", SiegeRules.chance(hours, data.siege()) * 100.0);
        source.sendSuccess(() -> Component.translatable(K + "status.condition",
                SiegeRules.conditionHolds(f, data.siege()), f.homeAndWarm(), f.activePlayers(), f.maxPoolTier(),
                SiegeRules.cooldownServed(f.playHoursSince(), f.ticksSince(), data.siege()), chance), false);
        return 1;
    }

    /** A siege of {@code siegeLevel} called at the Hearth, as a horn of that level would (0 = the ceiling's level; no stage gate: an operator's test). */
    private static int call(CommandContext<CommandSourceStack> context, int siegeLevel) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        HearthBlockEntity hearth = hearth(context);
        if (hearth.state().siegePhase() != SiegeRules.Phase.IDLE) {
            source.sendFailure(Component.translatable(SiegeNotices.KEY + "horn.busy"));
            return 0;
        }
        List<ServerPlayer> concerned = new ArrayList<>(SiegeService.home(level, hearth.getBlockPos(), online(source)));
        ServerPlayer caller = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (caller != null && !concerned.contains(caller)) {
            concerned.add(0, caller);
        }
        SiegeService.beginCountdown(level, hearth.getBlockPos(), hearth, level.getGameTime(), level.getDayTime(), true,
                concerned, caller, siegeLevel);
        source.sendSuccess(() -> Component.translatable(K + "called", hearth.getBlockPos().toShortString()), true);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        HearthBlockEntity hearth = hearth(context);
        if (hearth.state().siegePhase() != SiegeRules.Phase.COUNTDOWN) {
            source.sendFailure(Component.translatable(K + "not_counting"));
            return 0;
        }
        SiegeService.start(level, hearth.getBlockPos(), hearth, level.getGameTime(), level.getDayTime(), online(source));
        source.sendSuccess(() -> Component.translatable(K + "started", hearth.getBlockPos().toShortString()), true);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        HearthBlockEntity hearth = hearth(context);
        if (hearth.state().siegePhase() != SiegeRules.Phase.ACTIVE) {
            source.sendFailure(Component.translatable(K + "not_running"));
            return 0;
        }
        SiegeRules.Outcome outcome;
        try {
            outcome = SiegeRules.Outcome.valueOf(StringArgumentType.getString(context, "outcome").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(K + "bad_outcome"));
            return 0;
        }
        if (outcome == SiegeRules.Outcome.FIZZLED) {
            source.sendFailure(Component.translatable(K + "bad_outcome"));
            return 0;
        }
        SiegeService.end(level, hearth.getBlockPos(), hearth, outcome, level.getGameTime(), online(source));
        source.sendSuccess(() -> Component.translatable(K + "ended", hearth.getBlockPos().toShortString(),
                outcome.id()), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        HearthBlockEntity hearth = hearth(context);
        SiegeService.cancel(level, hearth.getBlockPos(), hearth, level.getGameTime());
        source.sendSuccess(() -> Component.translatable(K + "cleared", hearth.getBlockPos().toShortString()), true);
        return 1;
    }
}
