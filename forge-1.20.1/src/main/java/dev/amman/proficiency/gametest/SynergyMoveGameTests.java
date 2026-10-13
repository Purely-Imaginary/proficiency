package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.ExpansionEvents;
import dev.amman.proficiency.event.MovementEvents;
import dev.amman.proficiency.event.TalentExpansionEvents;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.BuildClassifier;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillProcEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Synergies 2, Batch B4: the seven movement, discovery and building synergies, each with a control
 * case (the same setup without the synergy, or without the condition it asks for). Each test is its
 * own batch, so water, a roof and a Hearth never reach a neighbour.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SynergyMoveGameTests {

    private static final String T = "empty";

    private SynergyMoveGameTests() {
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, y, z)));
        ProcService.forget(player.getUUID());
        return player;
    }

    private static void rank(ServerPlayer player, Skill skill, String talent, int rank) {
        ProficiencyAttachments.of(player).setRank(Talents.get(skill, talent), rank);
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ProcService.forget(player.getUUID());
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The end of the player's tick, as the game posts it. */
    private static void tickEnd(ServerPlayer player) {
        ForgeTestEvents.playerTickEnd(player);
    }

    private static void crouch(ServerPlayer player) {
        player.setShiftKeyDown(true);
        player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
    }

    private static void grit(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(
                new SkillProcEvent(player, Skill.ENDURANCE, null, null, true));
    }

    // ---- Play Dead ------------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_play_dead")
    public static void playDeadHidesACrouchedPlayerOnAGrit(GameTestHelper helper) {
        ServerPlayer with = player(helper, 1.5, 2, 1.5);
        ServerPlayer standing = player(helper, 3.5, 2, 1.5);
        ServerPlayer plain = player(helper, 5.5, 2, 1.5);
        for (ServerPlayer p : new ServerPlayer[] {with, standing}) {
            rank(p, Skill.SNEAKING, "shadowmeld", 2);
            rank(p, Skill.ENDURANCE, "last_stand", 1);
        }
        crouch(with);
        crouch(plain);
        Mob hunter = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        hunter.setTarget(with);
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, 2, 7));
        far.setTarget(plain);
        Mob other = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 3));
        other.setTarget(standing);

        grit(standing);
        helper.assertFalse(standing.hasEffect(MobEffects.INVISIBILITY), "a standing player vanished on a Grit");
        helper.assertTrue(other.getTarget() == standing, "a standing player's hunter lost the trail");
        grit(plain);
        helper.assertFalse(plain.hasEffect(MobEffects.INVISIBILITY), "a Grit hid a player without the synergy");
        helper.assertTrue(far.getTarget() == plain, "a mob lost a player without the synergy");

        grit(with);
        helper.assertTrue(with.hasEffect(MobEffects.INVISIBILITY), "a crouched Grit gave no Invisibility");
        helper.assertTrue(with.getEffect(MobEffects.INVISIBILITY).getDuration() <= 60, "Invisibility outlasted 3 s");
        helper.assertTrue(hunter.getTarget() == null, "the hunter still had the player in its sights");

        // Once per 30 s: a second Grit straight away does nothing.
        with.removeEffect(MobEffects.INVISIBILITY);
        hunter.setTarget(with);
        grit(with);
        helper.assertFalse(with.hasEffect(MobEffects.INVISIBILITY), "Play Dead fired twice inside its cooldown");
        helper.assertTrue(hunter.getTarget() == with, "Play Dead dropped a target inside its cooldown");
        finish(helper, with, standing, plain);
    }

    // ---- Ranger ---------------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_ranger")
    public static void rangerFillsTheQuiverOnANewBiome(GameTestHelper helper) {
        ServerPlayer with = player(helper, 1.5, 2, 1.5);
        ServerPlayer plain = player(helper, 3.5, 2, 1.5);
        rank(with, Skill.ARCHERY, "quiver", 3);
        rank(with, Skill.WAYFARING, "pathfinder", 3);
        rank(plain, Skill.ARCHERY, "quiver", 3);
        TalentExpansionEvents.ranger(plain);
        helper.assertTrue(plain.getInventory().countItem(Items.ARROW) == 0, "a new biome gave arrows without the synergy");
        TalentExpansionEvents.ranger(with);
        helper.assertTrue(with.getInventory().countItem(Items.ARROW) == 4,
                "a new biome should give 4 arrows, gave " + with.getInventory().countItem(Items.ARROW));
        TalentExpansionEvents.ranger(with);
        helper.assertTrue(with.getInventory().countItem(Items.ARROW) == 8, "a second new biome should give 4 more");
        finish(helper, with, plain);
    }

    // ---- Seismic Survey -------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_seismic_survey")
    public static void seismicSurveyPulsesEveryFiveSecondsAndReachesFurther(GameTestHelper helper) {
        ServerPlayer with = player(helper, 1.5, 2, 1.5);
        ServerPlayer plain = player(helper, 3.5, 2, 1.5);
        for (ServerPlayer p : new ServerPlayer[] {with, plain}) {
            rank(p, Skill.SPELUNKING, "echolocation", 3);
        }
        rank(with, Skill.MINING, "geologist", 3);
        rank(with, Skill.EXCAVATION, "archaeologist", 3);
        // A roof over both, so neither sees the sky.
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 4; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            }
        }
        helper.assertTrue(dev.amman.proficiency.perk.TalentService.hasSynergy(with, "seismic_survey"), "setup: no synergy");
        with.tickCount = 100;
        plain.tickCount = 100;
        helper.assertTrue(TalentExpansionEvents.echoPulse(plain, true) == 0, "plain Echolocation pulsed at 5 s");
        helper.assertTrue(TalentExpansionEvents.echoPulse(with, true) == 8,
                "Seismic Survey should pulse at 5 s with radius 8");
        with.tickCount = 200;
        plain.tickCount = 200;
        helper.assertTrue(TalentExpansionEvents.echoPulse(plain, true) == 6,
                "plain Echolocation should pulse at 10 s with radius 6");
        helper.assertTrue(TalentExpansionEvents.echoPulse(with, true) == 8, "Seismic Survey missed the 10 s pulse");
        with.tickCount = 150;
        helper.assertTrue(TalentExpansionEvents.echoPulse(with, true) == 0, "a pulse came off the 5 s grid");
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 4; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.AIR);
            }
        }
        finish(helper, with, plain);
    }

    // ---- Freerunner -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_freerunner")
    public static void freerunnerKeepsTheSprintAfterAHighLanding(GameTestHelper helper) {
        ServerPlayer with = player(helper, 1.5, 2, 1.5);
        ServerPlayer plain = player(helper, 3.5, 2, 1.5);
        rank(with, Skill.RUNNING, "parkour", 1);
        rank(with, Skill.JUMPING, "bounce", 1);
        rank(plain, Skill.RUNNING, "parkour", 1);
        with.setSprinting(true);
        plain.setSprinting(true);
        MovementEvents.freerunner(plain, 5f);
        helper.assertFalse(plain.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed came without the synergy");
        MovementEvents.freerunner(with, 3f);
        helper.assertFalse(with.hasEffect(MobEffects.MOVEMENT_SPEED), "a 3 block fall gave Speed");
        with.setSprinting(false);
        MovementEvents.freerunner(with, 5f);
        helper.assertFalse(with.hasEffect(MobEffects.MOVEMENT_SPEED), "a walking landing gave Speed");
        with.setSprinting(true);
        MovementEvents.freerunner(with, 5f);
        helper.assertTrue(with.hasEffect(MobEffects.MOVEMENT_SPEED), "a sprinting 5 block landing gave no Speed");
        helper.assertTrue(with.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1, "Speed should be level II");
        helper.assertTrue(with.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() <= 40, "Speed outlasted 2 s");
        helper.assertTrue(with.isSprinting(), "the sprint was not kept");
        finish(helper, with, plain);
    }

    // ---- High Diver -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_high_diver", timeoutTicks = 100)
    public static void highDiverGetsDolphinsGraceFromAHighDive(GameTestHelper helper) {
        ServerPlayer with = player(helper, 1.5, 6, 1.5);
        ServerPlayer plain = player(helper, 4.5, 6, 4.5);
        ServerPlayer shallow = player(helper, 6.5, 6, 1.5);
        for (ServerPlayer p : new ServerPlayer[] {with, plain, shallow}) {
            rank(p, Skill.JUMPING, "safe_landing", 5);
        }
        for (ServerPlayer p : new ServerPlayer[] {with, shallow}) {
            rank(p, Skill.SWIMMING, "deep_lungs", 3);
        }
        for (int x = 0; x < 8; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        // The last dry tick of the fall: the distance vanilla has counted so far.
        with.fallDistance = 10f;
        plain.fallDistance = 10f;
        shallow.fallDistance = 5f;
        for (ServerPlayer p : new ServerPlayer[] {with, plain, shallow}) {
            tickEnd(p);
        }
        // Then the water: the body's next tick zeroes the distance before the handler looks.
        with.setPos(with.getX(), helper.absoluteVec(new Vec3(0, 3.5, 0)).y, with.getZ());
        plain.setPos(plain.getX(), with.getY(), plain.getZ());
        shallow.setPos(shallow.getX(), with.getY(), shallow.getZ());
        for (ServerPlayer p : new ServerPlayer[] {with, plain, shallow}) {
            p.baseTick();
            helper.assertTrue(p.isInWater(), "setup: the player should be in water");
            helper.assertTrue(p.fallDistance == 0f, "setup: water contact should have reset the fall");
            tickEnd(p);
        }
        helper.assertTrue(with.hasEffect(MobEffects.DOLPHINS_GRACE), "a 10 block dive gave no Dolphin's Grace");
        helper.assertTrue(with.getEffect(MobEffects.DOLPHINS_GRACE).getDuration() <= 120, "Dolphin's Grace outlasted 6 s");
        helper.assertFalse(plain.hasEffect(MobEffects.DOLPHINS_GRACE), "Dolphin's Grace came without the synergy");
        helper.assertFalse(shallow.hasEffect(MobEffects.DOLPHINS_GRACE), "a 5 block dive gave Dolphin's Grace");
        // Swimming on later gives nothing more: the dive is over, and the effect is on a timer.
        with.removeEffect(MobEffects.DOLPHINS_GRACE);
        for (int i = 0; i < 3; i++) {
            with.baseTick();
            tickEnd(with);
        }
        helper.assertFalse(with.hasEffect(MobEffects.DOLPHINS_GRACE), "treading water renewed Dolphin's Grace");
        for (int x = 0; x < 8; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 0; z < 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        finish(helper, with, plain, shallow);
    }

    // ---- Steeplejack ----------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_steeplejack", timeoutTicks = 200)
    public static void steeplejackStopsAFallOncePerFiveSeconds(GameTestHelper helper) {
        ServerPlayer with = player(helper, 1.5, 6, 1.5);
        ServerPlayer plain = player(helper, 3.5, 6, 1.5);
        rank(with, Skill.JUMPING, "cat_reflexes", 2);
        rank(with, Skill.MASONRY, "safety_harness", 3);
        rank(plain, Skill.JUMPING, "cat_reflexes", 2);
        with.fallDistance = 10f;
        plain.fallDistance = 10f;
        ExpansionEvents.steeplejack(plain);
        helper.assertTrue(plain.fallDistance == 10f, "the fall stopped without the synergy");
        with.fallDistance = 2f;
        ExpansionEvents.steeplejack(with);
        helper.assertTrue(with.fallDistance == 2f, "a 2 block fall was stopped");
        with.fallDistance = 10f;
        ExpansionEvents.steeplejack(with);
        helper.assertTrue(with.fallDistance == 0f, "a placed block did not stop a 10 block fall");
        with.fallDistance = 10f;
        ExpansionEvents.steeplejack(with);
        helper.assertTrue(with.fallDistance == 10f, "Steeplejack fired twice inside 5 s");
        helper.runAfterDelay(101, () -> {
            ExpansionEvents.steeplejack(with);
            helper.assertTrue(with.fallDistance == 0f, "Steeplejack never came back after 5 s");
            finish(helper, with, plain);
        });
    }

    // ---- Housewarming ---------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_housewarming")
    public static void housewarmingFeedsEverySixthPulseAtHome(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        for (ServerPlayer other : java.util.List.copyOf(level.players())) {
            if (other.position().distanceTo(origin) < 64) {
                other.setPos(other.getX(), other.getY() + 400, other.getZ());
            }
        }
        BlockPos hearth = new BlockPos(1, 2, 1);
        helper.setBlock(hearth, HearthBlocks.HEARTH.get().defaultBlockState()
                .setValue(HearthBlock.STAGE, 1).setValue(HearthBlock.SMOULDERING, false));
        BlockPos at = helper.absolutePos(hearth);
        // Twelve flower pots around the spot: a room worth warming even at the base threshold.
        for (int i = 0; i < 12; i++) {
            helper.setBlock(new BlockPos(2 + i % 4, 3, 2 + i / 4), Blocks.FLOWER_POT);
        }
        ServerPlayer with = player(helper, 3.5, 2, 3.5);
        ServerPlayer away = player(helper, 3.5, 2, 4.5);
        ServerPlayer plain = player(helper, 4.5, 2, 3.5);
        for (ServerPlayer p : new ServerPlayer[] {with, away, plain}) {
            ProficiencyAttachments.of(p).setLevel(Skill.DECORATING, 30);
            rank(p, Skill.DECORATING, "hygge", 3);
            p.getFoodData().setFoodLevel(10);
            p.getFoodData().setSaturation(0f);
        }
        for (ServerPlayer p : new ServerPlayer[] {with, away}) {
            rank(p, Skill.HOMEBUILDER, "light_step", 5);
        }
        helper.assertTrue(BuildClassifier.isDecorative(Blocks.FLOWER_POT.defaultBlockState()), "setup: a flower pot is not decor");
        for (ServerPlayer home : new ServerPlayer[] {with, plain}) {
            HearthAttachments.of(home).seenBy(at.asLong(), level.dimension().location().toString(), level.getGameTime());
        }
        for (int i = 0; i < 6; i++) {
            ExpansionEvents.cozyRoom(plain);
            ExpansionEvents.cozyRoom(away);
        }
        helper.assertTrue(plain.getFoodData().getFoodLevel() == 10, "a player without the synergy was fed");
        helper.assertTrue(away.getFoodData().getFoodLevel() == 10, "the room fed a player away from the Hearth");
        for (int i = 0; i < 5; i++) {
            ExpansionEvents.cozyRoom(with);
        }
        helper.assertTrue(with.getFoodData().getFoodLevel() == 10,
                "hunger should still be 10 after 5 pulses, was " + with.getFoodData().getFoodLevel());
        helper.assertTrue(with.hasEffect(MobEffects.REGENERATION), "setup: the room did not warm the player");
        ExpansionEvents.cozyRoom(with);
        helper.assertTrue(with.getFoodData().getFoodLevel() == 11,
                "hunger should be 11 after 6 pulses, was " + with.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(with.getFoodData().getSaturationLevel() - 2f) < 1e-4,
                "saturation should be 2, was " + with.getFoodData().getSaturationLevel());
        for (int i = 0; i < 6; i++) {
            ExpansionEvents.cozyRoom(with);
        }
        helper.assertTrue(with.getFoodData().getFoodLevel() == 12, "the second 30 s should feed once more");
        for (int i = 0; i < 12; i++) {
            helper.setBlock(new BlockPos(2 + i % 4, 3, 2 + i / 4), Blocks.AIR);
        }
        helper.setBlock(hearth, Blocks.AIR);
        finish(helper, with, away, plain);
    }
}
