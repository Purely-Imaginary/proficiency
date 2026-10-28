package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.Tempo;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The combat Tempo rules of 1.49.0 on a real server clock: a weapon chain lives 7 s and climbs 5% a step, Mining
 * keeps 3 s and 2%, a support skill pays the live weapon chain's multiplier without stepping it, and Endurance
 * has no Tempo at all. The chain is stepped through {@link Tempo#multiplier}, the call every real grant makes.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TempoGameTests {

    private static final String T = "empty";
    private static final double EPS = 1e-9;

    private TempoGameTests() {
    }

    private static ServerPlayer fighter(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2, 2, 2)));
        return player;
    }

    private static void leave(ServerPlayer player) {
        Tempo.forget(player.getUUID());
        player.server.getPlayerList().remove(player);
    }

    private static void near(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) < EPS, what + ": want x" + want + ", got x" + got);
    }

    @GameTest(template = T, timeoutTicks = 400)
    public static void aSwordChainHoldsAFiveSecondPauseAndBreaksAfterEight(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.0, "the first hit starts the chain");
        helper.runAfterDelay(2, () -> {
            near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.05, "step 1 is +5% in combat");
            helper.runAfterDelay(100, () -> {
                near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.10, "a 5 s pause keeps the chain");
                helper.runAfterDelay(160, () -> {
                    near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.0, "an 8 s pause breaks it");
                    leave(player);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 200)
    public static void miningStillBreaksAfterFourSeconds(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Tempo.multiplier(player, Skill.MINING);
        helper.runAfterDelay(2, () -> {
            near(helper, Tempo.multiplier(player, Skill.MINING), 1.02, "Mining keeps +2% a step");
            helper.runAfterDelay(80, () -> {
                near(helper, Tempo.multiplier(player, Skill.MINING), 1.0, "a 4 s pause breaks a Mining chain");
                leave(player);
                helper.succeed();
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void aSupportGrantRidesTheSwordChainAndDoesNotStepIt(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        near(helper, Tempo.multiplier(player, Skill.COURAGE), 1.0, "no weapon chain: Courage pays x1.0");
        Tempo.multiplier(player, Skill.SWORDS);
        helper.runAfterDelay(2, () -> {
            Tempo.multiplier(player, Skill.SWORDS);
            helper.runAfterDelay(2, () -> {
                Tempo.multiplier(player, Skill.SWORDS); // step 2: x1.10
                double nextSword = Tempo.preview(player, Skill.SWORDS);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(Tempo.ridden(player) == Skill.SWORDS, "rides " + Tempo.ridden(player));
                    for (Skill support : new Skill[] {Skill.COURAGE, Skill.CHARGER, Skill.GUARDIAN, Skill.TACTICIAN,
                            Skill.BLOCKING}) {
                        near(helper, Tempo.preview(player, support), 1.10, support.id() + " preview");
                        near(helper, Tempo.multiplier(player, support), 1.10, support.id() + " grant");
                    }
                    // A real Courage grant through the whole grant path takes no step either.
                    SkillService.grantNoFirstTime(player, Skill.COURAGE, 1.0, "mob");
                    near(helper, Tempo.preview(player, Skill.SWORDS), nextSword, "the riders stepped the sword chain");
                    near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.15, "the sword's own next step");
                    near(helper, Tempo.multiplier(player, Skill.COURAGE), 1.15, "Courage follows the new step");
                    leave(player);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void enduranceNeverStepsAndPaysNoTempo(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        for (int i = 0; i < 6; i++) {
            near(helper, Tempo.multiplier(player, Skill.ENDURANCE), 1.0, "Endurance has no Tempo");
            SkillService.grantNoFirstTime(player, Skill.ENDURANCE, 1.0, "damage");
        }
        helper.runAfterDelay(2, () -> {
            near(helper, Tempo.preview(player, Skill.ENDURANCE), 1.0, "Endurance built a chain");
            helper.assertTrue(Tempo.ridden(player) == null, "Endurance gave a support skill a chain to ride");
            near(helper, Tempo.multiplier(player, Skill.BLOCKING), 1.0, "Blocking rode Endurance");
            leave(player);
            helper.succeed();
        });
    }

    /** Decides a crit on {@code target} the way the loader's crit event does: vanilla's own, or one only we forced. */
    private static void crit(ServerPlayer player, net.minecraft.world.entity.Entity target, boolean vanilla) {
        var event = new net.minecraftforge.event.entity.player.CriticalHitEvent(player, target, vanilla ? 1.5f : 1.0f,
                vanilla);
        event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        dev.amman.proficiency.event.CritEvents.onCriticalHitDecided(event);
    }

    @GameTest(template = T, timeoutTicks = 600)
    public static void aJumpCritHoldsTheChainAcrossATwelveSecondGapForOneGapOnly(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new net.minecraft.core.BlockPos(2, 2, 3));
        Tempo.multiplier(player, Skill.SWORDS);
        helper.runAfterDelay(2, () -> {
            crit(player, target, true);
            near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.05, "the crit hit steps as usual");
            helper.runAfterDelay(240, () -> {
                near(helper, Tempo.preview(player, Skill.COURAGE), 1.05, "Courage rides the crit-held chain");
                near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.10, "a 12 s pause after a crit keeps the chain");
                helper.runAfterDelay(160, () -> {
                    near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.0,
                            "after an ordinary hit an 8 s pause breaks it again");
                    leave(player);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 400)
    public static void aProficiencyCritAloneDoesNotHoldTheChain(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new net.minecraft.core.BlockPos(2, 2, 3));
        Tempo.multiplier(player, Skill.SWORDS);
        helper.runAfterDelay(2, () -> {
            crit(player, target, false); // Lunge and the other crits of ours are not vanilla's jump crit
            near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.05, "step 1");
            helper.runAfterDelay(240, () -> {
                near(helper, Tempo.multiplier(player, Skill.SWORDS), 1.0, "our own crit lengthened the window");
                leave(player);
                helper.succeed();
            });
        });
    }
}
