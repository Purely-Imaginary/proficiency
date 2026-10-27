package dev.amman.proficiency.perk;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Buying Expertise ranks, emptying the tree, and telling a player when a level or a star earned a
 * new point. The rules themselves are pure and live in {@link PlayerSkills#checkExpertise}; this
 * class adds the messages, the sounds and the vanilla XP a respec costs. The Forge 1.20.1 line
 * keeps a hand copy.
 */
public final class ExpertiseService {

    private ExpertiseService() {
    }

    /** The message for a refusal decided by {@link PlayerSkills#checkExpertise}. */
    public static Component explain(PlayerSkills skills, ExpertiseNode node, TalentOutcome outcome) {
        return switch (outcome) {
            case MAXED -> Component.translatable("proficiency.talent.msg.maxed", Component.translatable(node.nameKey()));
            case PARENT_NOT_FULL -> {
                Component names = Component.empty();
                boolean first = true;
                for (String parent : node.parents()) {
                    ExpertiseNode before = Expertise.byId(parent);
                    if (before == null || skills.isExpertiseFull(before)) {
                        continue;
                    }
                    names = names.copy().append(first ? Component.empty() : Component.literal(", "))
                            .append(Component.translatable(before.nameKey()));
                    first = false;
                }
                yield Component.translatable("proficiency.talent.msg.parent", names);
            }
            case NOT_ENOUGH_POINTS -> Component.translatable("proficiency.expertise.msg.points",
                    node.costPerRank(), skills.expertiseAvailable());
            default -> Component.empty();
        };
    }

    /** Puts one rank into an Expertise node, if the server agrees. */
    public static TalentService.Attempt tryInvest(ServerPlayer player, ExpertiseNode node) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        TalentOutcome check = skills.checkExpertise(node);
        if (check != TalentOutcome.OK) {
            return new TalentService.Attempt(check, explain(skills, node, check).copy().withStyle(ChatFormatting.RED));
        }
        skills.addExpertiseRank(node);
        dev.amman.proficiency.skill.ProficiencyAdvancements.check(player);
        if (skills.isExpertiseFull(node) && node.kind() == Talent.Kind.CAPSTONE) {
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.1f);
        }
        return new TalentService.Attempt(TalentOutcome.OK, Component.translatable("proficiency.talent.msg.invested",
                        Component.translatable(node.nameKey()), skills.expertiseRank(node), node.maxRank())
                .withStyle(ChatFormatting.GREEN));
    }

    /** What a respec costs this player now, in vanilla XP levels: nothing at their own lit Hearth. */
    public static int respecPrice(ServerPlayer player) {
        int spent = ProficiencyAttachments.of(player).expertiseSpent();
        return atHome(player) ? 0 : Expertise.respecPrice(spent);
    }

    private static boolean atHome(ServerPlayer player) {
        try {
            return dev.amman.proficiency.hearth.HearthRange.isHome(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Empties the whole tree and hands every point back. Costs vanilla experience levels, one per
     * ten points, as a talent respec does; free at the player's own lit Hearth.
     */
    public static TalentService.Attempt respec(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int spent = skills.expertiseSpent();
        if (spent == 0) {
            return new TalentService.Attempt(TalentOutcome.MAXED,
                    Component.translatable("proficiency.expertise.respec.nothing"));
        }
        int price = respecPrice(player);
        if (price > 0 && !player.isCreative() && player.experienceLevel < price) {
            return new TalentService.Attempt(TalentOutcome.NOT_ENOUGH_POINTS,
                    Component.translatable("proficiency.respec.price", price, player.experienceLevel)
                            .withStyle(ChatFormatting.RED));
        }
        if (price > 0 && !player.isCreative()) {
            player.giveExperienceLevels(-price);
        }
        skills.respecExpertise();
        return new TalentService.Attempt(TalentOutcome.OK, Component.translatable(price > 0
                ? "proficiency.expertise.respec.done" : "proficiency.expertise.respec.done_home", spent)
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * After a grant: if the player now has more Expertise points than {@code before}, say so with a
     * chat line and a soft chime. Called with the count from before the grant.
     */
    public static void noticeEarned(ServerPlayer player, int before) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int now = skills.expertisePoints();
        if (now <= before) {
            return;
        }
        player.sendSystemMessage(Component.translatable("proficiency.expertise.earned",
                        Component.translatable("proficiency.expertise.title").withStyle(ChatFormatting.BOLD),
                        now, Expertise.MAX_POINTS, skills.expertiseAvailable())
                .withStyle(ChatFormatting.AQUA));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.9f);
    }
}
