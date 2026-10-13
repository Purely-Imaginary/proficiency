package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.EnduranceMath;
import dev.amman.proficiency.event.ExhaustionWatch;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthLook;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;
import dev.amman.proficiency.skill.NightwalkerMath;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The integration task's checks (docs/hearth-build-log/integration.md), on both loaders: the one
 * exhaustion watcher at the head of the food tick (Night Owl and Lean Times took Hearty's fix), the
 * block's glow and feeding colour, and the GameTest guard every Hearth test now runs under. Each test
 * has its own batch.
 */
public final class HearthIntegrationGameTests {

    private static final String T = "proficiency:empty";

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HearthIntegrationGameTests() {
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-4;
    }

    // ---- The one exhaustion watcher ---------------------------------------------------------------

    /**
     * Night Owl and Lean Times used to watch exhaustion after the player's tick, so a climb that rolled a
     * food point over was seen as a drop and nothing was handed back (the weakness Hearty had). They are
     * now shares of the one watcher that also looks at the head of the food tick, and the shares multiply.
     */
    @GameTest(template = T, batch = "integration_exhaustion", timeoutTicks = 100)
    public static void nightOwlAndLeanTimesShaveTheClimbBeforeTheFoodTickTakesAPoint(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        long dayBefore = level.getDayTime();
        ServerPlayer player = survivor(helper);
        ServerPlayer plain = survivor(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.NIGHTWALKER, 100);
        skills.fillTree(Skill.NIGHTWALKER);
        skills.setLevel(Skill.ENDURANCE, 100);
        skills.fillTree(Skill.ENDURANCE);
        level.setDayTime(18_000L);
        // The sky darkens on the level's own tick: give it two.
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(level.isNight(), "setup: midnight");
                double owl = 1.0 - NightwalkerMath.nightOwlRefund(3);
                double lean = 1.0 - EnduranceMath.leanRefund(3);
                player.setHealth(player.getMaxHealth());
                helper.assertTrue(near(ExhaustionWatch.keep(player), owl),
                        "Night Owl III alone at night keeps " + owl + ", got " + ExhaustionWatch.keep(player));
                player.setHealth(player.getMaxHealth() * 0.4f);
                helper.assertTrue(near(ExhaustionWatch.keep(player), owl * lean),
                        "Night Owl and Lean Times multiply: " + ExhaustionWatch.keep(player));
                plain.setHealth(plain.getMaxHealth() * 0.4f);
                helper.assertTrue(ExhaustionWatch.keep(plain) == 1.0, "no talents, no share: " + ExhaustionWatch.keep(plain));

                // The roll-over: 3.9 + 0.2 would pass 4.0 and cost a point; shaved at the head of the
                // food tick it stays under.
                FoodData food = player.getFoodData();
                food.setFoodLevel(20);
                food.setSaturation(5f);
                food.setExhaustion(3.9f);
                ExhaustionWatch.forget(player.getUUID());
                ExhaustionWatch.look(player, food);
                food.addExhaustion(0.2f);
                food.tick(player);
                helper.assertTrue(food.getSaturationLevel() == 5f && food.getFoodLevel() == 20,
                        "no point taken: saturation " + food.getSaturationLevel() + ", food " + food.getFoodLevel());
                helper.assertTrue(Math.abs(food.getExhaustionLevel() - (3.9 + 0.2 * owl * lean)) < 1e-3,
                        "only the shares' part of the climb stays: " + food.getExhaustionLevel());
                // A look with no new climb finds nothing to scale: nothing is scaled twice.
                float settled = food.getExhaustionLevel();
                ExhaustionWatch.look(player, food);
                helper.assertTrue(food.getExhaustionLevel() == settled, "scaled once: " + food.getExhaustionLevel());

                // Without the talents the same climb rolls a point over, as vanilla does.
                FoodData other = plain.getFoodData();
                other.setFoodLevel(20);
                other.setSaturation(5f);
                other.setExhaustion(3.9f);
                ExhaustionWatch.forget(plain.getUUID());
                ExhaustionWatch.look(plain, other);
                other.addExhaustion(0.2f);
                other.tick(plain);
                helper.assertTrue(other.getSaturationLevel() == 4f, "vanilla takes the point: " + other.getSaturationLevel());
            } finally {
                level.setDayTime(dayBefore);
            }
            helper.succeed();
        });
    }

    /**
     * The look at the tail of the food tick scales what the tick itself added (natural regeneration), also on
     * a tick where vanilla took a food point at the head. The look used to run after the player's tick and
     * see only the net change: the head kept 5.5, vanilla took 4.0, regeneration added 4.0, and the net
     * climb of 0 left all of it. Lean Times III (0.55) shaves the 10.0 climb to 5.5; vanilla leaves 1.5 and
     * adds 4.0 (saturation 5 less the point it just took); 0.55 of that 4.0 stays: 3.7.
     */
    @GameTest(template = T, batch = "integration_exhaustion_regen", timeoutTicks = 100)
    public static void whatTheFoodTickAddsIsScaledEvenOnTheTickThatTookAPoint(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.ENDURANCE, 100);
        skills.fillTree(Skill.ENDURANCE);
        player.setHealth(player.getMaxHealth() * 0.4f);
        double lean = 1.0 - EnduranceMath.leanRefund(3);
        helper.assertTrue(near(ExhaustionWatch.keep(player), lean), "setup: Lean Times III below half health keeps "
                + lean + ", got " + ExhaustionWatch.keep(player));
        helper.assertTrue(level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),
                "setup: natural regeneration is on");

        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(5f);
        food.setExhaustion(0f);
        ExhaustionWatch.forget(player.getUUID());
        ExhaustionWatch.look(player, food);
        // Nine quiet food ticks wind vanilla's regeneration clock to one short of a heart.
        for (int i = 0; i < 9; i++) {
            food.tick(player);
        }
        helper.assertTrue(food.getExhaustionLevel() == 0f && food.getSaturationLevel() == 5f,
                "setup: nothing moved yet: " + food.getExhaustionLevel() + ", saturation " + food.getSaturationLevel());
        float healthBefore = player.getHealth();
        food.addExhaustion(10.0f);
        food.tick(player);
        helper.assertTrue(food.getSaturationLevel() == 4f, "vanilla took the roll-over point from saturation: "
                + food.getSaturationLevel());
        helper.assertTrue(player.getHealth() > healthBefore, "the tick healed (saturated regeneration ran): "
                + player.getHealth());
        helper.assertTrue(near(food.getExhaustionLevel(), (10.0 * lean - 4.0) + 4.0 * lean),
                "head kept " + (10.0 * lean) + ", vanilla took 4.0, regeneration's 4.0 kept at " + lean + ": "
                        + ((10.0 * lean - 4.0) + 4.0 * lean) + ", got " + food.getExhaustionLevel());
        helper.succeed();
    }

    /**
     * Long Haul refunds its share of what the charge was left at, so the shares multiply: sprinting at night
     * below half health with Night Owl III (keep 0.5), Lean Times III (0.55) and Long Haul III (30%) leaves
     * 0.7 of what stayed of the charge (b + 0.7 x 0.275c). Handing back 30% of the whole, unshaved charge
     * gave b + 0.275c - 0.3c, below where it started. The refund through the watcher also moves the watcher's
     * record, so the next climb is measured from the lowered number (a sprint-jump's refund lands before the
     * look, like this second phase).
     */
    @GameTest(template = T, batch = "integration_long_haul", timeoutTicks = 100)
    public static void longHaulRefundsAShareOfWhatTheOtherSharesLeftSoTheyMultiply(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        long dayBefore = level.getDayTime();
        ServerPlayer player = survivor(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        for (Skill skill : new Skill[] {Skill.NIGHTWALKER, Skill.ENDURANCE, Skill.RUNNING}) {
            skills.setLevel(skill, 100);
            skills.fillTree(skill);
        }
        level.setDayTime(18_000L);
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(level.isNight(), "setup: midnight");
                player.setHealth(player.getMaxHealth() * 0.4f);
                int rank = TalentService.rank(player, Skill.RUNNING, "endurance");
                helper.assertTrue(rank == 3, "setup: Long Haul III, got rank " + rank);
                double share = rank * 0.10;
                double keep = ExhaustionWatch.keep(player);
                double expectedKeep = (1.0 - NightwalkerMath.nightOwlRefund(3)) * (1.0 - EnduranceMath.leanRefund(3));
                helper.assertTrue(near(keep, expectedKeep), "setup: the shares keep " + expectedKeep + ", got " + keep);

                FoodData food = player.getFoodData();
                food.setFoodLevel(20);
                food.setSaturation(5f);
                food.setExhaustion(1.0f);
                ExhaustionWatch.forget(player.getUUID());
                ExhaustionWatch.look(player, food);
                // MovementEvents seeds its position tracker on the first tick it sees, then pays for metres.
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                player.setPos(player.getX() + 2.0, player.getY(), player.getZ());
                player.setSprinting(true);
                player.setOnGround(true);
                float charge = 0.1f * 2.0f;                       // vanilla: 0.1 a metre
                food.addExhaustion(charge);
                food.tick(player);                                // the head look shaves the charge
                helper.assertTrue(near(food.getExhaustionLevel(), 1.0 + keep * charge),
                        "the charge is shaved to the shares: " + food.getExhaustionLevel());
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));   // Long Haul hands its share back
                double want = 1.0 + keep * charge * (1.0 - share);
                helper.assertTrue(near(food.getExhaustionLevel(), want),
                        "0.7 of the shaved charge stays: " + want + ", got " + food.getExhaustionLevel());
                helper.assertTrue(food.getExhaustionLevel() > 1.0f,
                        "sprinting never lowers exhaustion below where it was: " + food.getExhaustionLevel());

                // A sprint-jump: the refund lands first, vanilla charges 0.2 after it, the head look shaves that.
                float base = food.getExhaustionLevel();
                ExhaustionWatch.refund(player, 0.2 * share);
                food.addExhaustion(0.2f);
                food.tick(player);
                double wantJump = base + keep * 0.2 * (1.0 - share);
                helper.assertTrue(near(food.getExhaustionLevel(), wantJump),
                        "a jump keeps 0.7 of the shaved 0.2 too: " + wantJump + ", got " + food.getExhaustionLevel());
                // No refund takes exhaustion below nothing.
                food.setExhaustion(0.01f);
                ExhaustionWatch.refund(player, 5.0);
                helper.assertTrue(food.getExhaustionLevel() == 0f, "never below zero: " + food.getExhaustionLevel());
                player.setSprinting(false);
            } finally {
                level.setDayTime(dayBefore);
            }
            helper.succeed();
        });
    }

    // ---- The block glows with charge --------------------------------------------------------------

    /**
     * The spec's "glows with charge (light 6 at no charge to 15 at any tier VI), particles in the colour
     * of the category feeding it most this hour": the block's glow and feed follow the state, through
     * HearthLook on the Hearth's own second.
     */
    @GameTest(template = T, batch = "integration_glow", timeoutTicks = 100)
    public static void theBlockGlowsWithChargeAndTakesTheColourOfWhatFedItMost(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, 6));
        if (!(level.getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
            helper.fail("no Hearth block entity");
            return;
        }
        BlockState fresh = level.getBlockState(abs);
        helper.assertTrue(fresh.getValue(HearthBlock.GLOW) == 0 && fresh.getLightEmission() == 6,
                "no charge: glow 0, light 6, got " + fresh.getLightEmission());
        HearthState state = hearth.state();
        state.setStage(6);
        state.setHighestOffering(6);
        // Tier II needs no Character (the ceiling stays at II without it), so the comfort lane's scan
        // cannot move it between the priming and the check.
        state.setCharge(HearthPool.HEARTY, 650);
        long now = level.getGameTime();
        state.addCapped(HearthPool.WORKSHOP, 40, now, Hearths.data());
        state.addCapped(HearthPool.HEARTY, 15, now, Hearths.data());
        HearthLook.second(level, abs, hearth);
        BlockState lit = level.getBlockState(abs);
        helper.assertTrue(lit.getValue(HearthBlock.GLOW) == state.maxEffectiveTier(Hearths.data())
                        && lit.getValue(HearthBlock.GLOW) == 2,
                "glow is the highest effective tier (II): " + lit.getValue(HearthBlock.GLOW));
        helper.assertTrue(lit.getLightEmission() == HearthRules.chargeLight(2, false), "light follows: " + lit.getLightEmission());
        helper.assertTrue(lit.getValue(HearthBlock.FEED) == HearthPool.WORKSHOP.ordinal() + 1,
                "Workshop was fed most this hour: feed " + lit.getValue(HearthBlock.FEED));
        helper.assertTrue(lit.getValue(HearthBlock.STAGE) == 6, "the stage is untouched");
        helper.assertTrue(level.getBlockEntity(abs) == hearth, "the same block entity, state and all");

        // A smouldering Hearth glows like embers and feeds no colour.
        state.setSmouldering(true);
        HearthLook.second(level, abs, hearth);
        BlockState doused = level.getBlockState(abs);
        helper.assertTrue(doused.getValue(HearthBlock.GLOW) == 0 && doused.getValue(HearthBlock.FEED) == 0,
                "smouldering: glow 0, no colour");
        state.setSmouldering(false);

        // The Hearth's own second does it too (HearthLook is on HEARTH_TICK).
        state.addCapped(HearthPool.HEARTY, 60, now, Hearths.data());
        helper.runAfterDelay(45, () -> {
            BlockState ticked = level.getBlockState(abs);
            // The stages lane's own check may have lowered the ceiling (no platform stands here), so the
            // glow is compared with the state as it is now, not with II.
            helper.assertTrue(ticked.getValue(HearthBlock.GLOW) == state.maxEffectiveTier(Hearths.data()),
                    "the Hearth's second set the glow: " + ticked.getValue(HearthBlock.GLOW) + " for tier "
                            + state.maxEffectiveTier(Hearths.data()));
            helper.assertTrue(ticked.getValue(HearthBlock.FEED) == HearthPool.HEARTY.ordinal() + 1,
                    "Hearty took the lead this hour: feed " + ticked.getValue(HearthBlock.FEED));
            helper.succeed();
        });
    }

    // ---- The guard every Hearth GameTest runs under ------------------------------------------------

    /** What the guard does at a test's end, called now: Hearths, comfort-marked blocks and new mock players go. */
    @GameTest(template = T, batch = "integration_guard")
    public static void theGuardTakesAwayHearthsMarkedBlocksAndMockPlayers(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        Set<UUID> before = new HashSet<>();
        level.getServer().getPlayerList().getPlayers().forEach(p -> before.add(p.getUUID()));
        ServerPlayer player = survivor(helper);
        BlockPos hearthRel = new BlockPos(1, 2, 1);
        BlockPos hearthAbs = helper.absolutePos(hearthRel);
        helper.setBlock(hearthRel, HearthBlocks.HEARTH.get().defaultBlockState());
        helper.assertTrue(HearthIndexData.get(level).contains(hearthAbs), "setup: indexed");
        BlockPos chairRel = new BlockPos(3, 2, 3);
        BlockPos chairAbs = helper.absolutePos(chairRel);
        BlockState chair = Blocks.OAK_STAIRS.defaultBlockState();
        helper.setBlock(chairRel, chair);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(level, chairAbs, Blocks.AIR.defaultBlockState(), chair,
                Blocks.STONE.defaultBlockState(), player));
        helper.assertTrue(PlacedBlocks.rawComfortMarked(level, chairAbs), "setup: the chair carries a comfort mark");
        BlockPos floorRel = new BlockPos(3, 1, 3);
        BlockState floor = level.getBlockState(helper.absolutePos(floorRel));

        HearthTestGuard.sweep(level, helper.getBounds(), before, "theGuardTakesAwayHearthsMarkedBlocksAndMockPlayers");

        helper.assertTrue(level.getBlockState(hearthAbs).isAir() && !HearthIndexData.get(level).contains(hearthAbs),
                "the Hearth and its index entry are gone");
        helper.assertTrue(level.getBlockState(chairAbs).isAir() && !PlacedBlocks.rawComfortMarked(level, chairAbs),
                "the marked chair and its mark are gone");
        helper.assertTrue(level.getBlockState(helper.absolutePos(floorRel)) == floor, "an unmarked block stays");
        helper.assertTrue(level.getServer().getPlayerList().getPlayer(player.getUUID()) == null,
                "the test's mock player has left");
        helper.succeed();
    }
}
