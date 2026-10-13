package dev.amman.proficiency.hearth.comfort;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /proficiency hearth comfort}: what the Hearth at your feet counts, for anyone standing in
 * its range (the same numbers as the panel, for a player with no client half, and the answer to "why
 * is my chair not counted"). {@code rescan} (op) scans first instead of waiting for the clock.
 */
public final class ComfortCommand {

    private ComfortCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("proficiency")
                .then(Commands.literal("hearth")
                        .then(Commands.literal("comfort")
                                .executes(context -> report(context, false))
                                .then(Commands.literal("rescan")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> report(context, true))))));
    }

    private static int report(CommandContext<CommandSourceStack> context, boolean rescan)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        HearthBlockEntity hearth = HearthRange.hearthEntityAt(level, player.blockPosition());
        if (hearth == null) {
            context.getSource().sendFailure(Component.translatable("proficiency.hearth.comfort.command.none"));
            return 0;
        }
        if (rescan && !HearthComfort.scanNow(level, hearth)) {
            context.getSource().sendFailure(Component.translatable("proficiency.hearth.comfort.command.unloaded"));
        }
        HearthData data = Hearths.data();
        HearthState state = hearth.state();
        List<Component> present = new ArrayList<>();
        for (String kind : state.comfortKinds()) {
            present.add(name(kind));
        }
        List<Component> missing = new ArrayList<>();
        for (String kind : HearthComfort.missingKinds(state)) {
            missing.add(name(kind));
        }
        Component counted = present.isEmpty() ? Component.translatable("proficiency.hearth.comfort.command.none_yet")
                : ComponentUtils.formatList(present, Component.literal(", "));
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.hearth.comfort.command.comfort",
                state.comfort(), data.comfort().max(), counted), false);
        if (!missing.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("proficiency.hearth.comfort.command.missing",
                    ComponentUtils.formatList(missing, Component.literal(", "))), false);
        }
        int ceiling = state.ceiling(data);
        int next = ceiling < HearthData.TIERS ? data.character().forStage(ceiling + 1) : 0;
        if (next > state.character()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "proficiency.hearth.comfort.command.character.next", state.character(), data.character().max(),
                    next), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("proficiency.hearth.comfort.command.character",
                    state.character(), data.character().max()), false);
        }
        return state.comfort();
    }

    /** A kind's name: the shipped translation, or a readable name made from the id of a kind a pack added. */
    static Component name(String kind) {
        return Component.translatableWithFallback(ComfortKinds.translationKey(kind), ComfortKinds.fallbackName(kind));
    }
}
