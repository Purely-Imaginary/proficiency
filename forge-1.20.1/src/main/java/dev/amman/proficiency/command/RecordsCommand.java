package dev.amman.proficiency.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.PlayerRecords;
import dev.amman.proficiency.skill.RecordsService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /proficiency records <player> [skill] [reset]} (operators): reads a player's personal records, one skill or
 * all, and clears them. Records are never touched by a death or {@code /proficiency reset}; this is the only way to
 * wipe one.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class RecordsCommand {

    private static final SuggestionProvider<CommandSourceStack> SKILLS =
            (context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Skill.VALUES).map(Skill::id), builder);

    private RecordsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("proficiency")
                .then(Commands.literal("records")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> show(context, null))
                                .then(Commands.literal("reset")
                                        .executes(context -> reset(context, null)))
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(SKILLS)
                                        .executes(context -> show(context, skill(context)))
                                        .then(Commands.literal("reset")
                                                .executes(context -> reset(context, skill(context))))))));
    }

    private static Skill skill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "skill");
        Skill skill = Skill.byId(raw.toLowerCase(Locale.ROOT));
        if (skill == null) {
            throw new SimpleCommandExceptionType(Component.literal("Unknown skill: " + raw)).create();
        }
        return skill;
    }

    private static int show(CommandContext<CommandSourceStack> context, Skill only) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        PlayerRecords records = ProficiencyAttachments.of(target).records();
        int shown = 0;
        for (Skill skill : Skill.VALUES) {
            if ((only != null && skill != only) || records.isEmpty(skill)) {
                continue;
            }
            PlayerRecords.Entry e = records.get(skill);
            String line = target.getGameProfile().getName() + " " + skill.id() + ": " + fmt(e.rate()) + " XP/min, chain "
                    + e.chain() + ", biggest grant " + fmt(e.grant()) + " XP"
                    + (e.extra() > 0 ? ", " + extraName(skill) + " " + e.extra() : "");
            context.getSource().sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
        if (shown == 0) {
            context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName()
                    + " has no records" + (only == null ? "." : " in " + only.id() + ".")), false);
        }
        return shown;
    }

    private static int reset(CommandContext<CommandSourceStack> context, Skill only) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        PlayerRecords records = ProficiencyAttachments.of(target).records();
        if (only == null) {
            records.reset();
        } else {
            records.reset(only);
        }
        ProficiencyAttachments.of(target).markDirty();
        RecordsService.sendAll(target);
        String what = only == null ? "all records" : "the " + only.id() + " records";
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + what + " of "
                + target.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static String fmt(float value) {
        return value >= 100f ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String extraName(Skill skill) {
        return switch (skill) {
            case BEASTSLAYING -> "most kills in one chain";
            case FISHING -> "most catches in a minute";
            default -> "extra";
        };
    }
}
