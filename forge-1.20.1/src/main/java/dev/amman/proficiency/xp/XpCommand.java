package dev.amman.proficiency.xp;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.amman.proficiency.Proficiency;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /proficiency xpsources <id>} (ops): prints, for a block, item, mob, structure, biome or
 * dimension, which XP rule matched in each place it counts and why the others did not win.
 */
public final class XpCommand {

    private XpCommand() {
    }

    /** {@code /proficiency xpaudit <namespace>}: what a mod's ores, logs, crops and mobs pay. */
    public static LiteralArgumentBuilder<CommandSourceStack> auditNode() {
        return Commands.literal("xpaudit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("namespace", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                BuiltInRegistries.BLOCK.keySet().stream().map(ResourceLocation::getNamespace)
                                        .distinct(), builder))
                        .executes(XpCommand::audit));
    }

    private static final int AUDIT_LINES = 60;

    private static int audit(CommandContext<CommandSourceStack> context) {
        String namespace = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "namespace");
        XpTable table = XpSources.table();
        List<String> problems = new ArrayList<>();
        int ores = 0;
        int logs = 0;
        int crops = 0;
        int mobs = 0;
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(namespace)) {
                continue;
            }
            BlockState state = entry.getValue().defaultBlockState();
            double hardness = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            XpSubject subject = XpSubjects.block(state, hardness);
            boolean ore = subject.hasTag("c:ores") || subject.hasTag("forge:ores") || id.getPath().endsWith("_ore");
            boolean log = subject.hasTag("minecraft:logs");
            boolean crop = subject.hasTag("minecraft:crops") || subject.hasTag("forge:crops")
                    || subject.hasTag("c:crops")
                    || state.getBlock() instanceof net.minecraft.world.level.block.CropBlock;
            XpMatch match = table.match(XpDomain.BREAK, subject);
            if (ore && hardness >= 0) {
                ores++;
                check(problems, "ore", id, match, dev.amman.proficiency.skill.Skill.MINING);
            }
            if (log) {
                logs++;
                check(problems, "log", id, match, dev.amman.proficiency.skill.Skill.WOODCUTTING);
            }
            if (crop) {
                crops++;
                check(problems, "crop", id, match, dev.amman.proficiency.skill.Skill.FARMING);
            }
        }
        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(namespace)) {
                continue;
            }
            double health = XpSubjects.defaultMaxHealth(entry.getValue());
            if (health <= 0) {
                continue;
            }
            mobs++;
            XpMatch match = table.match(XpDomain.KILL, XpSubjects.entity(entry.getValue(), health));
            if (match == null || match.paysNothing()) {
                problems.add("mob " + id + " (health " + (int) health + "): no kill rule, pays nothing");
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(namespace + ": " + ores + " ores, " + logs + " logs, " + crops + " crops, " + mobs
                + " mobs checked, " + problems.size() + " that pay nothing or another skill");
        lines.addAll(problems.subList(0, Math.min(AUDIT_LINES, problems.size())));
        if (problems.size() > AUDIT_LINES) {
            lines.add("... and " + (problems.size() - AUDIT_LINES) + " more");
        }
        for (String line : lines) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
            Proficiency.LOG.info("xpaudit {}", line);
        }
        return problems.size();
    }

    private static void check(List<String> problems, String what, ResourceLocation id, XpMatch match,
            dev.amman.proficiency.skill.Skill expected) {
        if (match == null || match.paysNothing()) {
            problems.add(what + " " + id + ": no break rule, pays nothing");
        } else if (match.rule().skill != expected) {
            problems.add(what + " " + id + ": pays " + match.rule().skill.id() + " (rule " + match.rule().matchText() + ")");
        }
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("xpsources")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                Stream.of(BuiltInRegistries.BLOCK.keySet().stream(),
                                                BuiltInRegistries.ENTITY_TYPE.keySet().stream(),
                                                BuiltInRegistries.ITEM.keySet().stream())
                                        .flatMap(stream -> stream), builder))
                        .executes(XpCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        MinecraftServer server = context.getSource().getServer();
        XpTable table = XpSources.table();
        List<String> lines = new ArrayList<>();
        lines.add(table.ruleCount() + " XP sources from " + table.fileCount() + " files. "
                + "The highest priority wins, then the more specific match, then the later file.");

        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            double hardness = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            XpSubject subject = XpSubjects.block(state, hardness);
            for (XpDomain domain : new XpDomain[] {XpDomain.BREAK, XpDomain.PLACE, XpDomain.FIRST_TIME}) {
                lines.addAll(XpExplain.lines(table, domain, subject));
            }
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            XpSubject subject = XpSubjects.item(BuiltInRegistries.ITEM.get(id));
            for (XpDomain domain : new XpDomain[] {XpDomain.CRAFT, XpDomain.CAST, XpDomain.FIRST_TIME}) {
                lines.addAll(XpExplain.lines(table, domain, subject));
            }
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            XpSubject subject = XpSubjects.entity(BuiltInRegistries.ENTITY_TYPE.get(id),
                    XpSubjects.defaultMaxHealth(BuiltInRegistries.ENTITY_TYPE.get(id)));
            for (XpDomain domain : new XpDomain[] {XpDomain.KILL, XpDomain.KILL_BONUS, XpDomain.BOSS,
                    XpDomain.CAST, XpDomain.FIRST_TIME}) {
                lines.addAll(XpExplain.lines(table, domain, subject));
            }
        }
        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        if (structures.containsKey(id)) {
            // Variants are one discovery, so rules name the canonical id.
            ResourceLocation canonical = ResourceLocation.tryParse(
                    dev.amman.proficiency.event.StructureIds.canonical(id.toString()));
            lines.addAll(XpExplain.lines(table, XpDomain.STRUCTURE, XpSubjects.structure(
                    canonical == null ? id : canonical, structures.getHolderOrThrow(
                            ResourceKey.create(Registries.STRUCTURE, id)))));
        }
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        if (biomes.containsKey(id)) {
            Holder<net.minecraft.world.level.biome.Biome> holder =
                    biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, id));
            lines.addAll(XpExplain.lines(table, XpDomain.BIOME, XpSubjects.biome(id, holder)));
        }
        if (server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) != null) {
            lines.addAll(XpExplain.lines(table, XpDomain.DIMENSION, XpSubjects.dimension(id)));
        }
        if (lines.size() == 1) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Nothing is registered as " + id + " (block, item, mob, structure, biome or dimension)"))
                    .create();
        }
        for (String line : lines) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }
}
