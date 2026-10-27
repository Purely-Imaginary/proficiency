package dev.amman.proficiency.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.CodexService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * {@code /skills title}: the Codex titles. Bare shows yours; {@code list} shows every skill's count,
 * {@code list <skill>} the four titles of one skill and what earns each; {@code set <id>} picks an
 * earned title for the tab list; {@code clear} drops it. Cosmetic only.
 */
public final class CodexCommand {

    private static final SuggestionProvider<CommandSourceStack> EARNED = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        return SharedSuggestionProvider.suggest(Codex.all().stream()
                .filter(title -> Codex.earned(skills.visitedKeys(), title)).map(Codex.Title::id), builder);
    };

    private CodexCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("title")
                .executes(CodexCommand::show)
                .then(Commands.literal("list")
                        .executes(CodexCommand::listAll)
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Skill.VALUES).map(Skill::id), b))
                                .executes(CodexCommand::listOne)))
                .then(Commands.literal("set")
                        .then(Commands.argument("title", StringArgumentType.greedyString()).suggests(EARNED)
                                .executes(CodexCommand::set)))
                .then(Commands.literal("clear").executes(CodexCommand::clear));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component name = CodexService.pickedName(ProficiencyAttachments.of(player));
        context.getSource().sendSuccess(() -> name == null
                ? Component.translatable("proficiency.command.title_none")
                : Component.translatable("proficiency.command.title_current", name), false);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PlayerSkills skills = ProficiencyAttachments.of(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.title_list_head")
                .withStyle(ChatFormatting.GOLD), false);
        for (Skill skill : Skill.VALUES) {
            int found = skills.kindsFound(skill);
            int next = Codex.next(found);
            context.getSource().sendSuccess(() -> Component.translatable(next == 0
                    ? "proficiency.command.title_list_done" : "proficiency.command.title_list_line",
                    Component.translatable(skill.translationKey()), found, next)
                    .withStyle(next == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        return Skill.VALUES.length;
    }

    private static int listOne(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Skill skill = Skill.byId(StringArgumentType.getString(context, "skill"));
        if (skill == null) {
            context.getSource().sendFailure(Component.literal("Unknown skill: " + StringArgumentType.getString(context, "skill")));
            return 0;
        }
        PlayerSkills skills = ProficiencyAttachments.of(context.getSource().getPlayerOrException());
        int found = skills.kindsFound(skill);
        int next = Codex.next(found);
        context.getSource().sendSuccess(() -> next == 0
                ? Component.translatable("proficiency.codex.kinds_found_done", found).withStyle(ChatFormatting.GOLD)
                : Component.translatable("proficiency.codex.kinds_found", found, next).withStyle(ChatFormatting.GOLD),
                false);
        for (int milestone : Codex.MILESTONES) {
            Codex.Title title = new Codex.Title(skill, milestone);
            boolean earned = found >= milestone;
            context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.title_line",
                    CodexService.titleName(title), milestone, Component.translatable(earned
                            ? "proficiency.command.title_earned" : "proficiency.command.title_locked"))
                    .withStyle(earned ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        }
        return Codex.MILESTONES.length;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "title");
        Codex.Title title = Codex.parse(id);
        if (title == null) {
            context.getSource().sendFailure(Component.translatable("proficiency.command.title_unknown", id));
            return 0;
        }
        if (!CodexService.pick(player, id)) {
            context.getSource().sendFailure(Component.translatable("proficiency.command.title_locked_fail",
                    CodexService.titleName(title), title.kinds()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.title_current",
                CodexService.titleName(title)), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CodexService.pick(context.getSource().getPlayerOrException(), "");
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.title_none"), false);
        return 1;
    }
}
