package dev.amman.proficiency.command;

import dev.amman.proficiency.perk.TalentOutcome;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SurvivalStreak;
import dev.amman.proficiency.skill.XpFeedRecorder;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.recipe.StationEvents;
import dev.amman.proficiency.recipe.WorldRecipe;
import dev.amman.proficiency.recipe.WorldRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class SkillCommand {

    private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(Skill.VALUES).map(Skill::id), builder);

    private static final SuggestionProvider<CommandSourceStack> TALENT_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Talents.of(Skill.MINING).stream().map(Talent::id), builder);

    private SkillCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("proficiency")
                .executes(context -> list(context, context.getSource().getPlayerOrException()))
                .then(Commands.literal("list")
                        .executes(context -> list(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> list(context,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(SKILL_SUGGESTIONS)
                                        .then(Commands.argument("level",
                                                        IntegerArgumentType.integer(0, SkillMath.MAX_LEVEL))
                                                .executes(SkillCommand::set)))))
                .then(Commands.literal("stars")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(SKILL_SUGGESTIONS)
                                        .then(Commands.argument("stars",
                                                        IntegerArgumentType.integer(0, Mastery.MAX_STARS))
                                                .executes(SkillCommand::setStars)))))
                .then(Commands.literal("addxp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(SKILL_SUGGESTIONS)
                                        .then(Commands.argument("amount",
                                                        FloatArgumentType.floatArg(0.0f))
                                                .executes(SkillCommand::addXp)))))
                .then(Commands.literal("perks")
                        .executes(context -> perks(context, null))
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(context -> perks(context, requireSkill(context)))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(context -> invest(context, null))
                                .then(Commands.argument("talent", StringArgumentType.word())
                                        .suggests(TALENT_SUGGESTIONS)
                                        .executes(context -> invest(context,
                                                StringArgumentType.getString(context, "talent"))))))
                .then(Commands.literal("respec")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(SkillCommand::respec)))
                .then(Commands.literal("synergies")
                        .executes(SkillCommand::synergies))
                .then(Commands.literal("recipes")
                        .executes(SkillCommand::recipes))
                .then(Commands.literal("use")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(SkillCommand::useAbility)))
                .then(Commands.literal("top")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(SkillCommand::top)))
                .then(Commands.literal("filltree")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests(SKILL_SUGGESTIONS)
                                        .executes(SkillCommand::fillTree))))
                .then(Commands.literal("procfx")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_SUGGESTIONS)
                                .executes(context -> procFx(context, false))
                                .then(Commands.literal("next")
                                        .executes(context -> procFx(context, true)))))
                .then(Commands.literal("streak")
                        .executes(context -> streak(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> streak(context,
                                        EntityArgument.getPlayer(context, "player")))
                                .then(Commands.argument("stacks", IntegerArgumentType.integer(0, 1000))
                                        .executes(SkillCommand::setStreak))))
                .then(xpFeedCommand())
                .then(dev.amman.proficiency.xp.XpCommand.node())
                .then(dev.amman.proficiency.xp.XpCommand.auditNode())
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SkillCommand::reset)));

        event.getDispatcher().register(root);
        event.getDispatcher().register(Commands.literal("skills")
                .executes(context -> list(context, context.getSource().getPlayerOrException()))
                .then(xpFeedCommand()));
    }

    /**
     * Op test tool. Plays a skill's proc effect now, on the block you look at; with {@code next} the
     * skill's next real roll is certain instead, so the real handler plays it.
     */
    private static int procFx(CommandContext<CommandSourceStack> context, boolean next)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Skill skill = requireSkill(context);
        if (next) {
            dev.amman.proficiency.skill.ProcService.forceNextFor(player, skill, 60_000L);
            context.getSource().sendSuccess(() -> Component.literal("The next " + skill.id()
                    + " proc is certain. It lapses after 60 seconds or when it fires."), false);
            return 1;
        }
        var hit = player.pick(12.0, 0.0f, false);
        net.minecraft.core.BlockPos pos = hit instanceof net.minecraft.world.phys.BlockHitResult block
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? block.getBlockPos() : null;
        dev.amman.proficiency.skill.ProcFxSender.send(player, skill, null, pos);
        return 1;
    }

    private static Skill requireSkill(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "skill");
        Skill skill = Skill.byId(raw.toLowerCase());
        if (skill == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("Unknown skill: " + raw)).create();
        }
        return skill;
    }

    private static int list(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        PlayerSkills skills = ProficiencyAttachments.of(target);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("proficiency.command.header")
                .withStyle(ChatFormatting.GOLD), false);
        for (Skill skill : Skill.VALUES) {
            int level = skills.level(skill);
            if (level == 0 && skills.xp(skill) <= 0) {
                continue;
            }
            int done = (int) Math.floor(skills.xp(skill));
            int need = (int) Math.ceil(SkillMath.xpToNext(level));
            source.sendSuccess(() -> Component.translatable("proficiency.command.line",
                    Component.translatable(skill.translationKey()), level, done, need), false);
        }
        return 1;
    }

    /** Without a skill, only the trees you have actually started are worth printing. */
    private static int perks(CommandContext<CommandSourceStack> context, Skill only)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerSkills skills = ProficiencyAttachments.of(player);
        CommandSourceStack source = context.getSource();

        for (Skill skill : Skill.VALUES) {
            if (only != null && skill != only) {
                continue;
            }
            if (only == null && skills.pointsSpent(skill) == 0 && skills.pointsAvailable(skill) == 0) {
                continue;
            }
            source.sendSuccess(() -> Component.translatable("proficiency.perk.points",
                    Component.translatable(skill.translationKey()),
                    skills.pointsAvailable(skill)).withStyle(ChatFormatting.GOLD), false);
            if (only == null) {
                continue;
            }
            for (Talent talent : Talents.of(skill)) {
                int rank = skills.rank(talent);
                boolean open = skills.check(talent) == TalentOutcome.OK;
                ChatFormatting colour = skills.isFull(talent) ? ChatFormatting.GREEN
                        : rank > 0 ? ChatFormatting.YELLOW
                        : open ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
                source.sendSuccess(() -> Component.translatable("proficiency.command.talent_line",
                        Component.translatable(talent.nameKey()), rank, talent.maxRank(), talent.id())
                        .withStyle(colour), false);
            }
        }
        return 1;
    }

    /**
     * With a talent id, one rank into that node. Without, one rank into the first node that will
     * take it, trunk first: a way to spend points from chat that never needs the panel.
     */
    private static int invest(CommandContext<CommandSourceStack> context, String talentId)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Skill skill = requireSkill(context);
        PlayerSkills skills = ProficiencyAttachments.of(player);

        Talent target = null;
        if (talentId != null) {
            target = Talents.get(skill, talentId.toLowerCase());
            if (target == null) {
                throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                        Component.literal("Unknown talent: " + talentId)).create();
            }
        } else {
            for (Talent talent : Talents.of(skill)) {
                if (skills.check(talent) == TalentOutcome.OK) {
                    target = talent;
                    break;
                }
            }
            if (target == null) {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "proficiency.talent.msg.nothing_open"), false);
                return 0;
            }
        }
        TalentService.Attempt attempt = TalentService.tryInvest(player, target);
        context.getSource().sendSuccess(attempt::message, false);
        if (attempt.ok()) {
            ProficiencyNetwork.sendFullSync(player);
        }
        return attempt.ok() ? 1 : 0;
    }

    private static int respec(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Skill skill = requireSkill(context);
        TalentService.Attempt attempt = TalentService.respec(player, skill);
        context.getSource().sendSuccess(attempt::message, false);
        if (attempt.ok()) {
            ProficiencyNetwork.sendFullSync(player);
        }
        return attempt.ok() ? 1 : 0;
    }

    /** Every station recipe: what goes in and where for the known ones, who teaches the rest. */
    private static int recipes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.recipes.header")
                .withStyle(ChatFormatting.GOLD), false);
        for (WorldRecipe recipe : WorldRecipes.all()) {
            Component name = StationEvents.resultName(recipe);
            Component line;
            if (StationEvents.knows(player, recipe)) {
                MutableComponent parts = Component.empty();
                boolean first = true;
                for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
                    if (!first) {
                        parts.append(", ");
                    }
                    first = false;
                    parts.append(need.getValue() + " ").append(BuiltInRegistries.ITEM
                            .get(ResourceLocation.parse(need.getKey())).getDescription());
                }
                line = Component.translatable("proficiency.recipes.known", name, parts,
                        Component.translatable(recipe.station().translationKey())).withStyle(ChatFormatting.GREEN);
            } else {
                line = Component.translatable("proficiency.recipes.locked", name, StationEvents.teacher(recipe),
                        Component.translatable(recipe.skill().translationKey())).withStyle(ChatFormatting.DARK_GRAY);
            }
            context.getSource().sendSuccess(() -> line, false);
        }
        return WorldRecipes.all().size();
    }

    private static int synergies(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerSkills skills = ProficiencyAttachments.of(player);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("proficiency.synergy.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (Synergy synergy : Synergies.all()) {
            boolean active = Synergies.isActive(skills, synergy);
            source.sendSuccess(() -> Component.translatable(active
                                    ? "proficiency.synergy.line_active" : "proficiency.synergy.line",
                            Component.translatable(synergy.nameKey()), Component.translatable(synergy.descriptionKey()))
                    .withStyle(active ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), false);
            for (Synergy.Need need : synergy.requires()) {
                Talent talent = need.talent();
                int rank = skills.rank(talent);
                source.sendSuccess(() -> Component.translatable("proficiency.synergy.need",
                                Component.translatable(need.skill().translationKey()), Component.translatable(talent.nameKey()), Math.min(rank, need.minRank()), need.minRank())
                        .withStyle(rank >= need.minRank() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                        false);
            }
            if (synergy.grandmastersNeeded() > 0) {
                source.sendSuccess(() -> Component.translatable("proficiency.synergy.need_grandmasters",
                        Math.min(skills.grandmasters(), synergy.grandmastersNeeded()),
                        synergy.grandmastersNeeded()).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return 1;
    }

    private static int useAbility(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Skill skill = requireSkill(context);
        Component refusal = ActiveService.activate(player, skill);
        if (refusal != null) {
            context.getSource().sendSuccess(() -> refusal, false);
            return 0;
        }
        ProficiencyNetwork.sendFullSync(player);
        return 1;
    }

    /**
     * Online players only. Reading every offline player's attachment NBT off disk to answer a chat
     * command is not worth what it costs.
     */
    private static int top(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Skill skill = requireSkill(context);
        CommandSourceStack source = context.getSource();

        List<ServerPlayer> ranked = new ArrayList<>(source.getServer().getPlayerList().getPlayers());
        ranked.sort(Comparator.comparingInt(
                (ServerPlayer player) -> ProficiencyAttachments.of(player).level(skill)).reversed());

        source.sendSuccess(() -> Component.translatable("proficiency.command.top",
                Component.translatable(skill.translationKey())).withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (ServerPlayer player : ranked) {
            int level = ProficiencyAttachments.of(player).level(skill);
            if (level <= 0 || shown++ >= 10) {
                break;
            }
            int place = shown;
            source.sendSuccess(() -> Component.translatable("proficiency.command.top_line",
                    place, player.getDisplayName(), level), false);
        }
        if (shown == 0) {
            source.sendSuccess(() -> Component.translatable("proficiency.command.top_nobody"), false);
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Skill skill = requireSkill(context);
        int level = IntegerArgumentType.getInteger(context, "level");

        ProficiencyAttachments.of(target).setLevel(skill, level);
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.set",
                target.getDisplayName(), Component.translatable(skill.translationKey()), level), true);
        return 1;
    }

    /** Op: puts a skill at level 100 with this many Mastery stars and an empty bar. */
    private static int setStars(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Skill skill = requireSkill(context);
        int stars = IntegerArgumentType.getInteger(context, "stars");

        PlayerSkills skills = ProficiencyAttachments.of(target);
        if (skills.level(skill) < SkillMath.MAX_LEVEL) {
            skills.setLevel(skill, SkillMath.MAX_LEVEL);
        }
        skills.setStars(skill, stars);
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.stars",
                target.getDisplayName(), Component.translatable(skill.translationKey()), stars,
                Mastery.MAX_STARS), true);
        return 1;
    }

    private static int addXp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Skill skill = requireSkill(context);
        float amount = FloatArgumentType.getFloat(context, "amount");

        SkillService.grant(target, skill, amount, "proficiency.xplog.source.command");
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.addxp",
                target.getDisplayName(), amount, Component.translatable(skill.translationKey())), true);
        return 1;
    }

    private static int streak(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        int stacks = SurvivalStreak.stacks(ProficiencyAttachments.of(target));
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.streak",
                target.getDisplayName(), stacks, SurvivalStreak.percent(stacks)), false);
        return stacks;
    }

    /**
     * {@code xpfeed} toggles your own debug feed; {@code xpfeed <player> on|off} sets anyone's,
     * for ops. {@code record}, op only, and {@code record stop} write a CSV of every gain. Built fresh per call because a brigadier node cannot hang off two parents.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> xpFeedCommand() {
        return Commands.literal("xpfeed")
                .executes(context -> {
                    ServerPlayer self = context.getSource().getPlayerOrException();
                    return setXpFeed(context, self, !ProficiencyAttachments.of(self).xpFeed());
                })
                .then(Commands.literal("on").executes(context ->
                        setXpFeed(context, context.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("off").executes(context ->
                        setXpFeed(context, context.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("record")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> record(context, context.getSource().getPlayerOrException()))
                        .then(Commands.literal("stop").executes(context ->
                                stopRecord(context, context.getSource().getPlayerOrException()))))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("record")
                                .executes(context -> record(context, EntityArgument.getPlayer(context, "player")))
                                .then(Commands.literal("stop").executes(context ->
                                        stopRecord(context, EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("on").executes(context ->
                                setXpFeed(context, EntityArgument.getPlayer(context, "player"), true)))
                        .then(Commands.literal("off").executes(context ->
                                setXpFeed(context, EntityArgument.getPlayer(context, "player"), false))));
    }

    private static int record(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (XpFeedRecorder.isRecording(target.getUUID())) {
            context.getSource().sendFailure(Component.translatable(
                    "proficiency.command.xpfeed_record_already", target.getDisplayName()));
            return 0;
        }
        String name = XpFeedRecorder.start(target);
        if (name == null) {
            context.getSource().sendFailure(Component.translatable(
                    "proficiency.command.xpfeed_record_failed", target.getDisplayName()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "proficiency.command.xpfeed_record_on", target.getDisplayName(), name), false);
        return 1;
    }

    private static int stopRecord(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        String name = XpFeedRecorder.stop(target.getUUID());
        if (name == null) {
            context.getSource().sendFailure(Component.translatable(
                    "proficiency.command.xpfeed_record_none", target.getDisplayName()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "proficiency.command.xpfeed_record_off", target.getDisplayName(), name), false);
        return 1;
    }

    private static int setXpFeed(CommandContext<CommandSourceStack> context, ServerPlayer target,
            boolean on) {
        ProficiencyAttachments.of(target).setXpFeed(on);
        context.getSource().sendSuccess(() -> Component.translatable(
                on ? "proficiency.command.xpfeed_on" : "proficiency.command.xpfeed_off",
                target.getDisplayName()), false);
        return on ? 1 : 0;
    }

    /** Sets whole stacks, for testing. Clamped to the cap by the stack math, not here. */
    private static int setStreak(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int stacks = IntegerArgumentType.getInteger(context, "stacks");
        ProficiencyAttachments.of(target).setStreakTicks(stacks * ProficiencyConfig.streakStepTicks());
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.streak_set",
                target.getDisplayName(), stacks), true);
        return 1;
    }

    /** Every node full and paid, no checks. For testing a build, not for handing out. */
    private static int fillTree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Skill skill = requireSkill(context);
        ProficiencyAttachments.of(target).fillTree(skill);
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.filltree",
                target.getDisplayName(), Component.translatable(skill.translationKey())), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ProficiencyAttachments.of(target).reset();
        ProficiencyNetwork.sendFullSync(target);
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.command.reset",
                target.getDisplayName()), true);
        return 1;
    }
}
