package dev.amman.proficiency.perk;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checking, paying for and granting talent ranks, and telling the player when a synergy wakes. */
public final class TalentService {

    public record Attempt(TalentOutcome outcome, Component message) {
        public boolean ok() {
            return outcome == TalentOutcome.OK;
        }
    }

    private TalentService() {
    }

    /**
     * A node's special counts once the node has any rank, and a synergy's once it is active. Both
     * live under one name so the event code never has to know which kind it is reading.
     */
    public static boolean hasSpecial(Player player, Skill skill, String special) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        for (Talent talent : Talents.of(skill)) {
            if (special.equals(talent.special()) && skills.rank(talent) > 0) {
                return true;
            }
        }
        return Synergies.hasSpecial(skills, special);
    }

    /**
     * Ranks in this skill's tree on nodes carrying {@code special}. Zero when the tree has no such
     * node. The one lookup every rank-scaled talent should use: "5% per rank" is this times 0.05.
     */
    public static int rank(PlayerSkills skills, Skill skill, String special) {
        int ranks = 0;
        for (Talent talent : Talents.of(skill)) {
            if (special.equals(talent.special())) {
                ranks += skills.rank(talent);
            }
        }
        return ranks;
    }

    public static int rank(Player player, Skill skill, String special) {
        return rank(ProficiencyAttachments.of(player), skill, special);
    }

    public static boolean hasSynergy(Player player, String special) {
        return Synergies.hasSpecial(ProficiencyAttachments.of(player), special);
    }

    /** Requirements naming a mod that is not installed are simply not asked for. */
    public static List<Requirement> payableMaterials(Talent talent) {
        List<Requirement> payable = new ArrayList<>();
        for (Requirement requirement : talent.materials()) {
            if (Requirements.isAvailable(requirement)) {
                payable.add(requirement);
            }
        }
        return payable;
    }

    /** The message for a refusal decided by {@link PlayerSkills#check}. */
    public static Component explain(PlayerSkills skills, Talent talent, TalentOutcome outcome) {
        return switch (outcome) {
            case OK -> Component.empty();
            case MAXED -> Component.translatable("proficiency.talent.msg.maxed", Component.translatable(talent.nameKey()));
            case PARENT_NOT_FULL -> {
                Component names = Component.empty();
                boolean first = true;
                for (String parent : talent.parents()) {
                    Talent node = Talents.get(talent.skill(), parent);
                    if (node == null || skills.isFull(node)) {
                        continue;
                    }
                    names = names.copy().append(first ? Component.empty() : Component.literal(", "))
                            .append(Component.translatable(node.nameKey()));
                    first = false;
                }
                yield Component.translatable("proficiency.talent.msg.parent", names);
            }
            case LEVEL_TOO_LOW -> Component.translatable("proficiency.talent.msg.level",
                    talent.requiredLevel(), Component.translatable(talent.skill().translationKey()),
                    skills.level(talent.skill()));
            case NOT_ENOUGH_POINTS -> Component.translatable("proficiency.talent.msg.points",
                    talent.costPerRank(), skills.pointsAvailable(talent.skill()));
            case MISSING_MATERIALS -> Component.translatable("proficiency.perk.missing");
        };
    }

    /** Puts one rank into a node, charging materials on the rank that fills it. */
    public static Attempt tryInvest(ServerPlayer player, Talent talent) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        TalentOutcome check = skills.check(talent);
        if (check != TalentOutcome.OK) {
            return new Attempt(check, explain(skills, talent, check).copy().withStyle(ChatFormatting.RED));
        }

        boolean fills = skills.rank(talent) + 1 == talent.maxRank();
        List<Requirement> materials = fills && !skills.hasPaid(talent)
                ? payableMaterials(talent) : List.of();
        List<Component> missing = new ArrayList<>();
        for (Requirement requirement : materials) {
            int held = Requirements.countIn(requirement, player.getInventory());
            if (held < requirement.count()) {
                missing.add(Component.translatable("proficiency.perk.missing_line",
                        Requirements.displayName(requirement), held, requirement.count()));
            }
        }
        if (!missing.isEmpty()) {
            Component message = Component.translatable("proficiency.perk.missing")
                    .withStyle(ChatFormatting.RED);
            for (Component line : missing) {
                message = message.copy().append(Component.literal("\n")).append(line);
            }
            return new Attempt(TalentOutcome.MISSING_MATERIALS, message);
        }

        Set<String> before = activeIds(skills);
        for (Requirement requirement : materials) {
            Requirements.takeFrom(requirement, player.getInventory());
        }
        if (fills) {
            skills.markPaid(talent);
        }
        skills.addRank(talent);
        announceChanges(player, before, activeIds(skills));

        return new Attempt(TalentOutcome.OK, Component.translatable("proficiency.talent.msg.invested",
                        Component.translatable(talent.nameKey()), skills.rank(talent), talent.maxRank())
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Empties one tree and hands its points back. Costs vanilla experience levels, one per ten
     * points refunded, so it is a decision and not a toggle; materials already paid stay paid.
     */
    public static Attempt respec(ServerPlayer player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int spent = skills.pointsSpent(skill);
        if (spent == 0) {
            return new Attempt(TalentOutcome.MAXED, Component.translatable("proficiency.respec.nothing"));
        }
        int price = Math.max(1, spent / 10);
        if (!player.isCreative() && player.experienceLevel < price) {
            return new Attempt(TalentOutcome.NOT_ENOUGH_POINTS,
                    Component.translatable("proficiency.respec.price", price, player.experienceLevel)
                            .withStyle(ChatFormatting.RED));
        }
        Set<String> before = activeIds(skills);
        if (!player.isCreative()) {
            player.giveExperienceLevels(-price);
        }
        skills.respec(skill);
        announceChanges(player, before, activeIds(skills));
        return new Attempt(TalentOutcome.OK, Component.translatable("proficiency.respec.done",
                Component.translatable(skill.translationKey()), spent).withStyle(ChatFormatting.GREEN));
    }

    private static Set<String> activeIds(PlayerSkills skills) {
        Set<String> ids = new HashSet<>();
        for (Synergy synergy : Synergies.active(skills)) {
            ids.add(synergy.id());
        }
        return ids;
    }

    /** A synergy waking up is the loudest thing a tree can do, so it gets a sound and a line. */
    private static void announceChanges(ServerPlayer player, Set<String> before, Set<String> after) {
        for (String id : after) {
            if (before.contains(id)) {
                continue;
            }
            Synergy synergy = Synergies.byId(id);
            player.sendSystemMessage(Component.translatable("proficiency.synergy.awakened",
                            Component.translatable(synergy.nameKey()).copy().withStyle(ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            player.sendSystemMessage(Component.translatable(synergy.descriptionKey())
                    .withStyle(ChatFormatting.GRAY));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
        for (String id : before) {
            if (!after.contains(id)) {
                player.sendSystemMessage(Component.translatable("proficiency.synergy.faded",
                        Component.translatable(Synergies.byId(id).nameKey())).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
