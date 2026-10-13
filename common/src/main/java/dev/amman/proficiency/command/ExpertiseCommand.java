package dev.amman.proficiency.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.perk.Expertise;
import dev.amman.proficiency.perk.ExpertiseNode;
import dev.amman.proficiency.perk.ExpertiseService;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /proficiency expertise}: the Expertise tree from chat. Anyone reads their own tree, puts a
 * rank in and respecs; an op fills or empties another player's tree for testing. The Forge 1.20.1
 * line keeps a hand copy.
 */
public final class ExpertiseCommand {

    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Expertise.nodes().stream().map(ExpertiseNode::id), builder);

    private ExpertiseCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("expertise")
                .executes(context -> show(context, context.getSource().getPlayerOrException()))
                .then(Commands.literal("invest")
                        .then(Commands.argument("node", StringArgumentType.word())
                                .suggests(NODE_SUGGESTIONS)
                                .executes(ExpertiseCommand::invest)))
                .then(Commands.literal("respec")
                        .executes(ExpertiseCommand::respec))
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> show(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("fill")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> fill(context, true))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> fill(context, false))));
    }

    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        PlayerSkills skills = ProficiencyAttachments.of(target);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("proficiency.expertise.header",
                skills.expertisePoints(), Expertise.MAX_POINTS, skills.expertiseSpent(), skills.expertiseAvailable())
                .withStyle(ChatFormatting.GOLD), false);
        for (ExpertiseNode node : Expertise.nodes()) {
            int rank = skills.expertiseRank(node);
            boolean open = skills.checkExpertise(node) == TalentOutcome.OK;
            ChatFormatting colour = skills.isExpertiseFull(node) ? ChatFormatting.GREEN
                    : rank > 0 ? ChatFormatting.YELLOW
                    : open ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
            source.sendSuccess(() -> Component.translatable("proficiency.command.talent_line",
                    Component.translatable(node.nameKey()), rank, node.maxRank(), node.id()).withStyle(colour), false);
        }
        return 1;
    }

    private static int invest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "node").toLowerCase(java.util.Locale.ROOT);
        ExpertiseNode node = Expertise.byId(id);
        if (node == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("Unknown Expertise node: " + id)).create();
        }
        TalentService.Attempt attempt = ExpertiseService.tryInvest(player, node);
        context.getSource().sendSuccess(attempt::message, false);
        if (attempt.ok()) {
            ProficiencyNetwork.sendFullSync(player);
        }
        return attempt.ok() ? 1 : 0;
    }

    private static int respec(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TalentService.Attempt attempt = ExpertiseService.respec(player);
        context.getSource().sendSuccess(attempt::message, false);
        if (attempt.ok()) {
            ProficiencyNetwork.sendFullSync(player);
        }
        return attempt.ok() ? 1 : 0;
    }

    /** Op test tool: every node full, or the tree empty, with no checks and no price. */
    private static int fill(CommandContext<CommandSourceStack> context, boolean full) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        PlayerSkills skills = ProficiencyAttachments.of(target);
        if (full) {
            skills.fillExpertise();
        } else {
            skills.respecExpertise();
        }
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable(full
                ? "proficiency.command.expertise_fill" : "proficiency.command.expertise_reset",
                target.getDisplayName()), true);
        return 1;
    }
}
