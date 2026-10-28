package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.coop.CoopBatch;
import dev.amman.proficiency.coop.CoopService;
import dev.amman.proficiency.skill.CompanyBonus;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Co-op XP streams (1.45.0): a grant goes into the second's batch, a player within company range gets the earner's
 * stream (dots, flying into their bar since company pays them) and the earner in their pip row, a far player gets
 * nothing about them, and the earner hears the shared plink for the company XP their own grant carried.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoopGameTests {

    private static final String T = "empty";

    private CoopGameTests() {
    }

    /** A spot of the test's own, far from the spawn and from the Social tests, so company is only who we place. */
    private static ServerPlayer at(GameTestHelper helper, double dx) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(base.getX() + 60_000.5 + dx, base.getY(), base.getZ() + 60_000.5);
        CompanyBonus.forget(player.getUUID());
        CoopService.forget(player.getUUID());
        return player;
    }

    @GameTest(template = T)
    public static void aNearPlayerGetsTheBatchAFarOneGetsNothing(GameTestHelper helper) {
        ServerPlayer earner = at(helper, 0);
        ServerPlayer near = at(helper, 5);
        ServerPlayer far = at(helper, 400);
        SkillService.grantNoFirstTime(earner, Skill.MINING, 20.0, null);
        SkillService.grantNoFirstTime(earner, Skill.MINING, 10.0, null);
        Map<java.util.UUID, CoopBatch> batches = CoopService.build(List.of(earner, near, far));

        CoopBatch toNear = batches.get(near.getUUID());
        helper.assertTrue(toNear != null, "the near player gets a batch");
        helper.assertTrue(toNear.streams().size() == 1, "one stream, the earner's, batched: " + toNear.streams());
        CoopBatch.Stream stream = toNear.streams().get(0);
        helper.assertTrue(stream.id().equals(earner.getUUID()), "it is the earner's");
        helper.assertTrue(stream.skill() == Skill.MINING.ordinal(), "in the skill they earned");
        helper.assertTrue(stream.dots() >= 1 && stream.dots() <= 6, "1..6 dots: " + stream.dots());
        helper.assertTrue(stream.xp() > 0, "the XP");
        helper.assertTrue(stream.pays(), "5 blocks apart, company pays the watcher: dots into the bar");
        helper.assertTrue(toNear.partners().stream().anyMatch(p -> p.id().equals(earner.getUUID())),
                "the earner is in the near player's pip row");

        CoopBatch toFar = batches.get(far.getUUID());
        helper.assertTrue(toFar == null || toFar.streams().isEmpty(), "the far player gets no stream: " + toFar);
        helper.assertTrue(toFar == null || toFar.partners().isEmpty(), "and nobody in company");

        CoopBatch toEarner = batches.get(earner.getUUID());
        helper.assertTrue(toEarner != null && toEarner.streams().isEmpty(), "the earner never sees their own dots");
        helper.assertTrue(toEarner.ownBonus() > 0f, "the earner's grants carried company XP: the shared plink");

        // Next second, nothing earned and nobody moved: no news, nothing sent.
        Map<java.util.UUID, CoopBatch> quiet = CoopService.build(List.of(earner, near, far));
        helper.assertTrue(!quiet.containsKey(near.getUUID()) && !quiet.containsKey(far.getUUID()),
                "a quiet second sends nothing: " + quiet.keySet());
        helper.succeed();
    }

    @GameTest(template = T)
    public static void movingOutOfRangeEmptiesThePipRow(GameTestHelper helper) {
        ServerPlayer one = at(helper, 1_000);
        ServerPlayer two = at(helper, 1_004);
        CoopBatch first = CoopService.build(List.of(one, two)).get(one.getUUID());
        helper.assertTrue(first != null && first.partners().size() == 1, "in company: one pip");
        two.setPos(two.getX() + 300, two.getY(), two.getZ());
        CoopBatch after = CoopService.build(List.of(one, two)).get(one.getUUID());
        helper.assertTrue(after != null && after.partners().isEmpty(), "the change is sent, with no partners");
        SkillService.grantNoFirstTime(two, Skill.WOODCUTTING, 50.0, null);
        CoopBatch away = CoopService.build(List.of(one, two)).get(one.getUUID());
        helper.assertTrue(away == null || away.streams().isEmpty(), "300 blocks away: no stream");
        helper.succeed();
    }
}
