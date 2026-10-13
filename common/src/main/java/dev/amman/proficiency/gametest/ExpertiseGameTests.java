package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.perk.Expertise;
import dev.amman.proficiency.perk.ExpertiseNode;
import dev.amman.proficiency.perk.ExpertiseService;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** The Expertise tree on a real player: buying through the service, the effect in the modifier, the respec. */
public final class ExpertiseGameTests {

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public ExpertiseGameTests() {
    }

    /** Five skills at 100: five Expertise points. */
    private static PlayerSkills fivePoints(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        for (Skill skill : new Skill[] {Skill.MINING, Skill.FARMING, Skill.RUNNING, Skill.COOKING, Skill.WAYFARING}) {
            skills.setLevel(skill, SkillMath.MAX_LEVEL);
        }
        skills.setLevel(Skill.SWORDS, 40);
        return skills;
    }

    @GameTest(template = "proficiency:empty")
    public static void investingThroughTheServiceRaisesThePerkModifier(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerSkills skills = fivePoints(player);
        helper.assertTrue(skills.expertisePoints() == 5, "five skills at 100 should give 5 points, got "
                + skills.expertisePoints());
        double bonusBefore = skills.perkModifier(Skill.SWORDS, PerkEffect.BONUS);
        double xpBefore = skills.perkModifier(Skill.MINING, PerkEffect.XP_RATE);

        ExpertiseNode root = Expertise.byId("breadth");
        ExpertiseNode martial = Expertise.byId("martial");
        helper.assertTrue(ExpertiseService.tryInvest(player, martial).outcome() == TalentOutcome.PARENT_NOT_FULL,
                "a branch node must wait for the trunk");
        for (int i = 0; i < root.maxRank(); i++) {
            helper.assertTrue(ExpertiseService.tryInvest(player, root).ok(), "trunk rank " + (i + 1) + " refused");
        }
        helper.assertTrue(ExpertiseService.tryInvest(player, martial).ok(), "Martial rank 1 refused");
        helper.assertTrue(ExpertiseService.tryInvest(player, martial).outcome() == TalentOutcome.NOT_ENOUGH_POINTS,
                "a sixth point was spent out of five");

        double bonusAfter = skills.perkModifier(Skill.SWORDS, PerkEffect.BONUS);
        helper.assertTrue(Math.abs(bonusAfter - bonusBefore * 1.02) < 1e-9,
                "Martial 1 should make Swords' passive x1.02: " + bonusBefore + " -> " + bonusAfter);
        double xpAfter = skills.perkModifier(Skill.MINING, PerkEffect.XP_RATE);
        helper.assertTrue(Math.abs(xpAfter - xpBefore * 1.03) < 1e-9,
                "the full trunk should make every skill's XP x1.03: " + xpBefore + " -> " + xpAfter);
        helper.assertTrue(Math.abs(skills.perkModifier(Skill.MINING, PerkEffect.BONUS)
                - skills.modifier(Skill.MINING, PerkEffect.BONUS).total()) < 1e-12, "Martial reached Mining");
        helper.assertTrue(skills.modifier(Skill.MINING, PerkEffect.BONUS).expertise() == 1.0,
                "Martial is combat only, Mining's passive must not move");
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void aRespecRefundsEveryPoint(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerSkills skills = fivePoints(player);
        player.setExperienceLevels(10);
        ExpertiseNode root = Expertise.byId("breadth");
        for (int i = 0; i < root.maxRank(); i++) {
            ExpertiseService.tryInvest(player, root);
        }
        helper.assertTrue(skills.expertiseAvailable() == 2, "3 of 5 points should be spent");
        helper.assertTrue(ExpertiseService.respec(player).ok(), "the respec was refused");
        helper.assertTrue(skills.expertiseRanks().isEmpty() && skills.expertiseAvailable() == 5,
                "a respec must empty the tree and hand every point back");
        helper.assertTrue(!ExpertiseService.respec(player).ok(), "an empty tree has nothing to respec");
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void aLevelUpThatCrossesAHundredEarnsAPoint(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.MINING, 99);
        helper.assertTrue(skills.expertisePoints() == 0, "99 levels give no point yet");
        SkillService.grant(player, Skill.MINING, SkillMath.xpToNext(99) + 1.0, "block.minecraft.stone");
        helper.assertTrue(skills.level(Skill.MINING) == SkillMath.MAX_LEVEL, "the grant should reach level 100");
        helper.assertTrue(skills.expertisePoints() == 1, "level 100 in total should give one point, got "
                + skills.expertisePoints());
        helper.succeed();
    }
}
