package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SurvivalStreak;
import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world checks for the event wiring. The unit tests cover the arithmetic; these cover the part
 * that can only be wrong against a running server — that the right event fires, finds the right
 * skill for the tool in hand, and actually moves the number.
 *
 * <p>Excluded from the shipped jar by the {@code jar} task; they exist for {@code runGameTestServer}.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProficiencyGameTests {

    private static final BlockPos TARGET = new BlockPos(1, 1, 1);

    private ProficiencyGameTests() {
    }

    @GameTest(template = "empty")
    public static void miningStoneWithAPickaxeTrainsMining(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.setBlock(TARGET, Blocks.STONE);

        Breaks.separately(player, helper.absolutePos(TARGET));

        PlayerSkills skills = ProficiencyAttachments.of(player);
        helper.assertTrue(earned(skills, Skill.MINING), "breaking stone gave no Mining XP");
        helper.assertFalse(earned(skills, Skill.WOODCUTTING), "stone should not train Woodcutting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void choppingALogWithAnAxeTrainsWoodcutting(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        helper.setBlock(TARGET, Blocks.OAK_LOG);

        Breaks.separately(player, helper.absolutePos(TARGET));

        PlayerSkills skills = ProficiencyAttachments.of(player);
        helper.assertTrue(earned(skills, Skill.WOODCUTTING), "chopping a log gave no Woodcutting XP");
        helper.assertFalse(earned(skills, Skill.MINING), "a log should not train Mining");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void diggingDirtWithAShovelTrainsExcavation(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        helper.setBlock(TARGET, Blocks.DIRT);

        Breaks.separately(player, helper.absolutePos(TARGET));

        helper.assertTrue(earned(ProficiencyAttachments.of(player), Skill.EXCAVATION),
                "digging dirt gave no Excavation XP");
        helper.succeed();
    }

    /** Punching stone is not mining. Without the tool check, every skill would train at once. */
    @GameTest(template = "empty")
    public static void punchingStoneTrainsNothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.setBlock(TARGET, Blocks.STONE);

        Breaks.separately(player, helper.absolutePos(TARGET));

        helper.assertFalse(earned(ProficiencyAttachments.of(player), Skill.MINING),
                "bare hands should not train Mining");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTrainedSwordHitsHarderAndKeepsTraining(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ProficiencyAttachments.of(player).setLevel(Skill.SWORDS, 50);

        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
        cow.setHealth(cow.getMaxHealth());
        float before = cow.getHealth();

        cow.hurt(player.damageSources().playerAttack(player), 4.0f);
        float dealt = before - cow.getHealth();

        // Swords cap at +80%, so half a skill bar is +40%: 4.0 becomes 5.6.
        helper.assertTrue(dealt > 5.0f, "sword damage was not scaled by the skill: " + dealt);
        helper.assertTrue(earned(ProficiencyAttachments.of(player), Skill.SWORDS),
                "hitting with a sword gave no Swords XP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anEmptyHandTrainsUnarmed(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
        cow.setHealth(cow.getMaxHealth());
        cow.hurt(player.damageSources().playerAttack(player), 1.0f);

        PlayerSkills skills = ProficiencyAttachments.of(player);
        helper.assertTrue(earned(skills, Skill.UNARMED), "punching gave no Unarmed XP");
        helper.assertFalse(earned(skills, Skill.SWORDS), "punching should not train Swords");
        helper.succeed();
    }

    /**
     * Master Mining resolves a furnace recipe rather than keeping a table of ores. Worth a real
     * world to test, because the recipe manager is not something a unit test can stand up.
     */
    @GameTest(template = "empty")
    public static void masterMiningBringsOreUpSmelted(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        // A creative player drops nothing at all, which would make this test pass or fail for the
        // wrong reason.
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        ProficiencyAttachments.of(player).fillTree(Skill.MINING);
        // Mountain King would put the ingot straight in the bag; this test is about the smelting.
        ProficiencyAttachments.of(player).setRank(
                dev.amman.proficiency.perk.Talents.get(Skill.MINING, "mountain_king"), 0);
        helper.setBlock(TARGET, Blocks.IRON_ORE);

        Breaks.separately(player, helper.absolutePos(TARGET));

        var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(TARGET)).inflate(4.0));
        helper.assertTrue(!dropped.isEmpty(), "the ore dropped nothing at all");

        boolean smelted = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(TARGET)).inflate(4.0))
                .stream()
                .anyMatch(entity -> entity.getItem().is(Items.IRON_INGOT));
        helper.assertTrue(smelted, "Master Mining did not smelt the ore on the way up");
        helper.succeed();
    }

    /** Mountain King: what you mine goes to the inventory, and nothing is left on the ground. */
    @GameTest(template = "empty")
    public static void mountainKingPutsTheOreInYourBag(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        ProficiencyAttachments.of(player).fillTree(Skill.MINING);
        helper.setBlock(TARGET, Blocks.IRON_ORE);

        Breaks.separately(player, helper.absolutePos(TARGET));

        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                "the smelted ingot did not reach the inventory");
        boolean leftOnGround = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(TARGET)).inflate(4.0)).stream()
                .anyMatch(entity -> entity.getItem().is(Items.IRON_INGOT));
        helper.assertFalse(leftOnGround, "an ingot was left on the ground");
        helper.succeed();
    }

    /**
     * A synergy between trees is only real if the other tree actually gets paid. Mastery in Mining
     * plus full Precision in Smithing wakes Prospector's Forge, and smelting ore on the way up then
     * has to put XP into Smithing, a skill the player never touched.
     */
    @GameTest(template = "empty")
    public static void prospectorsForgePaysSmithingForMinedOre(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.fillTree(Skill.MINING);
        skills.setRank(dev.amman.proficiency.perk.Talents.get(Skill.SMITHING, "fine_edge"), 3);
        helper.assertTrue(dev.amman.proficiency.perk.TalentService.hasSynergy(player, "forge_smelt"),
                "Prospector's Forge did not wake with both needs met");
        helper.setBlock(TARGET, Blocks.IRON_ORE);

        Breaks.separately(player, helper.absolutePos(TARGET));

        helper.assertTrue(earned(skills, Skill.SMITHING),
                "mining smelted ore under Prospector's Forge paid Smithing nothing");
        helper.succeed();
    }

    /**
     * Timber fells the rest of the tree from inside the origin block's break event. That ordering
     * is what made the proc land on the wrong block once already, so the cascade itself is worth
     * pinning down.
     */
    @GameTest(template = "empty")
    public static void timberFellsTheWholeTrunk(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));

        // A frenzy makes the proc certain, so the test is about the cascade and not about luck.
        long now = helper.getLevel().getGameTime();
        ProficiencyAttachments.of(player)
                .beginFrenzy(Skill.WOODCUTTING, now + 400, now + 400);

        BlockPos base = new BlockPos(1, 1, 1);
        for (int y = 0; y < 3; y++) {
            helper.setBlock(base.above(y), Blocks.OAK_LOG);
        }

        Breaks.separately(player, helper.absolutePos(base));

        for (int y = 0; y < 3; y++) {
            helper.assertBlockPresent(Blocks.AIR, base.above(y));
        }
        helper.succeed();
    }

    /**
     * The streak end to end through the real code: the XP grant multiplies by it and marks the
     * player active, the per-second tick grows it, and the respawn clone wipes it along with every
     * partial bar while the levels stay. The clone event is posted by hand because a mock player
     * cannot respawn.
     */
    @GameTest(template = "empty")
    public static void theStreakPaysAndADeathTakesItAndTheBarButNoLevel(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.MINING, 20);
        skills.setLevel(Skill.EXCAVATION, 20);

        // Two skills, one grant each, so neither has a tempo chain going. Everything else in the
        // grant applies equally to both, so the ratio is the streak alone.
        SkillService.grant(player, Skill.MINING, 1.0);
        float plain = skills.xp(Skill.MINING);
        long step = ProficiencyConfig.streakStepTicks();
        skills.setStreakTicks(step * 50);
        SkillService.grant(player, Skill.EXCAVATION, 1.0);
        float boosted = skills.xp(Skill.EXCAVATION);
        helper.assertTrue(plain > 0f && Math.abs(boosted / plain - 1.5f) < 0.01f,
                "50 stacks should pay +50%, got " + plain + " -> " + boosted);

        // The grant just marked the player active, so one more second crosses into stack 3.
        skills.setStreakTicks(step * 3 - 20);
        SurvivalStreak.tick(player, 20);
        helper.assertTrue(SurvivalStreak.stacks(skills) == 3,
                "the tick should have reached 3 stacks, got " + SurvivalStreak.stacks(skills));

        ServerPlayer respawned = MockPlayers.make(helper);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
        PlayerSkills fresh = ProficiencyAttachments.of(respawned);
        helper.assertTrue(fresh.level(Skill.MINING) == 20 && fresh.level(Skill.EXCAVATION) == 20,
                "a death must never cost a level");
        helper.assertTrue(fresh.xp(Skill.MINING) == 0f && fresh.xp(Skill.EXCAVATION) == 0f,
                "a death must wipe the progress bar");
        helper.assertTrue(fresh.streakTicks() == 0L, "a death must wipe the streak");
        helper.succeed();
    }

    /**
     * Endurance end to end: a zombie's hit on a survival player pays Endurance XP through the real
     * damage pipeline (Post, after armour and absorption), and the maximum-health modifier follows
     * the level up and back down, clamping health on the way down.
     */
    @GameTest(template = "empty")
    public static void takingAMobHitTrainsEnduranceAndLevelsRaiseMaxHealth(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        clearSpawnProtection(player);
        PlayerSkills skills = ProficiencyAttachments.of(player);

        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        player.setHealth(player.getMaxHealth());
        boolean landed = player.hurt(player.damageSources().mobAttack(zombie), 4.0f);
        helper.assertTrue(landed, "the zombie's hit did not land on the mock player");
        helper.assertTrue(player.getHealth() < 20f, "the hit took no health: " + player.getHealth());
        helper.assertTrue(earned(skills, Skill.ENDURANCE), "a survived mob hit gave no Endurance XP");

        // Four XP from level 0 is already a level or two, and each is +1% of the base 20.
        int earnedLevel = skills.level(Skill.ENDURANCE);
        dev.amman.proficiency.event.EnduranceEvents.refreshMaxHealth(player);
        float expected = 20f * (1f + earnedLevel / 100f);
        helper.assertTrue(Math.abs(player.getMaxHealth() - expected) < 0.01f,
                "level " + earnedLevel + " should be " + expected + " max health, got " + player.getMaxHealth());

        skills.setLevel(Skill.ENDURANCE, 50);
        dev.amman.proficiency.event.EnduranceEvents.refreshMaxHealth(player);
        float atFifty = player.getMaxHealth();
        skills.setLevel(Skill.ENDURANCE, 100);
        dev.amman.proficiency.event.EnduranceEvents.refreshMaxHealth(player);
        float atHundred = player.getMaxHealth();
        helper.assertTrue(Math.abs(atFifty - 30f) < 0.01f, "level 50 should be 30 max health, got " + atFifty);
        helper.assertTrue(Math.abs(atHundred - 40f) < 0.01f, "level 100 should be 40 max health, got " + atHundred);

        player.setHealth(40f);
        skills.setLevel(Skill.ENDURANCE, 0);
        dev.amman.proficiency.event.EnduranceEvents.refreshMaxHealth(player);
        helper.assertTrue(player.getMaxHealth() <= 20.01f && player.getHealth() <= 20.01f,
                "losing the levels must drop max health and clamp health, got "
                        + player.getHealth() + "/" + player.getMaxHealth());
        helper.succeed();
    }

    /**
     * The world pays too, but through the rolling budget: a pile of cactus hits in one minute stops
     * paying at the cap. Each hit is spaced past the invulnerability frames by resetting them.
     */
    @GameTest(template = "empty")
    public static void environmentalEnduranceXpIsCapped(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        clearSpawnProtection(player);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.ENDURANCE, 10);

        for (int i = 0; i < 40; i++) {
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().cactus(), 2.0f);
        }
        // 40 hits x 2 health x 0.5 = 40 XP asked for; the budget allows 10 before the multipliers
        // (tempo can at most add half again, and nothing else is running on a fresh mock player).
        // The first cactus hit also pays the one-time first-time bonus, outside the cap on purpose:
        // it cannot be farmed, so it gets the same tempo headroom on top and nothing more.
        float xp = skills.xp(Skill.ENDURANCE);
        float allowed = 16f + (float) (dev.amman.proficiency.config.ProficiencyConfig.firstTimeXp() * 1.5);
        helper.assertTrue(xp > 0f, "cactus hits paid nothing at all");
        helper.assertTrue(skills.level(Skill.ENDURANCE) == 10 && xp <= allowed,
                "environmental XP ran past the cap: level " + skills.level(Skill.ENDURANCE) + ", " + xp + " XP");
        helper.succeed();
    }

    /**
     * A fresh ServerPlayer ignores most damage for its first 60 ticks, and nothing ticks a mock
     * player to count that down. The field is private; NeoForge runs on Mojang names, so it is
     * reachable by name in the test server.
     */
    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not clear spawn protection", e);
        }
    }

    /** Right-clicking a player registers them, and the server keeps the needle on their position. */
    @GameTest(template = "empty")
    public static void theFriendCompassRegistersAFriendAndFollowsThem(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.make(helper);
        ServerPlayer friend = MockPlayers.make(helper);
        ItemStack compass = new ItemStack(dev.amman.proficiency.item.ProficiencyItems.FRIEND_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.getItem().interactLivingEntity(compass, owner, friend, InteractionHand.MAIN_HAND);
        ItemStack held = owner.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(friend.getUUID().equals(dev.amman.proficiency.item.FriendCompassItem.friend(held)),
                "right-clicking a player must register them");
        var tracker = dev.amman.proficiency.item.ItemData.target(held);
        helper.assertTrue(tracker != null && tracker.pos().equals(friend.blockPosition()),
                "the needle must point at the friend right after registering");
        helper.succeed();
    }

    /** Idea 33: a furnace shift-click pays Cooking for the real stack, and only once. */
    @GameTest(template = "empty")
    public static void shiftClickingCookedFoodPaysCookingOnce(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        net.minecraft.world.inventory.FurnaceMenu menu =
                new net.minecraft.world.inventory.FurnaceMenu(0, player.getInventory());
        player.containerMenu = menu;
        menu.getSlot(2).set(new ItemStack(Items.COOKED_BEEF, 4));

        menu.quickMoveStack(player, 2);

        PlayerSkills skills = ProficiencyAttachments.of(player);
        helper.assertTrue(earned(skills, Skill.COOKING), "shift-clicking cooked beef paid no Cooking XP");
        helper.assertFalse(earned(skills, Skill.SMITHING), "shift-clicking cooked beef paid Smithing XP");
        helper.succeed();
    }

    // ---- Social (idea 35) ----------------------------------------------------------------------

    /**
     * Every mock player is placed at the world spawn and stays there after its test, so near the
     * spawn everyone has company. Social tests move their players to a spot of their own, far from
     * the spawn and from each other, so "alone" really is alone.
     */
    private static ServerPlayer socialPlayer(GameTestHelper helper, int spot) {
        ServerPlayer player = MockPlayers.make(helper);
        BlockPos base = helper.absolutePos(TARGET);
        player.setPos(base.getX() + 20_000.5 + spot * 1_000, base.getY(), base.getZ() + 20_000.5);
        dev.amman.proficiency.skill.CompanyBonus.forget(player.getUUID());
        dev.amman.proficiency.skill.SocialService.forget(player.getUUID());
        return player;
    }

    /** A Mining grant boosted by camaraderie pays Social a share of the extra, and only that. */
    @GameTest(template = "empty")
    public static void aGrantBoostedByCompanyPaysSocialAShareOfTheExtra(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 1);
        ServerPlayer friend = socialPlayer(helper, 1);
        PlayerSkills skills = ProficiencyAttachments.of(player);

        double company = dev.amman.proficiency.skill.CompanyBonus.multiplier(player, Skill.MINING);
        double expected = 1.0 + ProficiencyConfig.camaraderieBonus();
        helper.assertTrue(Math.abs(company - expected) < 1e-6,
                "two level-0 players side by side should have camaraderie " + expected + ", got " + company);

        float paid = SkillService.grant(player, Skill.MINING, 10.0, null);
        double pending = dev.amman.proficiency.skill.SocialService.pending(player);
        double share = dev.amman.proficiency.skill.SocialMath.share(paid, company, ProficiencyConfig.socialShare());
        helper.assertTrue(pending > 0 && Math.abs(pending - share) < 1e-4,
                "Social should hold " + share + " from a " + paid + " XP grant, holds " + pending);
        helper.assertFalse(earned(skills, Skill.SOCIAL), "Social was paid before the 5-second payout");

        float social = dev.amman.proficiency.skill.SocialService.flush(player);
        helper.assertTrue(social > 0 && earned(skills, Skill.SOCIAL), "the payout added no Social XP");
        helper.assertTrue(logged(player, Skill.SOCIAL, dev.amman.proficiency.skill.SocialService.SOURCE, null) > 0f,
                "the Social payout has no 'Working together' log line");
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) == 0.0,
                "the pot was not emptied by the payout");
        helper.assertFalse(earned(ProficiencyAttachments.of(friend), Skill.SOCIAL),
                "the friend who did nothing earned Social");
        helper.succeed();
    }

    /** Alone, a grant has no company factor and pays Social nothing. */
    @GameTest(template = "empty")
    public static void workingAlonePaysNoSocial(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 2);
        helper.assertTrue(dev.amman.proficiency.skill.CompanyBonus.multiplier(player, Skill.MINING) == 1.0,
                "a lone player had a company bonus");
        SkillService.grant(player, Skill.MINING, 10.0, null);
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) == 0.0
                        && dev.amman.proficiency.skill.SocialService.flush(player) == 0f,
                "working alone paid Social");
        helper.assertFalse(earned(ProficiencyAttachments.of(player), Skill.SOCIAL), "working alone paid Social");
        helper.succeed();
    }

    /**
     * No feedback loop: company never multiplies Social, and a Social grant never pays Social.
     * An op's addxp is not work either.
     */
    @GameTest(template = "empty")
    public static void socialNeverEarnsFromItselfOrFromACommand(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 3);
        socialPlayer(helper, 3);
        PlayerSkills skills = ProficiencyAttachments.of(player);

        helper.assertTrue(dev.amman.proficiency.skill.CompanyBonus.multiplier(player, Skill.SOCIAL) == 1.0,
                "company multiplied Social");
        float paid = SkillService.grant(player, Skill.SOCIAL, 10.0, null);
        float plain = (float) (10.0 * baseRate(Skill.SOCIAL));
        helper.assertTrue(Math.abs(paid - plain) < 1e-3, "a Social grant was boosted: " + paid + " vs " + plain);
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) == 0.0,
                "a Social grant fed Social");

        SkillService.grant(player, Skill.MINING, 10.0, "proficiency.xplog.source.command");
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) == 0.0,
                "an addxp command fed Social");
        helper.assertTrue(skills.xp(Skill.MINING) > 0f || skills.level(Skill.MINING) > 0,
                "the command grant itself did not land");
        helper.succeed();
    }

    /**
     * Balance telemetry counts a real grant in memory: kind, base and final XP, and the line it
     * drains to carries the player's id. Nothing is written to disk by a grant.
     */
    @GameTest(template = "empty")
    public static void telemetryCountsAGrant(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 9);
        dev.amman.proficiency.telemetry.TelemetryHub.data().drain(0L, "");
        SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
        SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
        java.util.List<String> lines = dev.amman.proficiency.telemetry.TelemetryHub.data().drain(1L, "");
        String id = player.getUUID().toString();
        boolean found = lines.stream().anyMatch(l -> l.contains(id) && l.contains("\"type\":\"xp\"")
                && l.contains("\"skill\":\"mining\"") && l.contains("\"kind\":\"block\"")
                && l.contains("\"n\":2") && l.contains("\"base\":20"));
        helper.assertTrue(found, "no block XP bucket for the grant: " + lines);
        helper.succeed();
    }

    /**
     * A death wipes unpaid Social XP too: the clone handler empties the pot, so a quick respawn
     * before the next 1-second tick cannot collect it.
     */
    @GameTest(template = "empty")
    public static void deathWipesTheUnpaidSocialPot(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 5);
        socialPlayer(helper, 5);
        SkillService.grant(player, Skill.MINING, 10.0, null);
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) > 0.0,
                "the company grant filled no Social pot");

        ServerPlayer respawned = MockPlayers.make(helper);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) == 0.0,
                "a death left unpaid Social XP in the pot");
        helper.assertTrue(dev.amman.proficiency.skill.SocialService.flush(player) == 0f,
                "Social paid out after a death");
        helper.assertFalse(earned(ProficiencyAttachments.of(player), Skill.SOCIAL), "Social paid out after a death");
        helper.succeed();
    }

    /** Standing together doing nothing makes no grants, so three seconds of it pay no Social. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void standingTogetherIdlePaysNoSocial(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 4);
        ServerPlayer friend = socialPlayer(helper, 4);
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(earned(ProficiencyAttachments.of(player), Skill.SOCIAL), "idle company paid Social");
            helper.assertFalse(earned(ProficiencyAttachments.of(friend), Skill.SOCIAL), "idle company paid Social");
            helper.assertTrue(dev.amman.proficiency.skill.SocialService.pending(player) == 0.0,
                    "idle company filled the Social pot");
            helper.succeed();
        });
    }

    /**
     * The tree changes the bonus: a full Social tree makes an equal partner a mentor (Heart of the
     * Group) and keeps the bonus for a while after the partner leaves (Stay a While).
     */
    @GameTest(template = "empty")
    public static void aFullSocialTreeRaisesAndHoldsTheCompanyBonus(GameTestHelper helper) {
        // Spot 8: no other test uses it. It shared spot 5 with another test, and when the grid put
        // the two tests side by side, that test's players counted as company here.
        ServerPlayer player = socialPlayer(helper, 8);
        ServerPlayer friend = socialPlayer(helper, 8);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        double before = dev.amman.proficiency.skill.CompanyBonus.multiplier(player, Skill.MINING);

        skills.setLevel(Skill.SOCIAL, 100);
        skills.fillTree(Skill.SOCIAL);
        dev.amman.proficiency.skill.CompanyBonus.forget(player.getUUID());
        double after = dev.amman.proficiency.skill.CompanyBonus.multiplier(player, Skill.MINING);
        double mentor = ProficiencyConfig.mentorBonus() + 3 * dev.amman.proficiency.skill.SocialMath.MENTOR_PER_RANK;
        helper.assertTrue(after >= 1.0 + mentor - 1e-6,
                "a full tree should give at least the raised mentor bonus with an equal partner: "
                        + before + " -> " + after);

        // The friend walks far away. Stay a While keeps the bonus; the cache is cleared each time so
        // the result is a real rescan, not a remembered number.
        friend.setPos(friend.getX() + 500, friend.getY(), friend.getZ());
        dev.amman.proficiency.skill.CompanyBonus.rescan(player.getUUID());
        double lingering = dev.amman.proficiency.skill.CompanyBonus.multiplier(player, Skill.MINING);
        helper.assertTrue(Math.abs(lingering - after) < 1e-9,
                "the bonus did not linger after the friend left: " + after + " -> " + lingering);

        // Without Stay a While the same walk-away ends it at once.
        ServerPlayer plain = socialPlayer(helper, 7);
        ServerPlayer plainFriend = socialPlayer(helper, 7);
        helper.assertTrue(dev.amman.proficiency.skill.CompanyBonus.multiplier(plain, Skill.MINING) > 1.0,
                "the control pair had no company");
        plainFriend.setPos(plainFriend.getX() + 500, plainFriend.getY(), plainFriend.getZ());
        dev.amman.proficiency.skill.CompanyBonus.rescan(plain.getUUID());
        helper.assertTrue(dev.amman.proficiency.skill.CompanyBonus.multiplier(plain, Skill.MINING) == 1.0,
                "company lingered with no Stay a While");
        helper.succeed();
    }

    /** Good Company: a landed proc inspires everyone in the company radius. */
    @GameTest(template = "empty")
    public static void goodCompanyInspiresEveryoneNear(GameTestHelper helper) {
        ServerPlayer player = socialPlayer(helper, 6);
        ServerPlayer friend = socialPlayer(helper, 6);
        ProficiencyAttachments.of(player).setLevel(Skill.SOCIAL, 30);
        SkillService.grant(player, Skill.MINING, 10.0, null);
        dev.amman.proficiency.skill.ProcService.forceNext(player, Skill.SOCIAL);
        dev.amman.proficiency.skill.SocialService.flush(player);
        double multiplier = dev.amman.proficiency.skill.SocialMath.goodCompany(1.0);
        helper.assertTrue(Math.abs(SkillService.inspiredMultiplier(friend) - multiplier) < 1e-6,
                "the friend was not inspired: " + SkillService.inspiredMultiplier(friend));
        helper.assertTrue(Math.abs(SkillService.inspiredMultiplier(player) - multiplier) < 1e-6,
                "the player was not inspired: " + SkillService.inspiredMultiplier(player));
        helper.succeed();
    }

    // ---- Nightwalker (idea 36) -----------------------------------------------------------------

    /** Inside of the sealed stone box (dark) and of the glowstone box (lit), relative to the test. */
    private static final BlockPos DARK_ROOM = new BlockPos(1, 1, 1);
    private static final BlockPos LIT_ROOM = new BlockPos(3, 1, 3);

    /**
     * Two sealed 1x2 rooms in the 5x5x5 test space: one of stone (no light at all), one with a
     * glowstone floor (block light 13-14 where you stand). Real light, so the tests see what the
     * game sees; the light engine needs a few ticks to catch up, hence the delays.
     */
    private static void buildNightRooms(GameTestHelper helper) {
        sealRoom(helper, DARK_ROOM, Blocks.STONE);
        sealRoom(helper, LIT_ROOM, Blocks.GLOWSTONE);
    }

    private static void sealRoom(GameTestHelper helper, BlockPos inside, net.minecraft.world.level.block.Block floor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos at = inside.offset(dx, dy, dz);
                    boolean hollow = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                    if (hollow) {
                        helper.setBlock(at, Blocks.AIR);
                    } else {
                        helper.setBlock(at, dy == -1 && dx == 0 && dz == 0 ? floor : Blocks.STONE);
                    }
                }
            }
        }
    }

    private static ServerPlayer nightPlayer(GameTestHelper helper, BlockPos room) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(room)));
        dev.amman.proficiency.skill.NightwalkerService.forget(player.getUUID());
        return player;
    }

    /**
     * XP source 1 and its anti-farm rules, with real light: a grant in the stone room puts a share
     * in the pot and pays one "In the dark" line; the same grant in the lit room pays nothing;
     * Nightwalker's own grants, Social payouts and an addxp command never feed it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aGrantInTheDarkPaysNightwalkerAShare(GameTestHelper helper) {
        buildNightRooms(helper);
        ServerPlayer dark = nightPlayer(helper, DARK_ROOM);
        ServerPlayer lit = nightPlayer(helper, LIT_ROOM);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.isDark(dark),
                    "the sealed stone room is not dark");
            helper.assertFalse(dev.amman.proficiency.skill.NightwalkerService.isDark(lit),
                    "the glowstone room counts as dark");

            SkillService.grant(dark, Skill.MINING, 10.0, null);
            double expected = dev.amman.proficiency.skill.NightwalkerMath.share(10.0,
                    ProficiencyConfig.xpRate(Skill.MINING), 1.0, ProficiencyConfig.nightShare());
            double pending = dev.amman.proficiency.skill.NightwalkerService.pending(dark);
            helper.assertTrue(pending > 0 && Math.abs(pending - expected) < 1e-4,
                    "the dark grant should hold " + expected + ", holds " + pending);
            helper.assertFalse(earned(ProficiencyAttachments.of(dark), Skill.NIGHTWALKER),
                    "Nightwalker was paid before the 5-second payout");
            float paid = dev.amman.proficiency.skill.NightwalkerService.flush(dark);
            helper.assertTrue(paid > 0 && earned(ProficiencyAttachments.of(dark), Skill.NIGHTWALKER),
                    "the payout added no Nightwalker XP");
            helper.assertTrue(logged(dark, Skill.NIGHTWALKER,
                            dev.amman.proficiency.skill.NightwalkerService.DARK_SOURCE, null) > 0f,
                    "the payout has no 'In the dark' log line");
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.pending(dark) == 0.0,
                    "the payout did not empty the pot");

            SkillService.grant(lit, Skill.MINING, 10.0, null);
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.pending(lit) == 0.0,
                    "a grant in the light fed Nightwalker");

            SkillService.grant(dark, Skill.NIGHTWALKER, 10.0, null);
            SkillService.grant(dark, Skill.MINING, 10.0, "proficiency.xplog.source.command");
            SkillService.grant(dark, Skill.SOCIAL, 10.0, dev.amman.proficiency.skill.SocialService.SOURCE);
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.pending(dark) == 0.0,
                    "Nightwalker, an addxp command or a Social payout fed Nightwalker");
            helper.succeed();
        });
    }

    /** XP source 2: a full active minute pays one "Out at night" trickle; an idle minute pays nothing. */
    @GameTest(template = "empty")
    public static void theNightTricklePaysActiveMinutesOnly(GameTestHelper helper) {
        ServerPlayer walker = nightPlayer(helper, DARK_ROOM);
        ServerPlayer idle = nightPlayer(helper, LIT_ROOM);
        float paid = 0f;
        for (int i = 0; i <= dev.amman.proficiency.skill.NightwalkerMath.TRICKLE_SECONDS; i++) {
            paid += dev.amman.proficiency.skill.NightwalkerService.step(walker,
                    new dev.amman.proficiency.skill.NightwalkerMath.Second(true, true, true, false, false, false,
                            i * 2.0, 0.0, i * 5f, 0f));
        }
        helper.assertTrue(paid > 0f, "a full active minute outdoors at night paid nothing");
        helper.assertTrue(logged(walker, Skill.NIGHTWALKER,
                        dev.amman.proficiency.skill.NightwalkerService.NIGHT_SOURCE, null) > 0f,
                "the trickle has no 'Out at night' log line");
        for (int i = 0; i < 180; i++) {
            dev.amman.proficiency.skill.NightwalkerService.step(idle,
                    new dev.amman.proficiency.skill.NightwalkerMath.Second(true, true, true, false, false, false,
                            4.0, 4.0, 0f, 0f));
        }
        helper.assertFalse(earned(ProficiencyAttachments.of(idle), Skill.NIGHTWALKER),
                "three idle minutes outdoors at night paid Nightwalker");
        helper.succeed();
    }

    /** A death wipes the unpaid dark share and the partial night minute. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void deathWipesTheNightwalkerPotAndMinute(GameTestHelper helper) {
        buildNightRooms(helper);
        ServerPlayer player = nightPlayer(helper, DARK_ROOM);
        helper.runAfterDelay(40, () -> {
            SkillService.grant(player, Skill.MINING, 10.0, null);
            for (int i = 0; i < 10; i++) {
                dev.amman.proficiency.skill.NightwalkerService.step(player,
                        new dev.amman.proficiency.skill.NightwalkerMath.Second(true, true, true, false, false,
                                false, i * 2.0, 0.0, i * 5f, 0f));
            }
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.pending(player) > 0.0,
                    "the dark grant filled no pot");
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.trickleSeconds(player) > 0,
                    "the walk counted no seconds");
            ServerPlayer respawned = MockPlayers.make(helper);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.pending(player) == 0.0,
                    "a death left unpaid Nightwalker XP");
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.trickleSeconds(player) == 0,
                    "a death left the partial night minute");
            helper.succeed();
        });
    }

    /**
     * Moonlit fires on a kill in the dark and not in the light; with Hunter's Moon a second dark
     * kill while it runs renews it and adds Strength.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void moonlitFiresOnADarkKillAndHuntersMoonRenewsIt(GameTestHelper helper) {
        buildNightRooms(helper);
        ServerPlayer dark = nightPlayer(helper, DARK_ROOM);
        ServerPlayer lit = nightPlayer(helper, LIT_ROOM);
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 0));
        helper.runAfterDelay(40, () -> {
            ProficiencyAttachments.of(dark).setLevel(Skill.NIGHTWALKER, 30);
            ProficiencyAttachments.of(lit).setLevel(Skill.NIGHTWALKER, 30);
            dev.amman.proficiency.skill.ProcService.forceNext(dark, Skill.NIGHTWALKER);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(zombie,
                    dark.damageSources().playerAttack(dark)));
            helper.assertTrue(dark.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                            && dark.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
                    "a dark kill with a landed proc gave no Moonlit");
            helper.assertFalse(dark.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST),
                    "Moonlit gave Strength without Hunter's Moon");

            dev.amman.proficiency.skill.ProcService.forceNext(lit, Skill.NIGHTWALKER);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(zombie,
                    lit.damageSources().playerAttack(lit)));
            helper.assertFalse(lit.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION),
                    "a kill in the light gave Moonlit");
            dev.amman.proficiency.skill.ProcService.forget(lit.getUUID());

            PlayerSkills skills = ProficiencyAttachments.of(dark);
            skills.setLevel(Skill.NIGHTWALKER, 100);
            skills.fillTree(Skill.NIGHTWALKER);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(zombie,
                    dark.damageSources().playerAttack(dark)));
            helper.assertTrue(dark.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST),
                    "Hunter's Moon did not renew Moonlit with Strength on a dark kill");
            zombie.discard();
            helper.succeed();
        });
    }

    /**
     * Eclipse: a hostile mob in the dark more than 8 blocks away cannot take you as a target, and
     * the sweep drops one that already has you. Near, or without Eclipse, it keeps you.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void eclipseMakesFarMobsInTheDarkLoseYou(GameTestHelper helper) {
        buildNightRooms(helper);
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, DARK_ROOM);
        ServerPlayer player = MockPlayers.make(helper);
        ServerPlayer plain = MockPlayers.make(helper);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.isDark(zombie),
                    "the zombie's room is not dark");
            ProficiencyAttachments.of(player).setLevel(Skill.NIGHTWALKER, 50);
            helper.assertTrue(dev.amman.proficiency.skill.ActiveService.activate(player, Skill.NIGHTWALKER) == null,
                    "Eclipse did not start");

            player.setPos(zombie.getX() + 12, zombie.getY(), zombie.getZ());
            zombie.setTarget(player);
            helper.assertTrue(zombie.getTarget() == null, "a far mob in the dark took an Eclipse player as target");

            player.setPos(zombie.getX() + 4, zombie.getY(), zombie.getZ());
            zombie.setTarget(player);
            helper.assertTrue(zombie.getTarget() == player, "a near mob lost an Eclipse player");
            player.setPos(zombie.getX() + 12, zombie.getY(), zombie.getZ());
            dev.amman.proficiency.skill.NightwalkerService.eclipseSweep(player);
            helper.assertTrue(zombie.getTarget() == null, "the sweep did not drop a far target");

            plain.setPos(zombie.getX() + 12, zombie.getY(), zombie.getZ());
            zombie.setTarget(plain);
            helper.assertTrue(zombie.getTarget() == plain, "a player without Eclipse was lost");
            zombie.discard();
            helper.succeed();
        });
    }

    /** Darkborn Bane hits harder on a mob marked as spawned in the dark; Deep Calm shortens Darkness. */
    @GameTest(template = "empty")
    public static void darkbornBaneAndDeepCalm(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        ServerPlayer plain = MockPlayers.make(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.NIGHTWALKER, 100);
        skills.fillTree(Skill.NIGHTWALKER);

        var marked = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 0));
        var unmarked = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(0, 1, 4));
        dev.amman.proficiency.event.NightwalkerEvents.markDarkborn(marked);
        float before = marked.getHealth();
        marked.hurt(player.damageSources().playerAttack(player), 4.0f);
        float markedLoss = before - marked.getHealth();
        before = unmarked.getHealth();
        unmarked.hurt(player.damageSources().playerAttack(player), 4.0f);
        float plainLoss = before - unmarked.getHealth();
        helper.assertTrue(plainLoss > 0 && markedLoss > plainLoss * 1.1f,
                "Darkborn Bane: " + markedLoss + " vs " + plainLoss);
        marked.discard();
        unmarked.discard();

        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DARKNESS, 260));
        plain.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DARKNESS, 260));
        var calmed = player.getEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
        var full = plain.getEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
        helper.assertTrue(calmed != null && calmed.getDuration() == 65,
                "Deep Calm 3 should leave 65 of 260 ticks: " + (calmed == null ? "none" : calmed.getDuration()));
        helper.assertTrue(full != null && full.getDuration() == 260, "Darkness was shortened without Deep Calm");
        helper.succeed();
    }

    /** Sanctuary covers 16 blocks around the respawn point of a player who has it, and nothing else. */
    @GameTest(template = "empty")
    public static void sanctuaryGuardsTheRespawnPoint(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(2, 1, 2)).offset(0, 0, 40_000);
        player.setRespawnPosition(level.dimension(), home, 0f, true, false);
        helper.assertFalse(dev.amman.proficiency.event.NightwalkerEvents.sanctuaryCovers(level,
                home.getX() + 10.5, home.getY(), home.getZ() + 0.5), "Sanctuary without the talent");
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.NIGHTWALKER, 100);
        skills.fillTree(Skill.NIGHTWALKER);
        helper.assertTrue(dev.amman.proficiency.event.NightwalkerEvents.sanctuaryCovers(level,
                home.getX() + 10.5, home.getY(), home.getZ() + 0.5), "Sanctuary does not cover 10 blocks");
        helper.assertFalse(dev.amman.proficiency.event.NightwalkerEvents.sanctuaryCovers(level,
                home.getX() + 30.5, home.getY(), home.getZ() + 0.5), "Sanctuary covers 30 blocks");
        player.setRespawnPosition(level.dimension(), null, 0f, false, false);
        helper.succeed();
    }

    private static boolean earned(PlayerSkills skills, Skill skill) {
        return skills.level(skill) > 0 || skills.xp(skill) > 0f;
    }

    // ---- Station recipes ----------------------------------------------------------------------

    private static void drop(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        BlockPos at = helper.absolutePos(pos);
        ItemEntity item = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack);
        item.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(item);
    }

    private static long countOf(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2.0))
                .stream().filter(e -> e.isAlive() && e.getItem().is(item)).mapToLong(e -> e.getItem().getCount()).sum();
    }

    private static void dropStew(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        drop(helper, TARGET, new ItemStack(Items.BOWL));
        drop(helper, TARGET, new ItemStack(Items.COOKED_BEEF));
        drop(helper, TARGET, new ItemStack(Items.BAKED_POTATO));
        drop(helper, TARGET, new ItemStack(Items.BROWN_MUSHROOM));
    }

    /** A cook who learned Camp Kitchen stirs the cauldron: stew out, ingredients and a level of water gone. */
    @GameTest(template = "empty")
    public static void aLadleOnACauldronCooksAKnownRecipe(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack ladle = new ItemStack(dev.amman.proficiency.item.ProficiencyItems.LADLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ladle);
        ProficiencyAttachments.of(player).setRank(
                dev.amman.proficiency.perk.Talents.get(Skill.COOKING, "camp_kitchen"), 1);
        dropStew(helper);

        var result = dev.amman.proficiency.recipe.StationEvents.use(player, helper.getLevel(),
                helper.absolutePos(TARGET), InteractionHand.MAIN_HAND, ladle);

        helper.assertTrue(result != null && result.consumesAction(), "the ladle did nothing");
        helper.assertTrue(countOf(helper, TARGET, dev.amman.proficiency.item.ProficiencyItems.MINERS_STEW.get()) == 1,
                "no Miner's Stew came out");
        helper.assertTrue(countOf(helper, TARGET, Items.COOKED_BEEF) == 0, "the beef was not used up");
        helper.assertBlockProperty(TARGET, net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2);
        helper.assertTrue(ladle.getDamageValue() == 1, "the ladle took no wear");
        helper.assertTrue(earned(ProficiencyAttachments.of(player), Skill.COOKING), "cooking gave no Cooking XP");
        helper.succeed();
    }

    /** The same pot, a player who never learned it: nothing is made and nothing is used up. */
    @GameTest(template = "empty")
    public static void anUnknownRecipeMakesNothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        ItemStack ladle = new ItemStack(dev.amman.proficiency.item.ProficiencyItems.LADLE.get());
        dropStew(helper);

        dev.amman.proficiency.recipe.StationEvents.use(player, helper.getLevel(),
                helper.absolutePos(TARGET), InteractionHand.MAIN_HAND, ladle);

        helper.assertTrue(countOf(helper, TARGET, dev.amman.proficiency.item.ProficiencyItems.MINERS_STEW.get()) == 0,
                "a player without Camp Kitchen made the stew");
        helper.assertTrue(countOf(helper, TARGET, Items.COOKED_BEEF) == 1, "the beef was used up for nothing");
        helper.succeed();
    }

    /** A hammer on a bare anvil is left to vanilla, so the anvil screen still opens. */
    @GameTest(template = "empty")
    public static void aHammerOnABareAnvilIsAnOrdinaryClick(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        helper.setBlock(TARGET, Blocks.ANVIL);
        var result = dev.amman.proficiency.recipe.StationEvents.use(player, helper.getLevel(),
                helper.absolutePos(TARGET), InteractionHand.MAIN_HAND,
                new ItemStack(dev.amman.proficiency.item.ProficiencyItems.SMITHING_HAMMER.get()));
        helper.assertTrue(result == null, "the hammer swallowed a plain anvil click");
        helper.succeed();
    }

    /** Runescribe: reagents on the table, a book on it, and the book comes back as Magnetism. */
    @GameTest(template = "empty")
    public static void aBookOnTheTableWritesAKnownEnchantment(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        ProficiencyAttachments.of(player).setRank(
                dev.amman.proficiency.perk.Talents.get(Skill.ALCHEMY, "runescribe"), 1);
        helper.setBlock(TARGET, Blocks.ENCHANTING_TABLE);
        BlockPos top = TARGET.above();
        drop(helper, top, new ItemStack(Items.IRON_INGOT, 4));
        drop(helper, top, new ItemStack(Items.REDSTONE, 4));
        drop(helper, top, new ItemStack(Items.LAPIS_LAZULI, 4));
        ItemStack book = new ItemStack(Items.BOOK, 2);

        dev.amman.proficiency.recipe.StationEvents.use(player, helper.getLevel(),
                helper.absolutePos(TARGET), InteractionHand.MAIN_HAND, book);

        var magnetism = dev.amman.proficiency.item.SpecialItemEvents.MAGNETISM.get();
        boolean written = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(TARGET)).inflate(2.0)).stream()
                .anyMatch(e -> e.getItem().is(Items.ENCHANTED_BOOK)
                        && net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(e.getItem())
                                .getOrDefault(magnetism, 0) == 1);
        helper.assertTrue(written, "no Magnetism book came out");
        helper.assertTrue(book.getCount() == 1, "the book in hand was not used");
        helper.succeed();
    }

    // ---- Discovery and the first-time bonus ---------------------------------------------------

    /** The XP a log entry set paid for one skill and source prefix, and how many gains it merged. */
    private static float logged(ServerPlayer player, Skill skill, String sourcePrefix, int[] gains) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == skill.ordinal() && entry.source().startsWith(sourcePrefix)) {
                    sum += entry.amount();
                    if (gains != null) {
                        gains[0] += entry.count();
                    }
                }
            }
        }
        return sum;
    }

    private static double baseRate(Skill skill) {
        return ProficiencyConfig.xpRate(skill) * ProficiencyConfig.xpMultiplier();
    }

    /**
     * A real block break pays the first-time bonus once for the whole family. Iron ore pays it, a
     * second iron ore and a deepslate iron ore do not, and the bonus is its own "first|" log line.
     */
    @GameTest(template = "empty")
    public static void aRealBreakPaysTheFirstTimeBonusOncePerFamily(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        PlayerSkills skills = ProficiencyAttachments.of(player);

        helper.setBlock(TARGET, Blocks.IRON_ORE);
        Breaks.separately(player, helper.absolutePos(TARGET));

        // Iron is an ore, so the tier is x2 on top of firstTimeXp.
        float floor = (float) (ProficiencyConfig.firstTimeXp() * 2.0 * baseRate(Skill.MINING)) - 0.01f;
        int[] gains = new int[1];
        float bonus = logged(player, Skill.MINING, "first|", gains);
        helper.assertTrue(bonus >= floor, "the first iron ore paid " + bonus + " first-time XP, wanted at least " + floor);
        helper.assertTrue(gains[0] == 1, "expected one first-time line, got " + gains[0]);
        helper.assertTrue(skills.hasVisited("first:" + Skill.MINING.id() + ":block.minecraft.iron_ore"),
                "the iron ore kind was not marked visited");

        BlockPos second = TARGET.east();
        helper.setBlock(second, Blocks.IRON_ORE);
        Breaks.separately(player, helper.absolutePos(second));
        BlockPos deep = TARGET.east(2);
        helper.setBlock(deep, Blocks.DEEPSLATE_IRON_ORE);
        Breaks.separately(player, helper.absolutePos(deep));

        gains[0] = 0;
        float after = logged(player, Skill.MINING, "first|", gains);
        helper.assertTrue(gains[0] == 1 && Math.abs(after - bonus) < 0.001f,
                "a second iron ore or a deepslate one paid the bonus again: " + gains[0] + " lines, " + after + " XP");
        helper.assertFalse(skills.hasVisited("first:" + Skill.MINING.id() + ":block.minecraft.deepslate_iron_ore"),
                "the deepslate variant got a key of its own");
        helper.succeed();
    }

    /**
     * Idea 31: a block the held tool cannot harvest pays no XP and no first-time bonus. Emerald and
     * iron ore with a wooden or stone pickaxe pay nothing; an iron pickaxe pays on both.
     */
    @GameTest(template = "empty")
    public static void unharvestableOresPayNothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        // The mock player may keep the creative ability after setGameMode; this test needs the survival rule.
        player.getAbilities().instabuild = false;
        PlayerSkills skills = ProficiencyAttachments.of(player);

        BlockPos emerald = TARGET;
        BlockPos iron = TARGET.east();
        helper.setBlock(emerald, Blocks.EMERALD_ORE);
        helper.setBlock(iron, Blocks.IRON_ORE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        Breaks.separately(player, helper.absolutePos(emerald));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        Breaks.separately(player, helper.absolutePos(iron));

        helper.assertTrue(logged(player, Skill.MINING, "", null) == 0f,
                "ore the tool cannot harvest paid Mining XP: " + logged(player, Skill.MINING, "", null));
        helper.assertFalse(skills.hasVisited("first:" + Skill.MINING.id() + ":block.minecraft.emerald_ore"),
                "an unharvestable emerald ore marked the first-time kind");

        helper.setBlock(emerald, Blocks.EMERALD_ORE);
        helper.setBlock(iron, Blocks.IRON_ORE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        Breaks.separately(player, helper.absolutePos(emerald));
        Breaks.separately(player, helper.absolutePos(iron));
        helper.assertTrue(logged(player, Skill.MINING, "", null) > 0f, "an iron pickaxe paid no Mining XP");
        helper.assertTrue(logged(player, Skill.MINING, "first|", null) > 0f, "an iron pickaxe paid no first-time bonus");
        helper.succeed();
    }

    /**
     * Planting wheat from seeds is a placement, but it is farming: Farming XP, none for Masonry or
     * Decorating. Stone still pays Masonry and a flower still pays Decorating. Replanting the same
     * spot inside the window pays nothing twice.
     */
    @GameTest(template = "empty")
    public static void plantingPaysFarmingNotMasonry(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos wheat = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos sapling = helper.absolutePos(new BlockPos(2, 2, 1));
        BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos poppy = helper.absolutePos(new BlockPos(4, 2, 1));

        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.isPlanting(Blocks.WHEAT.defaultBlockState()), "wheat is not planting");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.isPlanting(Blocks.OAK_SAPLING.defaultBlockState()), "a sapling is not planting");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.isPlanting(Blocks.NETHER_WART.defaultBlockState()), "nether wart is not planting");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.isPlanting(Blocks.MELON_STEM.defaultBlockState()), "a stem is not planting");
        helper.assertFalse(dev.amman.proficiency.skill.BuildClassifier.isPlanting(Blocks.POPPY.defaultBlockState()), "a flower counts as planting");
        helper.assertFalse(dev.amman.proficiency.skill.BuildClassifier.isPlanting(Blocks.STONE.defaultBlockState()), "stone counts as planting");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.skillFor(Blocks.WHEAT.defaultBlockState()) == Skill.FARMING, "held seeds do not map to Farming");

        dev.amman.proficiency.event.ExpansionEvents.placed(player, Blocks.WHEAT.defaultBlockState(), wheat);
        float farming = logged(player, Skill.FARMING, "", null);
        helper.assertTrue(farming > 0f, "planting wheat paid no Farming XP");
        helper.assertTrue(logged(player, Skill.MASONRY, "", null) == 0f, "planting wheat paid Masonry XP");
        helper.assertTrue(logged(player, Skill.DECORATING, "", null) == 0f, "planting wheat paid Decorating XP");

        dev.amman.proficiency.event.ExpansionEvents.placed(player, Blocks.WHEAT.defaultBlockState(), wheat);
        helper.assertTrue(logged(player, Skill.FARMING, "", null) == farming, "replanting the same spot paid twice");

        dev.amman.proficiency.event.ExpansionEvents.placed(player, Blocks.OAK_SAPLING.defaultBlockState(), sapling);
        helper.assertTrue(logged(player, Skill.MASONRY, "", null) == 0f, "planting a sapling paid Masonry XP");

        dev.amman.proficiency.event.ExpansionEvents.placed(player, Blocks.STONE.defaultBlockState(), stone);
        helper.assertTrue(logged(player, Skill.MASONRY, "", null) > 0f, "placing stone paid no Masonry XP");

        dev.amman.proficiency.event.ExpansionEvents.placed(player, Blocks.POPPY.defaultBlockState(), poppy);
        helper.assertTrue(logged(player, Skill.DECORATING, "", null) > 0f, "placing a flower paid no Decorating XP");
        helper.succeed();
    }

    /** A light in either hand switches Nightwalker's darkness off. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heldLightSwitchesNightwalkerOff(GameTestHelper helper) {
        buildNightRooms(helper);
        ServerPlayer player = nightPlayer(helper, DARK_ROOM);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.isDark(player), "empty hands in the dark room are not dark");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.isDark(player), "stone in hand switched it off");
            for (net.minecraft.world.item.Item light : new net.minecraft.world.item.Item[] {
                    Items.TORCH, Items.SOUL_TORCH, Items.LANTERN, Items.GLOWSTONE}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(light));
                helper.assertFalse(dev.amman.proficiency.skill.NightwalkerService.isDark(player), light + " in the main hand left it dark");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(light));
                helper.assertFalse(dev.amman.proficiency.skill.NightwalkerService.isDark(player), light + " in the off hand left it dark");
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            helper.assertTrue(dev.amman.proficiency.skill.NightwalkerService.isDark(player), "empty hands again are not dark");
            helper.succeed();
        });
    }

    /** A block a player placed pays Masonry on placing and nothing on breaking; natural stone pays Mining. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void placedBlocksPayNoGatheringXp(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos placedRel = new BlockPos(1, 1, 1);
        BlockPos naturalRel = new BlockPos(3, 1, 1);
        BlockPos placed = helper.absolutePos(placedRel);
        helper.setBlock(placedRel, Blocks.STONE);
        helper.setBlock(naturalRel, Blocks.STONE);
        dev.amman.proficiency.skill.PlacedBlocks.markPlacement(level, placed, Blocks.STONE.defaultBlockState());
        dev.amman.proficiency.event.ExpansionEvents.placed(player, Blocks.STONE.defaultBlockState(), placed);
        helper.assertTrue(logged(player, Skill.MASONRY, "", null) > 0f, "placing stone paid no Masonry XP");

        Breaks.separately(player, placed);
        helper.assertTrue(logged(player, Skill.MINING, "", null) == 0f,
                "breaking a placed stone paid Mining XP: " + logged(player, Skill.MINING, "", null));
        helper.assertFalse(dev.amman.proficiency.skill.PlacedBlocks.rawMarked(level, placed), "the mark outlived the block");

        Breaks.separately(player, helper.absolutePos(naturalRel));
        helper.assertTrue(logged(player, Skill.MINING, "", null) > 0f, "breaking natural stone paid no Mining XP");
        helper.succeed();
    }

    /** A planted crop pays on a ripe harvest and nothing while unripe. */
    @GameTest(template = "empty")
    public static void plantedCropPaysOnlyWhenRipe(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos unripe = new BlockPos(1, 1, 1);
        BlockPos ripe = new BlockPos(3, 1, 1);
        helper.setBlock(unripe, Blocks.WHEAT);
        helper.setBlock(ripe, Blocks.WHEAT);
        dev.amman.proficiency.skill.PlacedBlocks.markPlacement(level, helper.absolutePos(unripe), Blocks.WHEAT.defaultBlockState());
        dev.amman.proficiency.skill.PlacedBlocks.markPlacement(level, helper.absolutePos(ripe), Blocks.WHEAT.defaultBlockState());

        Breaks.separately(player, helper.absolutePos(unripe));
        helper.assertTrue(logged(player, Skill.FARMING, "", null) == 0f, "an unripe planted crop paid Farming XP");

        // It grows to full age in place; the mark stays, and a ripe harvest still pays.
        helper.setBlock(ripe, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        Breaks.separately(player, helper.absolutePos(ripe));
        helper.assertTrue(logged(player, Skill.FARMING, "", null) > 0f, "a ripe planted crop paid no Farming XP");
        helper.succeed();
    }

    /** A piston carries the placed mark with the block it pushes. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pistonCarriesThePlacedMark(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // The empty template is tiny, so other tests leave blocks around; use a clean row.
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 8, 1), Blocks.AIR);
        }
        helper.setBlock(new BlockPos(1, 8, 1), Blocks.PISTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(new BlockPos(2, 8, 1), Blocks.STONE);
        dev.amman.proficiency.skill.PlacedBlocks.markPlacement(level, helper.absolutePos(new BlockPos(2, 8, 1)), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(1, 8, 2), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(30, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 8, 1));
            BlockPos moved = helper.absolutePos(new BlockPos(3, 8, 1));
            helper.assertTrue(dev.amman.proficiency.skill.PlacedBlocks.rawMarked(level, moved), "the mark did not follow the pushed block");
            helper.assertFalse(dev.amman.proficiency.skill.PlacedBlocks.rawMarked(level, helper.absolutePos(new BlockPos(2, 8, 1))), "the old spot kept its mark");
            Breaks.separately(player, moved);
            helper.assertTrue(logged(player, Skill.MINING, "", null) == 0f, "a pushed placed block paid Mining XP");
            helper.succeed();
        });
    }

    /** Spawn-egg mobs pay no combat XP, spawner mobs a quarter, natural ones all of it. */
    @GameTest(template = "empty")
    public static void mobOriginScalesCombatXp(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 1, 3);
        net.minecraft.world.entity.Mob[] mobs = new net.minecraft.world.entity.Mob[3];
        mobs[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        mobs[1] = EntityType.ZOMBIE.spawn(helper.getLevel(), (ItemStack) null, null, helper.absolutePos(at),
                net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, false, false);
        mobs[2] = EntityType.ZOMBIE.spawn(helper.getLevel(), (ItemStack) null, null, helper.absolutePos(at),
                net.minecraft.world.entity.MobSpawnType.SPAWNER, false, false);
        float[] paid = new float[3];
        for (int i = 0; i < 3; i++) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            mobs[i].setNoAi(true);
            mobs[i].hurt(player.damageSources().playerAttack(player), 2.0f);
            paid[i] = logged(player, Skill.SWORDS, "entity.minecraft.zombie", null);
        }
        helper.assertTrue(paid[0] > 0f, "a natural zombie paid no Swords XP");
        helper.assertTrue(paid[1] == 0f, "a spawn-egg zombie paid Swords XP: " + paid[1]);
        helper.assertTrue(Math.abs(paid[2] - 0.25f * paid[0]) < 0.02f * paid[0],
                "a spawner zombie paid " + paid[2] + " against " + paid[0]);
        helper.succeed();
    }

    /** Masonry vs Decorating: full cubes build, stairs split, fences, glass and panes decorate. */
    @GameTest(template = "empty")
    public static void blocksSplitBetweenMasonryAndDecorating(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        Object[][] cases = {
                {Blocks.STONE, 1, 0}, {Blocks.OAK_STAIRS, 2, 2}, {Blocks.OAK_FENCE, 0, 1},
                {Blocks.GLASS, 0, 1}, {Blocks.GLASS_PANE, 0, 1}, {Blocks.WHEAT, 0, 0}};
        int x = 1;
        for (Object[] c : cases) {
            net.minecraft.world.level.block.Block block = (net.minecraft.world.level.block.Block) c[0];
            String id = block.getDescriptionId();
            dev.amman.proficiency.event.ExpansionEvents.placed(player, block.defaultBlockState(), helper.absolutePos(new BlockPos(x++, 2, 1)));
            float masonry = logged(player, Skill.MASONRY, id, null);
            float decorating = logged(player, Skill.DECORATING, id, null);
            float farming = logged(player, Skill.FARMING, id, null);
            String what = id + " masonry=" + masonry + " decorating=" + decorating;
            if (block == Blocks.STONE) {
                helper.assertTrue(masonry > 0f && decorating == 0f && farming == 0f, what);
            } else if (block == Blocks.OAK_STAIRS) {
                helper.assertTrue(masonry > 0f && decorating > 0f
                        && Math.abs(masonry - decorating) < 0.25f * Math.max(masonry, decorating), what);
            } else if (block == Blocks.WHEAT) {
                helper.assertTrue(farming > 0f && masonry == 0f && decorating == 0f, what);
            } else {
                helper.assertTrue(decorating > 0f && masonry == 0f && farming == 0f, what);
            }
        }
        // Stairs pay one first-time bonus, to Masonry only.
        helper.assertTrue(logged(player, Skill.DECORATING, "first|" + Blocks.OAK_STAIRS.getDescriptionId(), null) == 0f,
                "the Decorating half of a stair paid a first-time bonus");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.isSplit(Blocks.OAK_SLAB.defaultBlockState()), "a slab is not split");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.isSplit(Blocks.COBBLESTONE_WALL.defaultBlockState()), "a wall is not split");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.skillFor(Blocks.OAK_DOOR.defaultBlockState()) == Skill.DECORATING, "a door is not Decorating");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.skillFor(Blocks.LANTERN.defaultBlockState()) == Skill.DECORATING, "a lantern is not Decorating");
        helper.assertTrue(dev.amman.proficiency.skill.BuildClassifier.skillFor(Blocks.OAK_PLANKS.defaultBlockState()) == Skill.MASONRY, "planks are not Masonry");
        helper.succeed();
    }

    /** Breaking an unripe crop pays no Farming XP; a ripe one pays 1.0. */
    @GameTest(template = "empty")
    public static void unripeCropPaysNothingOnBreak(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, Blocks.WHEAT);
        helper.assertTrue(dev.amman.proficiency.skill.SkillTools.harvestSkill(new ItemStack(Items.IRON_HOE),
                Blocks.WHEAT.defaultBlockState()) == null, "unripe wheat still counts as a harvest");
        helper.assertTrue(dev.amman.proficiency.skill.SkillTools.harvestSkill(new ItemStack(Items.IRON_HOE),
                Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7)) == Skill.FARMING,
                "ripe wheat does not count as a harvest");
        helper.assertFalse(dev.amman.proficiency.skill.SkillTools.isRipe(Blocks.NETHER_WART.defaultBlockState()), "unripe wart is ripe");
        helper.assertTrue(dev.amman.proficiency.skill.SkillTools.isRipe(Blocks.NETHER_WART.defaultBlockState()
                .setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 3)), "ripe wart is not ripe");
        helper.assertFalse(dev.amman.proficiency.skill.SkillTools.isRipe(Blocks.MELON_STEM.defaultBlockState()), "a stem is ripe");
        helper.assertTrue(dev.amman.proficiency.skill.SkillTools.isRipe(Blocks.PUMPKIN.defaultBlockState()), "a pumpkin block is not ripe");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        Breaks.separately(player, helper.absolutePos(rel));
        helper.assertTrue(logged(player, Skill.FARMING, "", null) == 0f, "breaking unripe wheat paid Farming XP");
        helper.assertFalse(ProficiencyAttachments.of(player).hasVisited("first:" + Skill.FARMING.id() + ":block.minecraft.wheat"),
                "breaking unripe wheat marked the first-time kind");

        helper.setBlock(rel, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        Breaks.separately(player, helper.absolutePos(rel));
        helper.assertTrue(logged(player, Skill.FARMING, "", null) >= 1.0f, "breaking ripe wheat paid no Farming XP");
        helper.succeed();
    }

    /**
     * AgriCraft support is a soft compat: without the mod it must change nothing. The test world has no
     * AgriCraft, so every question answers "no crop" and a plain wheat field pays exactly as before.
     */
    @GameTest(template = "empty")
    public static void agricraftSupportIsInertWithoutTheMod(GameTestHelper helper) {
        helper.assertFalse(dev.amman.proficiency.compat.AgriCraftCompat.active(), "AgriCraft support is on without the mod");
        helper.assertFalse(dev.amman.proficiency.compat.AgriCraftCompat.isCropBlock(Blocks.WHEAT.defaultBlockState()),
                "wheat counts as an AgriCraft crop");
        helper.assertTrue(dev.amman.proficiency.compat.AgriCraftCompat.snapshot(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1))) == null, "a plain position has an AgriCraft snapshot");
        helper.assertTrue(dev.amman.proficiency.skill.SkillTools.breakMatch(Blocks.WHEAT.defaultBlockState(), 0.0)
                .has("crop"), "wheat lost its crop rule");
        helper.assertTrue(dev.amman.proficiency.skill.SkillTools.isRipeCrop(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7)),
                "ripe wheat is not a ripe crop");
        helper.succeed();
    }

    /**
     * Masonry pays a few first-time kinds a day. Eleven distinct blocks placed through the grant path
     * pay ten, and the eleventh stays unseen so tomorrow it still counts.
     */
    @GameTest(template = "empty")
    public static void masonryFirstTimeBonusStopsAtTheDailyCap(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int cap = ProficiencyConfig.firstTimeBuildPerDay();
        net.minecraft.world.level.block.Block[] kinds = {
                Blocks.STONE, Blocks.COBBLESTONE, Blocks.BRICKS, Blocks.SANDSTONE, Blocks.OAK_PLANKS,
                Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.GRANITE, Blocks.DIORITE,
                Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.GLASS};
        helper.assertTrue(kinds.length > cap, "the test needs more kinds than the cap of " + cap);

        // The mock player reports isCreative() true whatever its game mode, and the place handler
        // skips creative players, so the event cannot be posted here. This is the call it makes.
        for (var kind : kinds) {
            SkillService.grant(player, Skill.MASONRY, 1.0, kind.getDescriptionId());
        }

        int paid = 0;
        for (String key : skills.visitedKeys()) {
            if (key.startsWith("first:" + Skill.MASONRY.id() + ":")) {
                paid++;
            }
        }
        helper.assertTrue(paid == cap, "expected " + cap + " Masonry bonuses, " + paid + " were paid");
        String last = "first:" + Skill.MASONRY.id() + ":" + kinds[kinds.length - 1].getDescriptionId();
        helper.assertFalse(skills.hasVisited(last), "the kind over the cap was marked visited");
        int[] gains = new int[1];
        logged(player, Skill.MASONRY, "first|", gains);
        // The log keeps eight lines, so only presence is checkable here; the count is the keys above.
        helper.assertTrue(gains[0] > 0, "no first-time line reached the XP log");
        helper.succeed();
    }

    /** A fresh player's first check records the dimension and biome and pays nothing. */
    @GameTest(template = "empty")
    public static void theSpawnPlaceIsRecordedAndPaysNothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        helper.assertFalse(skills.hasAnyPlace(), "a fresh player already had a place");

        dev.amman.proficiency.event.ExpansionEvents.checkWhereYouAre(player);

        helper.assertTrue(skills.hasAnyPlace(), "the spawn dimension and biome were not recorded");
        helper.assertTrue(skills.hasVisited("dim:" + player.level().dimension().location()),
                "the dimension was not marked");
        helper.assertFalse(earned(skills, Skill.WAYFARING), "the spawn place paid Wayfaring XP");
        // A second check in the same place is still nothing.
        dev.amman.proficiency.event.ExpansionEvents.checkWhereYouAre(player);
        helper.assertFalse(earned(skills, Skill.WAYFARING), "standing still paid Wayfaring XP");
        helper.succeed();
    }

    /**
     * Registers a one-block structure start at the player's feet, the way worldgen would leave it
     * in the chunk, so the real structure manager finds it.
     */
    private static void plantStructure(GameTestHelper helper, ServerPlayer player, String id) {
        var level = helper.getLevel();
        var structure = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(new net.minecraft.resources.ResourceLocation(id));
        if (structure == null) {
            throw new IllegalStateException("no such structure " + id);
        }
        BlockPos at = player.blockPosition();
        var piece = new net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces
                .BuriedTreasurePiece(at);
        var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure,
                new net.minecraft.world.level.ChunkPos(at), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(java.util.List.of(piece)));
        var chunk = level.getChunk(at);
        chunk.setStartForStructure(structure, start);
        chunk.addReferenceForStructure(structure, new net.minecraft.world.level.ChunkPos(at).toLong());
    }

    private static void discoverStructure(GameTestHelper helper, String id, String canonical, int baseXp) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        PlayerSkills skills = ProficiencyAttachments.of(player);
        // The first check is the spawn rule; it records the place silently.
        dev.amman.proficiency.event.ExpansionEvents.checkWhereYouAre(player);
        helper.assertFalse(skills.hasVisited("structure:" + canonical), "structure visited before it was planted");

        plantStructure(helper, player, id);
        dev.amman.proficiency.event.ExpansionEvents.checkWhereYouAre(player);

        helper.assertTrue(skills.hasVisited("structure:" + canonical), canonical + " was not marked visited");
        String source = new net.minecraft.resources.ResourceLocation(canonical).toLanguageKey("structure");
        int[] gains = new int[1];
        float paid = logged(player, Skill.WAYFARING, source, gains);
        float floor = (float) (baseXp * baseRate(Skill.WAYFARING)) - 0.01f;
        helper.assertTrue(gains[0] == 1 && paid >= floor,
                canonical + " paid " + paid + " over " + gains[0] + " gains, wanted at least " + floor);

        // Still standing inside it: a second check finds nothing new.
        float before = skills.xp(Skill.WAYFARING) + skills.level(Skill.WAYFARING) * 1000f;
        dev.amman.proficiency.event.ExpansionEvents.checkWhereYouAre(player);
        gains[0] = 0;
        logged(player, Skill.WAYFARING, source, gains);
        float afterXp = skills.xp(Skill.WAYFARING) + skills.level(Skill.WAYFARING) * 1000f;
        helper.assertTrue(gains[0] == 1 && afterXp == before, "an immediate second check paid again");
        helper.succeed();
    }

    /** A grand structure found through the real check pays grandStructureXp, once. */
    @GameTest(template = "empty")
    public static void aGrandStructureIsDiscoveredOnceThroughTheRealCheck(GameTestHelper helper) {
        discoverStructure(helper, "minecraft:ancient_city", "minecraft:ancient_city",
                ProficiencyConfig.grandStructureXp());
    }

    /** A village variant is counted as the plain village, and pays the ordinary amount. */
    @GameTest(template = "empty")
    public static void aVillageVariantCountsAsTheVillage(GameTestHelper helper) {
        discoverStructure(helper, "minecraft:village_taiga", "minecraft:village",
                ProficiencyConfig.structureXp());
    }
}
