package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.homebuilder.HomePassive;
import dev.amman.proficiency.homebuilder.HomebuilderEvents;
import dev.amman.proficiency.homebuilder.HomebuilderXp;
import dev.amman.proficiency.homebuilder.Homeward;
import dev.amman.proficiency.homebuilder.HouseProud;
import dev.amman.proficiency.homebuilder.NightWatch;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.NightwalkerService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SocialService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Homebuilder in a real world, on both loaders (docs/HOMEBUILDER-SPEC.md, "Tests"): the share pays
 * only at home and never feeds itself; milestones pay once per Hearth and per player (sieges, Feasts,
 * Tales and Chronicle pages each time, the banner the first time); the passive's
 * modifiers come off within 2 s of leaving range or of the Hearth breaking or smouldering; the magnet
 * is off without Tidy Home and leaves sneaking, hoppers, water flow and fresh drops alone; Hearthglow
 * stays inside the hourly cap; Homeward breaks on a hit or a step and a death mid-channel loses the
 * Tales. The rules are unit tested in core ({@code HomebuilderMathTest}).
 *
 * <p>Every test places a Hearth, so each has its own batch (the empty template is tiny). "Home" is the
 * flag a Hearth's tick writes on the players in range; the tests write it themselves
 * ({@link PlayerHearth#seenBy}), as the Stages lane's tick does in play.
 */
public final class HomebuilderGameTests {

    private static final String T = "proficiency:empty";
    /** How far round a test its setup clears what earlier tests left. */
    private static final double CLEAR_RADIUS = 64.0;

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HomebuilderGameTests() {
    }

    /** A Hearth, players around it, and the cleanup GuardianGameTests does (mock players never leave). */
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel level;
        final List<ServerPlayer> players = new ArrayList<>();
        BlockPos hearth;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            clearArea();
        }

        /**
         * What earlier tests left nearby: their mock players go far up (out of every range here), and a
         * Hearth a failed test left behind goes, since a live Hearth flags whoever stands in its range
         * and refuses a lighting within its spacing. Batches run one after another, so nothing nearby
         * is still running.
         */
        private void clearArea() {
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other.position().distanceTo(origin) < CLEAR_RADIUS) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            BlockPos centre = BlockPos.containing(origin);
            for (int i = 0; i < 16; i++) {
                BlockPos left = HearthIndexData.get(level).tooClose(level, centre, CLEAR_RADIUS);
                if (left == null) {
                    break;
                }
                level.setBlock(left, Blocks.AIR.defaultBlockState(), 3);
                HearthIndexData.forget(level, left);
            }
        }

        /** A stage-3 Hearth at relative (x, 2, z), on the template's floor. */
        BlockPos hearth(int x, int z) {
            BlockPos rel = new BlockPos(x, 2, z);
            helper.setBlock(rel, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, 3));
            hearth = helper.absolutePos(rel);
            return hearth;
        }

        HearthBlockEntity entity() {
            return (HearthBlockEntity) level.getBlockEntity(hearth);
        }

        ServerPlayer player(double x, double z) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            clearSpawnProtection(player);
            players.add(player);
            return player;
        }

        /** What the Hearth's tick writes on a player in range. */
        void home(ServerPlayer player) {
            HearthAttachments.of(player).seenBy(hearth.asLong(), level.dimension().location().toString(),
                    level.getGameTime());
        }

        void done() {
            for (ServerPlayer player : players) {
                ProcService.forget(player.getUUID());
                player.server.getPlayerList().remove(player);
            }
            // Our Hearth goes too: left burning, it would flag the next tests' players as home.
            if (hearth != null && level.getBlockState(hearth).is(HearthBlocks.HEARTH.get())) {
                level.setBlock(hearth, Blocks.AIR.defaultBlockState(), 3);
            }
            if (hearth != null) {
                HearthIndexData.forget(level, hearth);
                for (Entity loose : level.getEntities((Entity) null, new AABB(hearth).inflate(8.0),
                        entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
                    loose.discard();
                }
            }
            helper.succeed();
        }
    }

    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not clear spawn protection", e);
        }
    }

    /**
     * This player fed the Hearth: a ledger cell (what Homeward reads while the Hearth is loaded) and
     * the copy the per-player second keeps for when it is not.
     */
    private static void fed(Fixture f, ServerPlayer player, BlockPos hearth) {
        f.entity().state().ledger().add(player.getUUID(), player.getGameProfile().getName(), HearthPool.HEARTY,
                "mining", 10.0, dev.amman.proficiency.skill.RestedMath.today());
        HearthAttachments.of(player).setMark(Homeward.FED_PREFIX + HearthBlockEntity.key(f.level, hearth), 1000L);
    }

    private static PlayerSkills maxed(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.HOMEBUILDER, 100);
        skills.fillTree(Skill.HOMEBUILDER);
        return skills;
    }

    private static double modifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            net.minecraft.resources.ResourceLocation id) {
        AttributeModifier modifier = player.getAttribute(attribute).getModifier(id);
        return modifier == null ? 0.0 : modifier.amount();
    }

    // ---- XP -------------------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_share")
    public static void theShareIsPaidOnlyAtHome(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer home = f.player(1.5, 3.5);
        ServerPlayer away = f.player(3.5, 3.5);
        f.home(home);
        SkillService.grant(home, Skill.MINING, 10.0, null);
        SkillService.grant(away, Skill.MINING, 10.0, null);
        double expected = HomebuilderMath.share(10.0, ProficiencyConfig.xpRate(Skill.MINING), 1.0,
                ProficiencyConfig.homeShare());
        helper.assertTrue(Math.abs(HomebuilderXp.pending(home) - expected) < 1e-9,
                "a grant at home should put " + expected + " in the pot, got " + HomebuilderXp.pending(home));
        helper.assertTrue(HomebuilderXp.pending(away) == 0.0, "a grant away from home fed Homebuilder");
        helper.assertTrue(ProficiencyAttachments.of(home).xp(Skill.HOMEBUILDER) == 0f
                && ProficiencyAttachments.of(home).level(Skill.HOMEBUILDER) == 0, "paid before the 5-second payout");
        float paid = HomebuilderXp.flush(home);
        helper.assertTrue(paid > 0f && HomebuilderXp.pending(home) == 0.0, "the payout added no Homebuilder XP");

        // A smouldering Hearth is not home.
        f.entity().state().setSmouldering(true);
        SkillService.grant(home, Skill.MINING, 10.0, null);
        helper.assertTrue(HomebuilderXp.pending(home) == 0.0, "a smouldering Hearth fed Homebuilder");
        f.done();
    }

    @GameTest(template = T, batch = "homebuilder_no_self_share")
    public static void homebuilderNeverFeedsItselfOrAnotherSharePot(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        f.home(player);
        SkillService.grant(player, Skill.HOMEBUILDER, 10.0, null);
        SkillService.grant(player, Skill.HOMEBUILDER, 10.0, HomebuilderXp.SHARE_SOURCE);
        SkillService.grant(player, Skill.SOCIAL, 10.0, SocialService.SOURCE);
        SkillService.grant(player, Skill.NIGHTWALKER, 10.0, NightwalkerService.DARK_SOURCE);
        SkillService.grant(player, Skill.MINING, 10.0, "proficiency.xplog.source.command");
        helper.assertTrue(HomebuilderXp.pending(player) == 0.0,
                "Homebuilder, a share pot's payout or an addxp fed the share: " + HomebuilderXp.pending(player));
        // A first-time bonus is not work at home: only the grant itself is shared.
        SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.diamond_ore");
        double expected = HomebuilderMath.share(10.0, ProficiencyConfig.xpRate(Skill.MINING), 1.0,
                ProficiencyConfig.homeShare());
        helper.assertTrue(Math.abs(HomebuilderXp.pending(player) - expected) < 1e-9,
                "the first-time line was shared too: " + HomebuilderXp.pending(player));
        // Homebuilder feeds no Hearth pool and is not Tales.
        helper.assertTrue(Hearths.data().poolFor(Skill.HOMEBUILDER) == null, "a pool names Homebuilder");
        helper.assertFalse(Hearths.data().tales().banks(Skill.HOMEBUILDER), "Homebuilder banks Tales");
        f.done();
    }

    @GameTest(template = T, batch = "homebuilder_milestones")
    public static void milestonesPayOncePerHearthAndPlayer(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        BlockPos hearth = f.hearth(1, 1);
        ServerPlayer a = f.player(1.5, 3.5);
        ServerPlayer b = f.player(2.5, 3.5);
        String key = HearthBlockEntity.key(f.level, hearth);

        HearthEvents.STAGE_SEALED.fire(new HearthEvents.StageSealed(f.level, hearth, a, 2));
        helper.assertTrue(HearthAttachments.of(a).hasMilestone(key + "|stage:2"), "sealing a stage paid nothing");
        helper.assertTrue(ProficiencyAttachments.of(a).level(Skill.HOMEBUILDER) > 0
                || ProficiencyAttachments.of(a).xp(Skill.HOMEBUILDER) > 0, "the stage paid no XP");
        helper.assertTrue(HomebuilderXp.milestone(a, f.level, hearth, "stage:2", 300, HomebuilderXp.STAGE_SOURCE + 2,
                false) == 0f, "the same stage paid twice");
        // Another Hearth is another milestone.
        BlockPos elsewhere = hearth.offset(200, 0, 0);
        helper.assertTrue(HomebuilderXp.milestone(a, f.level, elsewhere, "stage:2", 300,
                HomebuilderXp.STAGE_SOURCE + 2, false) > 0f, "a second Hearth's stage paid nothing");

        HearthEvents.COMFORT_KIND_SEEN.fire(new HearthEvents.ComfortKindSeen(f.level, hearth, a.getUUID(), "bed"));
        HearthEvents.COMFORT_KIND_SEEN.fire(new HearthEvents.ComfortKindSeen(f.level, hearth, null, "seat"));
        helper.assertTrue(HearthAttachments.of(a).hasMilestone(key + "|comfort:bed"), "the placer of a new kind was not paid");
        helper.assertFalse(HearthAttachments.of(a).hasMilestone(key + "|comfort:seat"), "a kind nobody placed paid");
        HearthEvents.CHARACTER_ID_SEEN.fire(new HearthEvents.CharacterIdSeen(f.level, hearth, b.getUUID(), "minecraft:oak_planks"));
        helper.assertTrue(HearthAttachments.of(b).hasMilestone(key + "|character:minecraft:oak_planks"),
                "a new Character block paid nothing");

        // A siege: 300 split by kills, at least 10% for anyone present.
        HearthEvents.SIEGE_DEFENDED.fire(new HearthEvents.SiegeDefended(f.level, hearth, Map.of(a.getUUID(), 5),
                List.of(a, b)));
        helper.assertTrue(HearthAttachments.of(a).hasMilestone(key + "|siege")
                && HearthAttachments.of(b).hasMilestone(key + "|siege"), "a defended siege did not pay both");
        HearthEvents.FEAST.fire(new HearthEvents.Feast(f.level, hearth, List.of(a, b)));
        HearthEvents.TALES_PAID.fire(new HearthEvents.TalesPaid(f.level, hearth, a, 200.0));
        HearthEvents.CHRONICLE_WRITTEN.fire(new HearthEvents.ChronicleWritten(f.level, hearth, a, 1L, 50.0));
        PlayerHearth data = HearthAttachments.of(a);
        helper.assertTrue(data.hasMilestone(key + "|feast") && data.hasMilestone(key + "|tales")
                && data.hasMilestone(key + "|chronicle"), "the Feast, the Tales or the Chronicle paid nothing");

        // The rows that come again pay again: a second siege, Tales on a later trip, a new page.
        float before = total(a);
        HearthEvents.SIEGE_DEFENDED.fire(new HearthEvents.SiegeDefended(f.level, hearth, Map.of(a.getUUID(), 3),
                List.of(a)));
        helper.assertTrue(total(a) > before, "a second siege at the same Hearth paid nothing");
        before = total(a);
        HearthEvents.TALES_PAID.fire(new HearthEvents.TalesPaid(f.level, hearth, a, 200.0));
        helper.assertTrue(total(a) > before, "Tales paid on a later trip paid nothing");
        before = total(a);
        HearthEvents.CHRONICLE_WRITTEN.fire(new HearthEvents.ChronicleWritten(f.level, hearth, a, 2L, 50.0));
        helper.assertTrue(total(a) > before, "a second Chronicle page paid nothing");
        before = total(a);
        HearthEvents.CHRONICLE_WRITTEN.fire(new HearthEvents.ChronicleWritten(f.level, hearth, a, 2L, 50.0));
        helper.assertTrue(total(a) == before, "the same Chronicle page paid twice");
        // The ones that come once stay once.
        HearthEvents.STAGE_SEALED.fire(new HearthEvents.StageSealed(f.level, hearth, a, 2));
        helper.assertTrue(total(a) == before, "a relit Hearth's stage paid twice");

        // A grant that pays nothing (here an amount that underflows to 0; in play a spectator or a
        // Homebuilder rate of 0) leaves the milestone owed instead of spending it. Mock players cannot
        // be spectators: GameTestHelper's mock overrides isSpectator().
        ServerPlayer c = f.player(3.5, 3.5);
        helper.assertTrue(HomebuilderXp.milestone(c, f.level, hearth, "stage:3", 1e-60,
                HomebuilderXp.STAGE_SOURCE + 3, true) == 0f, "an amount of 0 paid something");
        helper.assertFalse(HearthAttachments.of(c).hasMilestone(key + "|stage:3"), "a grant of 0 spent a milestone");
        HearthEvents.STAGE_SEALED.fire(new HearthEvents.StageSealed(f.level, hearth, c, 3));
        helper.assertTrue(HearthAttachments.of(c).hasMilestone(key + "|stage:3") && total(c) > 0f,
                "the owed milestone did not pay later");
        f.done();
    }

    /** Every Homebuilder XP point a player has, across levels. */
    private static float total(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        return skills.level(Skill.HOMEBUILDER) * 100_000f + skills.xp(Skill.HOMEBUILDER);
    }

    // ---- The passive ----------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_passive", timeoutTicks = 200)
    public static void thePassiveIsOnlyAtHomeAndGoesWithinTwoSeconds(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        PlayerSkills skills = maxed(player);
        double bonus = skills.bonus(Skill.HOMEBUILDER);
        HomePassive.tick(player);
        helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID) == 0.0,
                "a speed modifier away from home");
        f.home(player);
        HomePassive.tick(player);
        helper.assertTrue(Math.abs(modifier(player, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID)
                - HomebuilderMath.speed(bonus)) < 1e-9, "no speed at home");
        helper.assertTrue(Math.abs(modifier(player, Attributes.BLOCK_INTERACTION_RANGE, HomePassive.REACH_ID)
                - HomebuilderMath.reach(bonus)) < 1e-9, "no reach at home");
        // A smouldering Hearth takes both off at once.
        f.entity().state().setSmouldering(true);
        HomePassive.tick(player);
        helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID) == 0.0
                && modifier(player, Attributes.BLOCK_INTERACTION_RANGE, HomePassive.REACH_ID) == 0.0,
                "the modifiers stayed while the Hearth smouldered");
        f.entity().state().setSmouldering(false);
        f.home(player);
        HomePassive.tick(player);
        // Leaving range: nothing refreshes "home", and the per-player loop takes them off within 2 s.
        player.setPos(player.getX(), player.getY() + 40, player.getZ());
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID) == 0.0
                    && modifier(player, Attributes.BLOCK_INTERACTION_RANGE, HomePassive.REACH_ID) == 0.0,
                    "the modifiers outlived leaving range by more than 2 s");
            f.done();
        });
    }

    @GameTest(template = T, batch = "homebuilder_passive_break", timeoutTicks = 100)
    public static void breakingTheHearthTakesThePassiveWithin2s(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        maxed(player);
        f.home(player);
        HomePassive.tick(player);
        helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID) > 0.0, "no speed at home");
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID) == 0.0,
                    "the speed outlived the Hearth by more than 2 s");
            f.done();
        });
    }

    @GameTest(template = T, batch = "homebuilder_scaffolder")
    public static void scaffolderSoftensFallsOnlyAtHome(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        maxed(player);
        player.setHealth(20f);
        player.hurt(f.level.damageSources().fall(), 6f);
        helper.assertTrue(player.getHealth() == 14f, "away from home a fall hurts in full: " + player.getHealth());
        player.setHealth(20f);
        player.invulnerableTime = 0;
        f.home(player);
        // A real landing: the hit comes while the fall distance is still counted.
        player.fallDistance = 9f;
        player.hurt(f.level.damageSources().fall(), 6f);
        helper.assertTrue(player.getHealth() == 20f, "a 6-damage fall hurt at home with Scaffolder 3: " + player.getHealth());
        player.invulnerableTime = 0;
        player.fallDistance = 15f;
        player.hurt(f.level.damageSources().fall(), 12f);
        helper.assertTrue(player.getHealth() == 16f, "Scaffolder 3 takes 8 off a 12-damage fall: " + player.getHealth());
        // An ender pearl's landing is fall damage too, with the distance cleared first: not a fall.
        player.setHealth(20f);
        player.invulnerableTime = 0;
        player.resetFallDistance();
        player.hurt(f.level.damageSources().fall(), 5f);
        helper.assertTrue(player.getHealth() == 15f, "Scaffolder softened an ender pearl's landing: " + player.getHealth());
        f.done();
    }

    // ---- The magnet -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_magnet", timeoutTicks = 60)
    public static void theMagnetNeedsTidyHomeAndLeavesTheMachineryAlone(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.HOMEBUILDER, 100);
        f.home(player);
        helper.assertTrue(HomePassive.magnetRadius(player) == 0.0, "the magnet worked without Tidy Home");
        skills.fillTree(Skill.HOMEBUILDER);
        f.home(player);
        double radius = HomePassive.magnetRadius(player);
        helper.assertTrue(radius > 6.0, "Tidy Home and Long Pull should give a magnet past 6 blocks: " + radius);
        // Off while you sneak and while switched off.
        player.setShiftKeyDown(true);
        helper.assertTrue(HomePassive.magnetRadius(player) == 0.0, "the magnet pulled while sneaking");
        player.setShiftKeyDown(false);
        HearthAttachments.of(player).setMagnetOff(true);
        helper.assertTrue(HomePassive.magnetRadius(player) == 0.0, "the switch did not turn it off");

        Vec3 at = player.position();
        ItemEntity loose = new ItemEntity(f.level, at.x + 3, at.y, at.z, new ItemStack(Items.COBBLESTONE));
        loose.setDeltaMovement(Vec3.ZERO);
        f.level.addFreshEntity(loose);
        ItemEntity fresh = new ItemEntity(f.level, at.x - 3, at.y, at.z, new ItemStack(Items.DIAMOND));
        ServerPlayer friend = f.player(4.5, 4.5);
        fresh.setThrower(friend);
        f.level.addFreshEntity(fresh);
        ItemEntity delayed = new ItemEntity(f.level, at.x, at.y, at.z + 3, new ItemStack(Items.STICK));
        delayed.setPickUpDelay(32767);
        f.level.addFreshEntity(delayed);
        helper.setBlock(new BlockPos(4, 1, 0), Blocks.HOPPER);
        Vec3 onHopper = helper.absoluteVec(new Vec3(4.5, 2.0, 0.5));
        ItemEntity sorted = new ItemEntity(f.level, onHopper.x, onHopper.y, onHopper.z, new ItemStack(Items.APPLE));
        helper.assertTrue(HomePassive.pullable(loose, player), "a loose item was refused");
        helper.assertFalse(HomePassive.pullable(fresh, player), "another player's fresh drop was taken");
        helper.assertFalse(HomePassive.pullable(delayed, player), "an item with a mod's pickup delay was taken");
        helper.assertFalse(HomePassive.pullable(sorted, player), "an item on a hopper was taken");
        // A water source for two ticks only (it is gone before it can spread).
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.WATER);
        Vec3 inWater = helper.absoluteVec(new Vec3(0.5, 2.2, 4.5));
        ItemEntity floating = new ItemEntity(f.level, inWater.x, inWater.y, inWater.z, new ItemStack(Items.BONE));
        f.level.addFreshEntity(floating);
        ExperienceOrb orb = new ExperienceOrb(f.level, at.x, at.y, at.z - 3, 3);
        f.level.addFreshEntity(orb);

        helper.runAfterDelay(2, () -> {
            helper.assertFalse(HomePassive.pullable(floating, player), "an item in water flow was taken");
            helper.setBlock(new BlockPos(0, 2, 4), Blocks.AIR);
            helper.setBlock(new BlockPos(4, 1, 0), Blocks.STONE);
            HearthAttachments.of(player).setMagnetOff(false);
            f.home(player);
            loose.setPos(at.x + 3, at.y, at.z);
            orb.setPos(at.x, at.y, at.z - 3);
            int moved = HomePassive.pull(player);
            helper.assertTrue(moved >= 2, "the magnet moved " + moved + " entities");
            Vec3 toPlayer = player.position().subtract(loose.position());
            helper.assertTrue(loose.getDeltaMovement().dot(toPlayer) > 0, "the loose item did not fly towards the player");
            helper.assertTrue(loose.getDeltaMovement().y > 0, "the item should arc in, not slide");
            helper.assertTrue(orb.getDeltaMovement().dot(player.position().subtract(orb.position())) > 0,
                    "Tidy Home 2 should pull the experience orb");
            // The switch, from the wheel or /skills magnet.
            HomebuilderEvents.toggleMagnet(player, null);
            helper.assertTrue(HearthAttachments.of(player).magnetOff(), "the toggle did not switch it off");
            HomebuilderEvents.toggleMagnet(player, null);
            helper.assertFalse(HearthAttachments.of(player).magnetOff(), "the toggle did not switch it back on");
            for (net.minecraft.world.entity.Entity entity : List.of(loose, fresh, delayed, floating, orb)) {
                entity.discard();
            }
            f.done();
        });
    }

    // ---- Hearthglow -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_glow_cap")
    public static void hearthglowRespectsTheHourlyCap(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        maxed(player);
        f.home(player);
        HearthData data = Hearths.data();
        long now = f.level.getGameTime();
        f.entity().state().addCapped(HearthPool.HEARTY, data.hourlyCap(), now, data);
        double before = f.entity().state().charge(HearthPool.HEARTY);
        ProcService.forceNext(player, Skill.HOMEBUILDER);
        SkillService.grant(player, Skill.MINING, 50.0, null);
        helper.assertTrue(f.entity().state().charge(HearthPool.HEARTY) <= data.hourlyCap() + 1e-9
                && f.entity().state().charge(HearthPool.HEARTY) == before,
                "Hearthglow went past the hourly cap: " + f.entity().state().charge(HearthPool.HEARTY));
        f.done();
    }

    @GameTest(template = T, batch = "homebuilder_glow")
    public static void hearthglowChargesTheSamePoolAgain(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        maxed(player);
        f.home(player);
        ProcService.forceNext(player, Skill.HOMEBUILDER);
        float amount = SkillService.grant(player, Skill.MINING, 50.0, null);
        double extra = amount * Hearths.data().mirrorShare() * ProcService.power(player, Skill.HOMEBUILDER);
        double charge = f.entity().state().charge(HearthPool.HEARTY);
        helper.assertTrue(extra > 0 && charge >= extra - 1e-6,
                "Hearthglow should add " + extra + " to Hearty, it holds " + charge);
        double booked = f.entity().state().ledger().total(player.getUUID(), null, HearthLedger.Window.LIFETIME);
        helper.assertTrue(Math.abs(booked - charge) < 1e-6, "the ledger should hold every point: " + booked + " vs " + charge);
        // Masonry's mirror is held 10 minutes: no second copy straight into its pool.
        ProcService.forceNext(player, Skill.HOMEBUILDER);
        SkillService.grant(player, Skill.MASONRY, 50.0, null);
        helper.assertTrue(f.entity().state().charge(HearthPool.FORTIFIED) == 0.0, "Hearthglow skipped the hold");
        f.done();
    }

    // ---- House Proud ----------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_house_proud")
    public static void houseProudIsCastAtHomeAndShared(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer caster = f.player(1.5, 3.5);
        ServerPlayer friend = f.player(2.5, 3.5);
        ProficiencyAttachments.of(caster).setLevel(Skill.HOMEBUILDER, 60);
        helper.assertTrue(ActiveService.durationTicks(ProficiencyAttachments.of(caster), Skill.HOMEBUILDER)
                == ProficiencyConfig.houseProudTicks(), "House Proud should last its own 30 s");
        helper.assertTrue(ActiveService.cooldownTicks(ProficiencyAttachments.of(caster), Skill.HOMEBUILDER)
                == ProficiencyConfig.houseProudCooldownTicks(), "House Proud should cool down its own 6 minutes");
        helper.assertTrue(ActiveService.activate(GameTestRoots.open(caster, Skill.HOMEBUILDER), Skill.HOMEBUILDER) != null, "House Proud was cast away from home");
        f.home(caster);
        f.home(friend);
        helper.assertTrue(ActiveService.activate(GameTestRoots.open(caster, Skill.HOMEBUILDER), Skill.HOMEBUILDER) == null, "House Proud refused at home");
        double own = ProficiencyAttachments.of(caster).bonus(Skill.HOMEBUILDER);
        helper.assertTrue(Math.abs(HomePassive.bonus(caster) - 2 * own) < 1e-9, "the caster's passive did not double");
        helper.assertTrue(Math.abs(HouseProud.sharedFor(friend) - own) < 1e-9,
                "the friend should share the undoubled passive " + own + ", got " + HouseProud.sharedFor(friend));
        HomePassive.tick(friend);
        helper.assertTrue(Math.abs(modifier(friend, Attributes.MOVEMENT_SPEED, HomePassive.SPEED_ID)
                - HomebuilderMath.speed(own)) < 1e-9, "the friend did not get the shared speed");
        f.done();
    }

    @GameTest(template = T, batch = "homebuilder_house_proud_tie")
    public static void twoEqualHostsDoNotTakeTurns(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer first = f.player(1.5, 3.5);
        ServerPlayer second = f.player(2.5, 3.5);
        ServerPlayer friend = f.player(3.5, 3.5);
        for (ServerPlayer caster : List.of(first, second)) {
            ProficiencyAttachments.of(caster).setLevel(Skill.HOMEBUILDER, 60);
            f.home(caster);
        }
        f.home(friend);
        helper.assertTrue(ActiveService.activate(GameTestRoots.open(first, Skill.HOMEBUILDER), Skill.HOMEBUILDER) == null
                && ActiveService.activate(GameTestRoots.open(second, Skill.HOMEBUILDER), Skill.HOMEBUILDER) == null, "House Proud refused at home");
        // Both casters' seconds, twice over: the share that was there first stays.
        for (int round = 0; round < 2; round++) {
            HouseProud.tick(first);
            HouseProud.tick(second);
        }
        helper.assertTrue(first.getUUID().equals(HouseProud.sharedBy(friend)),
                "an equal second host took the share over: " + HouseProud.sharedBy(friend));
        f.done();
    }

    @GameTest(template = T, batch = "homebuilder_heart_of_home")
    public static void heartOfTheHomeReachesJoinersButNotTheHost(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        BlockPos hearth = f.hearth(1, 1);
        ServerPlayer host = f.player(1.5, 3.5);
        ServerPlayer eater = f.player(2.5, 3.5);
        ServerPlayer joiner = f.player(3.5, 3.5);
        PlayerSkills skills = maxed(host);
        skills.setLevel(Skill.SOCIAL, 100);
        skills.fillTree(Skill.SOCIAL);
        helper.assertTrue(HomebuilderMath.heartOfHome(skills), "the host should have Heart of the Home");
        double bonus = skills.bonus(Skill.HOMEBUILDER);
        // The Feast starts with two eaters, then a third joins alone (as the Tales lane fires it).
        HearthEvents.FEAST.fire(new HearthEvents.Feast(f.level, hearth, List.of(host, eater)));
        helper.assertTrue(HouseProud.sharedBy(host) == null, "the host was handed their own Feast");
        helper.assertTrue(host.getUUID().equals(HouseProud.sharedBy(eater))
                && Math.abs(HouseProud.sharedFor(eater) - bonus) < 1e-9, "the eater did not get the host's passive");
        HearthEvents.FEAST.fire(new HearthEvents.Feast(f.level, hearth, List.of(joiner)));
        helper.assertTrue(host.getUUID().equals(HouseProud.sharedBy(joiner)), "a joiner did not get the host's passive");
        f.done();
    }

    // ---- Night Watch ----------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_night_watch")
    public static void nightWatchSeesOnlySiegeMobs(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        f.hearth(1, 1);
        ServerPlayer player = f.player(1.5, 3.5);
        maxed(player);
        f.home(player);
        Zombie siege = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
        SpawnOrigin.tagSiege(siege);
        Zombie wild = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        f.entity().state().setSiegePhase(SiegeRules.Phase.ACTIVE);
        NightWatch.onHearthSecond(f.level, f.hearth, f.entity());
        helper.assertTrue(NightWatch.glowingFor(player).contains(siege.getId()), "the siege mob does not glow");
        helper.assertFalse(NightWatch.glowingFor(player).contains(wild.getId()), "an ordinary mob glows");
        helper.assertFalse(siege.hasGlowingTag(), "the glow must be this player's alone, not a real Glowing");
        f.entity().state().setSiegePhase(SiegeRules.Phase.IDLE);
        NightWatch.tick(player);
        helper.assertTrue(NightWatch.glowingFor(player).isEmpty(), "the glow outlived the siege");
        // The early horn, once per siege.
        NightWatch.warn(player, f.entity().key(), 5000L);
        helper.assertTrue(HearthAttachments.of(player).mark("homebuilder.night_watch|" + f.entity().key(), 0L) == 5000L,
                "the early horn left no mark");
        siege.discard();
        wild.discard();
        f.done();
    }

    // ---- Homeward -------------------------------------------------------------------------------

    @GameTest(template = T, batch = "homebuilder_homeward", timeoutTicks = 200)
    public static void homewardBreaksOnAHitOrAStepAndLandsHome(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        BlockPos hearth = f.hearth(1, 1);
        ServerPlayer player = f.player(4.5, 4.5);
        maxed(player);
        PlayerHearth data = HearthAttachments.of(player);
        fed(f, player, hearth);
        // A hit just before: the hurt cooldown is on, so a second equal hit lands nothing.
        player.hurt(f.level.damageSources().generic(), 1f);
        net.minecraft.network.chat.Component refused = Homeward.start(player);
        helper.assertTrue(refused == null, "Homeward did not start: " + (refused == null ? "" : refused.getString()));
        helper.assertTrue(Homeward.channelling(player), "no channel");
        float health = player.getHealth();
        player.hurt(f.level.damageSources().generic(), 1f);
        helper.assertTrue(player.getHealth() == health, "the hurt cooldown did not swallow the hit");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(Homeward.channelling(player), "a hit that landed nothing broke the channel");
            player.invulnerableTime = 0;
            player.hurt(f.level.damageSources().generic(), 1f);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertFalse(Homeward.channelling(player), "a hit did not break the channel");
            helper.assertTrue(data.homewardDay() == PlayerHearth.NEVER, "a broken channel spent the day");
            player.invulnerableTime = 0;
            helper.assertTrue(Homeward.start(player) == null, "Homeward did not start again");
            player.setPos(player.getX() + 2, player.getY(), player.getZ());
            helper.runAfterDelay(3, () -> {
                helper.assertFalse(Homeward.channelling(player), "a step did not break the channel");
                // The bell's 5 s channel, standing still: home.
                player.setPos(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
                data.heardBell(hearth.asLong(), f.level.dimension().location().toString(), f.level.getGameTime() + 1200);
                helper.assertTrue(Homeward.start(player) == null, "Homeward did not start after the bell");
                long channel = Math.round(Hearths.data().bell().homewardChannelSeconds() * 20);
                helper.runAfterDelay(channel + 10, () -> {
                    helper.assertFalse(Homeward.channelling(player), "the channel never ended");
                    double distance = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(hearth)));
                    helper.assertTrue(distance < 6.0, "Homeward did not land at the Hearth: " + distance);
                    helper.assertTrue(data.homewardDay() == Homeward.today(player.server), "the day was not spent");
                    helper.assertTrue(Homeward.start(player) != null, "Homeward worked twice in one day");
                    // A clock that stands still (doDaylightCycle false): a day of game time brings it back.
                    data.setMark(Homeward.AT_MARK, player.server.overworld().getGameTime() - HomebuilderMath.TICKS_PER_DAY);
                    helper.assertTrue(Homeward.ready(player), "a day of game time did not bring Homeward back");
                    f.done();
                });
            });
        });
    }

    @GameTest(template = T, batch = "homebuilder_homeward_death", timeoutTicks = 60)
    public static void aDeathMidChannelLosesTheTales(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Fixture f = new Fixture(helper);
        BlockPos hearth = f.hearth(1, 1);
        ServerPlayer player = f.player(4.5, 4.5);
        ServerPlayer respawned = f.player(4.5, 4.5);
        maxed(player);
        PlayerHearth data = HearthAttachments.of(player);
        fed(f, player, hearth);
        data.addTales(50.0);
        helper.assertTrue(Homeward.start(player) == null, "Homeward did not start");
        Vec3 where = player.position();
        player.setHealth(0f);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(Homeward.channelling(player), "the channel outlived the death");
            helper.assertTrue(player.position().distanceTo(where) < 0.01, "a dead player was carried home");
            helper.assertTrue(HearthAttachments.of(respawned).tales() == 0.0, "the Tales survived a death mid-channel");
            helper.assertTrue(HearthAttachments.of(respawned).homewardDay() == PlayerHearth.NEVER,
                    "a broken channel spent the day");
            f.done();
        });
    }
}
