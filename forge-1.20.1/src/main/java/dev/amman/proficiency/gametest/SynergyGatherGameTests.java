package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.GatheringEvents;
import dev.amman.proficiency.event.TalentCraftingEvents;
import dev.amman.proficiency.event.TalentGatheringEvents;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.NightwalkerService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Synergies 2, Batch B3: the eight gathering, food and proc-roll synergies, each with a control case
 * (the same setup without the synergy, or without the condition it asks for). Each test is its own
 * batch, so water, logs and a Hearth never reach a neighbour.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SynergyGatherGameTests {

    private static final String T = "empty";

    private SynergyGatherGameTests() {
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

    /** Takes back every water block the test (and its flow) left inside the structure. */
    private static void drain(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int y = 2; y < 6; y++) {
                for (int z = 0; z < 8; z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    if (!helper.getBlockState(rel).getFluidState().isEmpty()) {
                        helper.setBlock(rel, Blocks.AIR);
                    }
                }
            }
        }
    }

    private static void flood(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
    }

    /** The test's footprint plus a margin: where drops and mobs of this test can be. */
    private static AABB area(GameTestHelper helper) {
        return new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(0, 0, 0))),
                Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(8, 8, 8)))).inflate(3);
    }

    /** Logs lying about plus logs swept into the player's pack (Sawmill). */
    private static int logsOut(GameTestHelper helper, ServerPlayer player) {
        return items(helper, Items.OAK_LOG) + TalentGatheringEvents.countInPack(player, Items.OAK_LOG);
    }

    private static int items(GameTestHelper helper, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper))) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    private static void clearItems(GameTestHelper helper) {
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper))) {
            entity.discard();
        }
    }

    // ---- Harpooneer -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_harpooneer", timeoutTicks = 100)
    public static void harpooneerBringsUpACatchFromAMobInWater(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 1.5, 2, 0.5);
        rank(with, Skill.TRIDENTS, "undertow", 3);
        rank(with, Skill.FISHING, "deep_waters", 3);
        rank(without, Skill.TRIDENTS, "undertow", 3);
        flood(helper, 4, 4, 6, 6);
        Mob wet = helper.spawnWithNoFreeWill(EntityType.DROWNED, new BlockPos(5, 2, 5));
        Mob dry = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 6));
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(wet.isInWater(), "setup: the drowned should be in water");
                helper.assertFalse(dry.isInWater(), "setup: the zombie should be dry");
                for (int i = 0; i < 40; i++) {
                    ProcService.forceNext(without, Skill.TRIDENTS);
                    ProcService.fire(without, Skill.TRIDENTS, wet);
                }
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(),
                        "a Riptide Strike without the synergy brought up a catch");
                for (int i = 0; i < 40; i++) {
                    ProcService.forceNext(with, Skill.TRIDENTS);
                    ProcService.fire(with, Skill.TRIDENTS, dry);
                }
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(),
                        "a strike on a mob out of the water brought up a catch");
                for (int i = 0; i < 40; i++) {
                    ProcService.forceNext(with, Skill.TRIDENTS);
                    ProcService.fire(with, Skill.TRIDENTS, wet);
                }
                helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper)).isEmpty(),
                        "forty strikes on a mob in water brought up nothing at 25% each");
            } finally {
                clearItems(helper);
                drain(helper);
            }
            finish(helper, with, without);
        });
    }

    // ---- Orchard Keeper -------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_orchard_keeper")
    public static void orchardKeeperPlantsTheSaplingOneStageAhead(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 1.5, 2, 0.5);
        rank(with, Skill.WOODCUTTING, "sapling_keeper", 3);
        rank(with, Skill.FARMING, "fertile_soil", 3);
        rank(without, Skill.WOODCUTTING, "sapling_keeper", 3);
        ServerLevel level = helper.getLevel();
        BlockPos control = new BlockPos(4, 2, 4);
        BlockPos planted = new BlockPos(6, 2, 4);
        helper.setBlock(control.below(), Blocks.DIRT);
        helper.setBlock(planted.below(), Blocks.DIRT);

        TalentGatheringEvents.replantSapling(level, without, helper.absolutePos(control), Blocks.OAK_LOG);
        BlockState plain = helper.getBlockState(control);
        helper.assertTrue(plain.is(Blocks.OAK_SAPLING), "the control sapling was not planted");
        helper.assertTrue(plain.getValue(SaplingBlock.STAGE) == 0, "the control sapling did not start at stage 0");

        TalentGatheringEvents.replantSapling(level, with, helper.absolutePos(planted), Blocks.OAK_LOG);
        BlockState ahead = helper.getBlockState(planted);
        helper.assertTrue(ahead.is(Blocks.OAK_SAPLING), "the sapling was not planted");
        helper.assertTrue(ahead.getValue(SaplingBlock.STAGE) == 1, "the sapling did not start one stage ahead");
        helper.setBlock(control, Blocks.AIR);
        helper.setBlock(planted, Blocks.AIR);
        finish(helper, with, without);
    }

    // ---- Charcoal Burner ------------------------------------------------------------------------

    private static void growTree(GameTestHelper helper) {
        for (int x = 4; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                for (int z = 4; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
                }
            }
        }
    }

    @GameTest(template = T, batch = "syn_charcoal_burner", timeoutTicks = 100)
    public static void charcoalBurnerFeedsAnEighthOfTheTreeToTheFurnace(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 0.5, 2, 1.5);
        rank(with, Skill.WOODCUTTING, "sawmill", 1);
        rank(with, Skill.SMITHING, "forgefire", 2);
        rank(without, Skill.WOODCUTTING, "sawmill", 1);
        BlockPos furnacePos = new BlockPos(2, 2, 0);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(furnacePos);
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));

        growTree(helper);
        GatheringEvents.timber(level, without, origin);
        helper.assertTrue(furnace.getItem(1).isEmpty(), "a Timber without the synergy fed the furnace");
        int controlDrops = logsOut(helper, without);
        helper.assertTrue(controlDrops >= 15, "the control Timber dropped only " + controlDrops + " logs");

        clearItems(helper);
        growTree(helper);
        GatheringEvents.timber(level, with, origin);
        ItemStack fuel = furnace.getItem(1);
        helper.assertTrue(fuel.is(Items.OAK_LOG) && fuel.getCount() == 2,
                "a 16-log tree should put 2 logs in the fuel slot, found " + fuel);
        helper.assertTrue(logsOut(helper, with) == controlDrops - 2,
                "the two fed logs should be gone from the drops (" + logsOut(helper, with) + " vs " + controlDrops + ")");

        // A furnace already burning something else keeps it.
        clearItems(helper);
        furnace.setItem(1, new ItemStack(Items.COAL, 3));
        growTree(helper);
        GatheringEvents.timber(level, with, origin);
        helper.assertTrue(furnace.getItem(1).is(Items.COAL) && furnace.getItem(1).getCount() == 3,
                "the furnace's coal was replaced or topped up with logs");
        clearItems(helper);
        for (int x = 4; x <= 5; x++) {
            for (int y = 2; y <= 5; y++) {
                for (int z = 4; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(furnacePos, Blocks.AIR);
        finish(helper, with, without);
    }

    // ---- Dredger --------------------------------------------------------------------------------

    private static float shovelSpeed(ServerPlayer player) {
        return shovelSpeed(player, 1f);
    }

    private static float shovelSpeed(ServerPlayer player, float original) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event = new net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed(player, Blocks.SAND.defaultBlockState(), original, null);
        GatheringEvents.onBreakSpeed(event);
        return event.getNewSpeed();
    }

    @GameTest(template = T, batch = "syn_dredger", timeoutTicks = 100)
    public static void dredgerUndoesTheWaterAndAirPenalties(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 0.5, 2, 1.5);
        rank(with, Skill.EXCAVATION, "wide_shovel", 1);
        rank(with, Skill.SWIMMING, "aqua_eyes", 1);
        rank(without, Skill.EXCAVATION, "wide_shovel", 1);

        // Dry and on the ground: nothing to undo.
        with.setOnGround(true);
        float dry = shovelSpeed(with);
        helper.assertTrue(dry == shovelSpeed(without), "Dredger changed the dry, grounded speed");

        // In the air: x5 for the synergy, nothing for the control.
        with.setOnGround(false);
        without.setOnGround(false);
        float airControl = shovelSpeed(without);
        helper.assertTrue(Math.abs(shovelSpeed(with) / airControl - 5f) < 1e-3,
                "in the air the ratio should be 5, got " + shovelSpeed(with) / airControl);

        flood(helper, 0, 0, 2, 2);
        helper.runAfterDelay(3, () -> {
            try {
                with.baseTick();
                without.baseTick();
                with.setOnGround(true);
                without.setOnGround(true);
                helper.assertTrue(with.isEyeInFluid(FluidTags.WATER), "setup: the diver's eyes should be under water");
                float wetControl = shovelSpeed(without);
                helper.assertTrue(Math.abs(shovelSpeed(with) / wetControl - 5f) < 1e-3,
                        "under water on the ground the ratio should be 5, got " + shovelSpeed(with) / wetControl);
                with.setOnGround(false);
                without.setOnGround(false);
                float swimControl = shovelSpeed(without);
                helper.assertTrue(Math.abs(shovelSpeed(with) / swimControl - 25f) < 1e-2,
                        "swimming under water the ratio should be 25, got " + shovelSpeed(with) / swimControl);
                // The speed vanilla hands the event after both penalties comes back as the dry one.
                helper.assertTrue(Math.abs(shovelSpeed(with, 1f / 25f) - dry) < 1e-4f,
                        "Dredger did not restore the dry, grounded speed (" + shovelSpeed(with, 1f / 25f) + " vs " + dry + ")");
            } finally {
                drain(helper);
            }
            finish(helper, with, without);
        });
    }

    // ---- Moon Garden ----------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_moon_garden")
    public static void moonGardenRaisesBountifulHarvestAtNightOnly(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 1.5, 2, 0.5);
        rank(with, Skill.FARMING, "harvest_festival", 2);
        rank(with, Skill.NIGHTWALKER, "night_owl", 3);
        rank(without, Skill.FARMING, "harvest_festival", 2);
        ServerLevel level = helper.getLevel();
        long before = level.getDayTime();
        try {
            level.setDayTime(18_000L);
            helper.assertTrue(NightwalkerService.isNight(level), "setup: midnight");
            helper.assertTrue(ProcService.situational(with, Skill.FARMING) == 1.5,
                    "the night multiplier was " + ProcService.situational(with, Skill.FARMING));
            helper.assertTrue(ProcService.situational(without, Skill.FARMING) == 1.0, "the control got the night multiplier");
            helper.assertTrue(ProcService.situational(with, Skill.MINING) == 1.0, "another skill got the night multiplier");
            level.setDayTime(6_000L);
            helper.assertTrue(ProcService.situational(with, Skill.FARMING) == 1.0, "the multiplier applied at noon");
        } finally {
            level.setDayTime(before);
        }
        finish(helper, with, without);
    }

    // ---- Shore Supper ---------------------------------------------------------------------------

    private static List<ItemStack> rawCod() {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(Items.COD));
        return drops;
    }

    private static float cookingXp(ServerPlayer player) {
        ProficiencyAttachments.of(player);
        return ProficiencyAttachments.of(player).xp(Skill.COOKING);
    }

    private static void fish(ServerPlayer player) {
        FishingHook hook = new FishingHook(player, player.serverLevel(), 0, 0);
        net.minecraftforge.event.entity.player.ItemFishedEvent event = new net.minecraftforge.event.entity.player.ItemFishedEvent(rawCod(), 1, hook);
        GatheringEvents.onFished(event);
    }

    @GameTest(template = T, batch = "syn_shore_supper")
    public static void shoreSupperCooksEveryCatchByAFireAndTrainsCooking(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer plain = player(helper, 6.5, 2, 6.5);
        rank(with, Skill.FISHING, "fresh_catch", 3);
        rank(with, Skill.COOKING, "banquet", 3);
        rank(plain, Skill.FISHING, "fresh_catch", 3);
        ServerLevel level = helper.getLevel();
        BlockPos camp = new BlockPos(2, 2, 0);

        // No campfire: only Fresh Catch's own 60% at rank 3, and no Cooking XP at all.
        int cooked = 0;
        for (int i = 0; i < 30; i++) {
            List<ItemStack> drops = rawCod();
            if (TalentGatheringEvents.freshCatch(level, with, drops) && drops.get(0).is(Items.COOKED_COD)) {
                cooked++;
            }
        }
        helper.assertTrue(cooked < 30, "30 of 30 catches were cooked with no campfire (60% each)");
        fish(with);
        helper.assertTrue(cookingXp(with) == 0f, "Cooking XP without a campfire");

        helper.setBlock(camp, Blocks.CAMPFIRE);
        helper.assertTrue(net.minecraft.world.level.block.CampfireBlock.isLitCampfire(helper.getBlockState(camp)), "setup: lit campfire");
        for (int i = 0; i < 30; i++) {
            List<ItemStack> drops = rawCod();
            helper.assertTrue(TalentGatheringEvents.freshCatch(level, with, drops) && drops.get(0).is(Items.COOKED_COD),
                    "a catch by the lit campfire stayed raw (try " + i + ")");
        }
        fish(with);
        helper.assertTrue(cookingXp(with) > 0f, "no Cooking XP for a fish cooked by the campfire");

        // The control: Fresh Catch rank 0 has no luck even by the fire's light, and the lit fire is
        // not enough for a player without the synergy to cook every time.
        ServerPlayer none = player(helper, 1.5, 2, 0.5);
        List<ItemStack> raw = rawCod();
        helper.assertFalse(TalentGatheringEvents.freshCatch(level, none, raw) || !raw.get(0).is(Items.COD),
                "a player with no Fresh Catch cooked a fish");
        fish(none);
        helper.assertTrue(cookingXp(none) == 0f, "Cooking XP without the synergy");
        helper.setBlock(camp, Blocks.AIR);
        finish(helper, with, plain, none);
    }

    // ---- Fishing Trip ---------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_fishing_trip")
    public static void fishingTripMakesAFriendsNextTreasureHaulCertain(GameTestHelper helper) {
        ServerPlayer caster = player(helper, 0.5, 2, 0.5);
        ServerPlayer friend = player(helper, 5.5, 2, 0.5);
        ServerPlayer plain = player(helper, 3.5, 2, 0.5);
        ServerPlayer far = player(helper, 0.5, 2, 20.5);
        for (ServerPlayer angler : new ServerPlayer[] {friend, far}) {
            rank(angler, Skill.FISHING, "double_hook", 3);
            rank(angler, Skill.SOCIAL, "stay_a_while", 3);
        }
        rank(plain, Skill.FISHING, "double_hook", 3);
        rank(plain, Skill.SOCIAL, "stay_a_while", 2);

        ProcService.forceNext(caster, Skill.FISHING);
        helper.assertTrue(ProcService.fire(caster, Skill.FISHING), "setup: the forced Treasure Haul did not land");
        helper.assertFalse(ProcService.fire(plain, Skill.FISHING), "a friend without the synergy got a certain haul");
        helper.assertFalse(ProcService.fire(far, Skill.FISHING), "a friend 20 blocks away got a certain haul");
        helper.assertTrue(ProcService.fire(friend, Skill.FISHING), "the friend in range did not get a certain haul");
        helper.assertFalse(ProcService.fire(friend, Skill.FISHING), "the certainty lasted for more than one roll");
        // The friend's granted haul does not hand one back: the caster has no synergy and none is forced.
        helper.assertFalse(ProcService.fire(caster, Skill.FISHING), "the friend's granted haul passed certainty back");
        finish(helper, caster, friend, plain, far);
    }

    // ---- Sunday Roast ---------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_sunday_roast")
    public static void sundayRoastFeedsMoreAtHome(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        for (ServerPlayer other : List.copyOf(level.players())) {
            if (other.position().distanceTo(origin) < 64) {
                other.setPos(other.getX(), other.getY() + 400, other.getZ());
            }
        }
        BlockPos hearth = new BlockPos(1, 2, 1);
        helper.setBlock(hearth, HearthBlocks.HEARTH.get().defaultBlockState()
                .setValue(HearthBlock.STAGE, 1).setValue(HearthBlock.SMOULDERING, false));
        BlockPos at = helper.absolutePos(hearth);
        ServerPlayer with = player(helper, 2.5, 2, 2.5);
        ServerPlayer away = player(helper, 2.5, 2, 3.5);
        ServerPlayer plain = player(helper, 3.5, 2, 2.5);
        for (ServerPlayer cook : new ServerPlayer[] {with, away}) {
            rank(cook, Skill.COOKING, "family_recipe", 1);
            rank(cook, Skill.HOMEBUILDER, "warm_welcome", 2);
            rank(cook, Skill.FARMING, "green_thumb", 1);
        }
        for (ServerPlayer home : new ServerPlayer[] {with, plain}) {
            HearthAttachments.of(home).seenBy(at.asLong(), level.dimension().location().toString(), level.getGameTime());
        }
        for (ServerPlayer eater : new ServerPlayer[] {with, away, plain}) {
            eater.getFoodData().setFoodLevel(10);
            eater.getFoodData().setSaturation(0f);
        }

        TalentCraftingEvents.sundayRoast(plain);
        helper.assertTrue(plain.getFoodData().getFoodLevel() == 10, "a player without the synergy got the extra meal");
        TalentCraftingEvents.sundayRoast(away);
        helper.assertTrue(away.getFoodData().getFoodLevel() == 10, "the extra meal was served away from the Hearth");
        TalentCraftingEvents.sundayRoast(with);
        helper.assertTrue(with.getFoodData().getFoodLevel() == 12, "hunger should be 12, was " + with.getFoodData().getFoodLevel());
        helper.assertTrue(Math.abs(with.getFoodData().getSaturationLevel() - 2f) < 1e-4,
                "saturation should be 2, was " + with.getFoodData().getSaturationLevel());
        helper.setBlock(hearth, Blocks.AIR);
        finish(helper, with, away, plain);
    }
}
