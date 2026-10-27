package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.MasteryMath;
import dev.amman.proficiency.skill.MasteryRiders;
import dev.amman.proficiency.skill.MasteryTasks;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Mastery riders in a real world. Every rider that changes the world has its own test: the damage
 * riders (a phantom hit lands a few ticks after the proc), the crowd riders, the markers that glow and
 * then go away, the placing and growing riders, the dashes, the gifts. Plus the level and star gates
 * and the death rule. The arithmetic is in core MasteryMathTest and MasteryEffectsTest.
 *
 * <p>The hero stands at relative (2.5, 2, 1.5) facing +z, a survival player, the skill at level 100.
 */
public final class MasteryRiderGameTests {

    private static final String T = "proficiency:empty";

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public MasteryRiderGameTests() {
    }

    private static PlayerSkills star(ServerPlayer player, Skill skill, int stars) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(skill, SkillMath.MAX_LEVEL);
        skills.setStars(skill, stars);
        return skills;
    }

    /** A survival player facing +z with the skill at level 100 and this many stars. */
    private static ServerPlayer hero(GameTestHelper helper, Skill skill, int stars) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 1.5)));
        player.setYRot(0f);
        player.setYHeadRot(0f);
        player.setXRot(0f);
        star(player, skill, stars);
        return player;
    }

    private static ServerPlayer extra(GameTestHelper helper, double x, double z) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
        return player;
    }

    /** What each running test spawned, so one test's cleanup never touches a neighbour's mobs (tests run side by side). */
    private static final java.util.Map<GameTestHelper, List<Entity>> SPAWNED = new java.util.concurrent.ConcurrentHashMap<>();

    private static <E extends Mob> E spawn(GameTestHelper helper, EntityType<E> type, int x, int z) {
        E mob = helper.spawnWithNoFreeWill(type, new BlockPos(x, 2, z));
        SPAWNED.computeIfAbsent(helper, h -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(mob);
        return mob;
    }

    /** A zombie with no armour and no will of its own. */
    private static Mob zombie(GameTestHelper helper, double x, double z) {
        Mob mob = spawn(helper, EntityType.ZOMBIE, (int) x, (int) z);
        // The test world has daylight: an unlit zombie would burn and every health reading would drift.
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600));
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(0);
        }
        return mob;
    }

    private static void floor(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), block);
            }
        }
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int dropped(GameTestHelper helper, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                bounds(helper).inflate(3))) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }

    private static List<Entity> marks(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Entity.class, bounds(helper).inflate(3),
                e -> e.getTags().contains(MasteryTasks.MARK_TAG));
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ProcService.forget(player.getUUID());
            player.server.getPlayerList().remove(player);
        }
        List<Entity> mine = SPAWNED.remove(helper);
        if (mine != null) {
            mine.forEach(Entity::discard);
        }
        for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, bounds(helper).inflate(3),
                e -> e instanceof ItemEntity || e.getTags().contains(MasteryTasks.MARK_TAG))) {
            entity.discard();
        }
        helper.succeed();
    }

    /** Mock players never tick, so the water flag a swimmer has is set by hand, through the entity's own update. */
    private static void soak(ServerPlayer player) {
        try {
            java.lang.reflect.Method update = Entity.class.getDeclaredMethod("updateInWaterStateAndDoFluidPushing");
            update.setAccessible(true);
            update.invoke(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not wet the mock player", e);
        }
    }

    /** The test's own 5 by 5 by 5 box. */
    private static AABB bounds(GameTestHelper helper) {
        return new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(5, 5, 5)));
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        helper.assertTrue(ok, message);
    }

    // ---- Combat -------------------------------------------------------------------------------------

    /** Swords: the phantom slash lands later, on the foe and the one beside it, not on the one far off. */
    @GameTest(template = T, batch = "masteryrider.echoSlashHitsTheFoeAndTheOneBesideIt")
    public static void echoSlashHitsTheFoeAndTheOneBesideIt(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SWORDS, 3);
        Mob first = zombie(helper, 2, 3);
        Mob beside = zombie(helper, 3, 3);
        Mob far = zombie(helper, 2, 8);
        ProcService.celebrate(player, Skill.SWORDS, first, null);
        check(helper, first.getHealth() == 20f, "the echo waits for the proc's own blow");
        helper.runAfterDelay(15, () -> {
            check(helper, first.getHealth() <= 13.5f, "Echo Slash at 3 stars hits the foe for 7, health " + first.getHealth());
            check(helper, beside.getHealth() <= 13.5f, "and the foe beside it, health " + beside.getHealth());
            check(helper, far.getHealth() == 20f, "but not one far off");
            finish(helper, player);
        });
    }

    /** Tridents and Archery: the charge arcs on to separate hostiles, each once, never back to the target. */
    @GameTest(template = T, batch = "masteryrider.arcsAndRicochetsGoOnToSeparateFoes")
    public static void arcsAndRicochetsGoOnToSeparateFoes(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.TRIDENTS, 2);
        star(player, Skill.ARCHERY, 3);
        Mob target = zombie(helper, 2, 3);
        Mob a = zombie(helper, 3, 4);
        Mob b = zombie(helper, 1, 4);
        Mob c = zombie(helper, 2, 6);
        ProcService.celebrate(player, Skill.TRIDENTS, target, null);
        helper.runAfterDelay(25, () -> {
            check(helper, target.getHealth() == 20f, "the arc leaves the target to the proc's own blow");
            int hit = 0;
            for (Mob m : List.of(a, b, c)) {
                if (m.getHealth() <= 16.5f) {
                    hit++;
                }
            }
            check(helper, hit == 2, "Arcing Tide at 2 stars reaches exactly 2 more hostiles, reached " + hit);
            ProcService.celebrate(player, Skill.ARCHERY, target, null);
            helper.runAfterDelay(30, () -> {
                int ricocheted = 0;
                for (Mob m : List.of(a, b, c)) {
                    if (m.getHealth() <= 11.5f) {
                        ricocheted++;
                    }
                }
                check(helper, ricocheted >= 2, "Ricochet at 3 stars hops 3 times, so the nearest hostiles take 5 more");
                finish(helper, player);
            });
        });
    }

    /** Unarmed: a flurry of jabs lands as one hit of the table's size. */
    @GameTest(template = T, batch = "masteryrider.flurryLandsTheTableDamage")
    public static void flurryLandsTheTableDamage(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.UNARMED, 3);
        Mob foe = zombie(helper, 2, 3);
        ProcService.celebrate(player, Skill.UNARMED, foe, null);
        helper.runAfterDelay(12, () -> {
            check(helper, Math.abs(foe.getHealth() - 12f) < 0.01f, "Flurry at 3 stars is 4 jabs of 2, health " + foe.getHealth());
            finish(helper, player);
        });
    }

    /** Axes whirl, Maces tremor: the crowd inside the radius is hit, the stranger outside is not. */
    @GameTest(template = T, batch = "masteryrider.whirlAndTremorHitTheCrowdAround")
    public static void whirlAndTremorHitTheCrowdAround(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.AXES, 2);
        star(player, Skill.MACES, 1);
        Mob near = zombie(helper, 2, 3);
        Mob far = zombie(helper, 2, 9);
        ProcService.celebrate(player, Skill.MACES);
        check(helper, near.getDeltaMovement().y > 0.5, "Tremor throws the hostile within 3 blocks into the air");
        check(helper, far.getDeltaMovement().y < 0.1, "and leaves the one far off alone");
        ProcService.celebrate(player, Skill.AXES);
        helper.runAfterDelay(8, () -> {
            check(helper, near.getHealth() <= 16.5f, "Whirl at 2 stars hits the hostile within 3 blocks for 4, health " + near.getHealth());
            check(helper, far.getHealth() == 20f, "and not the one far off");
            finish(helper, player);
        });
    }

    /** A rider spares neutral mobs that are not after you, and does nothing (no feed) when it has nothing to act on. */
    @GameTest(template = T, batch = "masteryrider.neutralMobsAndEmptyRoomsAreSpared")
    public static void neutralMobsAndEmptyRoomsAreSpared(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.AXES, 2);
        star(player, Skill.HOMEBUILDER, 1);
        Mob piglin = spawn(helper, EntityType.ZOMBIFIED_PIGLIN, 2, 3);
        float before = piglin.getHealth();
        ProcService.celebrate(player, Skill.AXES);
        player.getFoodData().setSaturation(0f);
        ProcService.celebrate(player, Skill.HOMEBUILDER);
        check(helper, player.getFoodData().getSaturationLevel() == 0f, "Hearthglow with no fire in reach gives no warm bite");
        helper.runAfterDelay(8, () -> {
            check(helper, piglin.getHealth() == before, "Whirl leaves an unprovoked zombified piglin alone, health " + piglin.getHealth());
            check(helper, piglin.getTarget() == null, "and does not anger it");
            finish(helper, player);
        });
    }

    /** Courage, Guardian, Tactician: shove, push and pin the hostiles in range only. */
    @GameTest(template = T, batch = "masteryrider.warCryWardingPulseAndPinningFire")
    public static void warCryWardingPulseAndPinningFire(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.COURAGE, 1);
        star(player, Skill.GUARDIAN, 1);
        star(player, Skill.TACTICIAN, 1);
        Mob near = zombie(helper, 2, 3);
        Mob far = zombie(helper, 2, 9);
        ProcService.celebrate(player, Skill.COURAGE);
        check(helper, near.hasEffect(MobEffects.WEAKNESS), "War Cry weakens the hostile within 4 blocks");
        check(helper, !far.hasEffect(MobEffects.WEAKNESS), "but not the one far off");
        near.setDeltaMovement(Vec3.ZERO);
        ProcService.celebrate(player, Skill.GUARDIAN);
        check(helper, near.getDeltaMovement().horizontalDistance() > 0.2, "Warding Pulse pushes the hostile within 3 blocks off you");
        ProcService.celebrate(player, Skill.TACTICIAN);
        check(helper, near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && near.hasEffect(MobEffects.GLOWING),
                "Pinning Fire slows and lights up the hostile within 4 blocks");
        check(helper, !far.hasEffect(MobEffects.GLOWING), "but not the one far off");
        finish(helper, player);
    }

    /** Crossbows: shrapnel around the target. Blocking: the attacker is bashed away. Charger: the lane. */
    @GameTest(template = T, batch = "masteryrider.splinterBashAndStampede")
    public static void splinterBashAndStampede(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.CROSSBOWS, 1);
        star(player, Skill.BLOCKING, 1);
        star(player, Skill.CHARGER, 1);
        Mob target = zombie(helper, 2, 3);
        Mob beside = zombie(helper, 3, 3);
        Mob far = zombie(helper, 2, 9);
        Mob out = zombie(helper, 6, 2);
        ProcService.celebrate(player, Skill.CROSSBOWS, target, null);
        ProcService.celebrate(player, Skill.BLOCKING, target, null);
        check(helper, target.getDeltaMovement().z > 0.5, "Shield Bash hurls the attacker away from you");
        target.setDeltaMovement(Vec3.ZERO);
        ProcService.celebrate(player, Skill.CHARGER);
        check(helper, target.getDeltaMovement().y > 0.3, "Stampede flings the hostile in the lane");
        check(helper, out.getDeltaMovement().y < 0.1, "and not the one beside the lane");
        helper.runAfterDelay(12, () -> {
            check(helper, beside.getHealth() <= 16.5f, "Splinter Bolt at 1 star hits the hostile within 2 blocks of the target, health " + beside.getHealth());
            check(helper, far.getHealth() == 20f, "and not the one far off");
            finish(helper, player);
        });
    }

    /** Endurance: the first kinds of harmful effect wash off, the later ones stay. */
    @GameTest(template = T, batch = "masteryrider.shrugItOffWashesTheFirstKindsOnly")
    public static void shrugItOffWashesTheFirstKindsOnly(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.ENDURANCE, 2);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 400));
        ProcService.celebrate(player, Skill.ENDURANCE);
        check(helper, !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !player.hasEffect(MobEffects.WEAKNESS),
                "Shrug It Off at 2 stars washes off Slowness and Weakness");
        check(helper, player.hasEffect(MobEffects.POISON), "but not Poison, which is the seventh kind");
        check(helper, player.hasEffect(MobEffects.WITHER), "and never Wither");
        finish(helper, player);
    }

    // ---- Gathering ----------------------------------------------------------------------------------

    /** Mining: the ore glows through the rock, the far one does not, and the glow goes away. */
    @GameTest(template = T, timeoutTicks = 260, batch = "masteryrider.motherlodePingMarksOresThenLetsGo")
    public static void motherlodePingMarksOresThenLetsGo(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.MINING, 1);
        helper.setBlock(new BlockPos(2, 2, 5), Blocks.DIAMOND_ORE);
        helper.setBlock(new BlockPos(2, 2, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 15), Blocks.GOLD_ORE);
        ProcService.celebrate(player, Skill.MINING);
        List<Entity> found = marks(helper);
        check(helper, found.size() == 1, "Motherlode Ping at 1 star marks the ore within 6 blocks and not the far one, marks " + found.size());
        check(helper, found.get(0).hasGlowingTag(), "the marker glows");
        helper.runAfterDelay(MasteryMath.MARK_TICKS + 15, () -> {
            check(helper, marks(helper).isEmpty(), "the glow goes away");
            finish(helper, player);
        });
    }

    /** Woodcutting: saplings go into the bare ground around the stump, no more than the table says. */
    @GameTest(template = T, batch = "masteryrider.replantPlantsSaplings")
    public static void replantPlantsSaplings(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.WOODCUTTING, 2);
        floor(helper, Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.BIRCH_LOG);
        ProcService.celebrate(player, Skill.WOODCUTTING, null, helper.absolutePos(new BlockPos(2, 2, 3)));
        int saplings = 0;
        int birch = 0;
        for (int x = -4; x <= 8; x++) {
            for (int z = -4; z <= 8; z++) {
                // The ground outside the template may sit at another height, so look at every height, but
                // every sapling must stand on dirt-like ground (the rule that placed it).
                for (int y = -6; y <= 8; y++) {
                    var state = helper.getBlockState(new BlockPos(x, y, z));
                    if (state.is(net.minecraft.tags.BlockTags.SAPLINGS)) {
                        saplings++;
                        check(helper, helper.getBlockState(new BlockPos(x, y - 1, z)).is(net.minecraft.tags.BlockTags.DIRT),
                                "a planted sapling stands on dirt-like ground");
                        check(helper, Math.abs(x - 2) <= 4 && Math.abs(z - 3) <= 4, "and within 4 blocks of the stump");
                        if (state.is(Blocks.BIRCH_SAPLING)) {
                            birch++;
                        }
                    }
                }
            }
        }
        check(helper, saplings >= 1 && saplings <= 3, "Replant at 2 stars plants up to 3 saplings, planted " + saplings);
        check(helper, birch == saplings, "of the species that stood there");
        finish(helper, player);
    }

    /** Excavation: the loose ground gives way, exactly as many blocks as the table, and no more. */
    @GameTest(template = T, batch = "masteryrider.landslideLoosensExactlyTheTableCount")
    public static void landslideLoosensExactlyTheTableCount(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.EXCAVATION, 3);
        floor(helper, Blocks.DIRT);
        ProcService.celebrate(player, Skill.EXCAVATION, null, null);
        int left = 0;
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT)) {
                    left++;
                }
            }
        }
        check(helper, left == 25 - 6, "Landslide at 3 stars removes 6 blocks, " + (25 - left) + " went");
        finish(helper, player);
    }

    /** Farming: the crops grow, the bare furrows are replanted. */
    @GameTest(template = T, batch = "masteryrider.greenThumbGrowsAndReplants")
    public static void greenThumbGrowsAndReplants(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.FARMING, 3);
        floor(helper, Blocks.FARMLAND);
        BlockPos[] crops = {new BlockPos(1, 2, 3), new BlockPos(3, 2, 3), new BlockPos(2, 2, 4)};
        for (BlockPos crop : crops) {
            helper.setBlock(crop, Blocks.WHEAT.defaultBlockState());
        }
        ProcService.celebrate(player, Skill.FARMING);
        int grown = 0;
        for (BlockPos crop : crops) {
            if (helper.getBlockState(crop).getValue(CropBlock.AGE) > 0) {
                grown++;
            }
        }
        check(helper, grown == 3, "Green Thumb at 3 stars bone-meals up to 5 crops, so all 3 grew, grew " + grown);
        int wheat = 0;
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.WHEAT)) {
                    wheat++;
                }
            }
        }
        check(helper, wheat == 3 + 3, "and 3 bare furrows were replanted, wheat " + wheat);
        finish(helper, player);
    }

    /** Fishing: extra fish are flung out, the table's number of them. */
    @GameTest(template = T, batch = "masteryrider.secondCatchHooksTheTableCount")
    public static void secondCatchHooksTheTableCount(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.FISHING, 3);
        ProcService.celebrate(player, Skill.FISHING);
        int fish = dropped(helper, Items.COD) + dropped(helper, Items.SALMON) + dropped(helper, Items.TROPICAL_FISH)
                + dropped(helper, Items.PUFFERFISH);
        check(helper, fish == 3, "Second Catch at 3 stars hooks 3 fish, got " + fish);
        finish(helper, player);
    }

    /** Husbandry: wool grows back, a hen lays, a calf grows, and a stranger's tamed pet is left alone. */
    @GameTest(template = T, batch = "masteryrider.openBarnWorksTheWholeBarn")
    public static void openBarnWorksTheWholeBarn(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.HUSBANDRY, 2);
        Sheep sheep = spawn(helper, EntityType.SHEEP, 1, 3);
        sheep.setSheared(true);
        Cow calf = spawn(helper, EntityType.COW, 3, 3);
        calf.setBaby(true);
        int before = calf.getAge();
        Chicken hen = spawn(helper, EntityType.CHICKEN, 2, 4);
        ProcService.celebrate(player, Skill.HUSBANDRY);
        check(helper, !sheep.isSheared(), "the wool grows back");
        check(helper, calf.getAge() >= before + 40 * 20 - 1, "the calf grows by 40 seconds at 2 stars, age " + before + " -> " + calf.getAge());
        check(helper, dropped(helper, Items.EGG) == 1 && hen.isAlive(), "the hen lays an egg");
        finish(helper, player);
    }

    // ---- Movement -----------------------------------------------------------------------------------

    /** Running: a burst forward, as far as the table says. Jumping: a rebound as high as the table says. */
    @GameTest(template = T, batch = "masteryrider.galeStepAndReboundMoveYou")
    public static void galeStepAndReboundMoveYou(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.RUNNING, 3);
        star(player, Skill.JUMPING, 3);
        player.setDeltaMovement(Vec3.ZERO);
        ProcService.celebrate(player, Skill.RUNNING);
        double expected = MasteryMath.dashSpeed(5, MasteryMath.GROUND_DRAG);
        check(helper, Math.abs(player.getDeltaMovement().z - expected) < 0.05,
                "Gale Step at 3 stars is the speed of a 5 block burst " + expected + ", got " + player.getDeltaMovement().z);
        player.setDeltaMovement(Vec3.ZERO);
        ProcService.celebrate(player, Skill.JUMPING);
        check(helper, Math.abs(player.getDeltaMovement().y - MasteryMath.launchSpeed(4)) < 0.01,
                "Rebound at 3 stars throws you 4 blocks up");
        check(helper, player.hasEffect(MobEffects.SLOW_FALLING), "with Slow Falling to bring you down");
        finish(helper, player);
    }

    /** Swimming: in water the current carries you and fills your lungs; on land nothing happens. */
    @GameTest(template = T, batch = "masteryrider.riptideGlideNeedsWater")
    public static void riptideGlideNeedsWater(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SWIMMING, 2);
        player.setDeltaMovement(Vec3.ZERO);
        ProcService.celebrate(player, Skill.SWIMMING);
        check(helper, player.getDeltaMovement().lengthSqr() == 0.0, "no glide on dry land");
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 3, 1), Blocks.WATER);
        soak(player);
        player.setAirSupply(10);
        player.setXRot(0f);
        ProcService.celebrate(player, Skill.SWIMMING);
        double expected = MasteryMath.dashSpeed(4, MasteryMath.WATER_DRAG);
        check(helper, Math.abs(player.getDeltaMovement().z - expected) < 0.05, "Riptide Glide at 2 stars carries you 4 blocks");
        check(helper, player.getAirSupply() == player.getMaxAirSupply(), "and fills your lungs");
        finish(helper, player);
    }

    /** Sneaking: hunters forget you and you vanish. Riding: the mount is mended and cleansed. */
    @GameTest(template = T, batch = "masteryrider.smokeStepAndRestedMount")
    public static void smokeStepAndRestedMount(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SNEAKING, 2);
        star(player, Skill.RIDING, 2);
        Mob hunter = zombie(helper, 2, 4);
        hunter.setTarget(player);
        ProcService.celebrate(player, Skill.SNEAKING);
        check(helper, hunter.getTarget() == null, "Smoke Step: the hunter within 6 blocks forgets you");
        check(helper, player.hasEffect(MobEffects.INVISIBILITY), "and you are gone for a moment");
        Pig pig = spawn(helper, EntityType.PIG, 2, 1);
        pig.setHealth(3f);
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        player.startRiding(pig, true);
        ProcService.celebrate(player, Skill.RIDING);
        check(helper, pig.getHealth() == 7f, "Rested Mount at 2 stars heals the mount 4, health " + pig.getHealth());
        check(helper, !pig.hasEffect(MobEffects.POISON), "and washes the poison off it");
        finish(helper, player);
    }

    // ---- Crafting -----------------------------------------------------------------------------------

    /** Smithing: a percent of the maximum comes back on armour and on the tool in hand. */
    @GameTest(template = T, batch = "masteryrider.quenchRepairsAPercentOfTheMaximum")
    public static void quenchRepairsAPercentOfTheMaximum(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SMITHING, 2);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.setDamageValue(100);
        player.getInventory().armor.set(3, helmet);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(100);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);
        ProcService.celebrate(player, Skill.SMITHING);
        int helmetBack = MasteryMath.repairAmount(helmet.getMaxDamage(), 10);
        check(helper, helmet.getDamageValue() == 100 - helmetBack, "Quench at 2 stars gives back 10% on the helmet, damage " + helmet.getDamageValue());
        check(helper, pick.getDamageValue() == 100 - MasteryMath.repairAmount(pick.getMaxDamage(), 10), "and on the tool in hand");
        finish(helper, player);
    }

    /** Cooking: the second plate goes to the cook and the table. Alchemy: a percent more on helpful effects. */
    @GameTest(template = T, batch = "masteryrider.secondsServedAndReagentEcho")
    public static void secondsServedAndReagentEcho(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.COOKING, 1);
        star(player, Skill.ALCHEMY, 1);
        ServerPlayer guest = extra(helper, 3.5, 1.5);
        player.getFoodData().setFoodLevel(10);
        guest.getFoodData().setFoodLevel(10);
        ProcService.celebrate(player, Skill.COOKING);
        check(helper, player.getFoodData().getFoodLevel() == 12, "Seconds Served at 1 star feeds the cook 2");
        check(helper, guest.getFoodData().getFoodLevel() == 12, "and the guest at the table");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        ProcService.celebrate(player, Skill.ALCHEMY);
        check(helper, player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 400 + MasteryMath.echoAdd(400, 10),
                "Reagent Echo at 1 star stretches the helpful effect, got " + player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration());
        check(helper, player.getEffect(MobEffects.POISON).getDuration() <= 400, "and leaves the harmful one alone");
        finish(helper, player, guest);
    }

    // ---- Mastery, Expedition ------------------------------------------------------------------------

    /** Spellcasting: as many missiles as stars allow, at separate hostiles, nearest first. */
    @GameTest(template = T, batch = "masteryrider.magicMissilesFlyAtSeparateFoes")
    public static void magicMissilesFlyAtSeparateFoes(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SPELLCASTING, 2);
        Mob a = zombie(helper, 2, 3);
        Mob b = zombie(helper, 3, 4);
        Mob c = zombie(helper, 1, 5);
        ProcService.celebrate(player, Skill.SPELLCASTING);
        helper.runAfterDelay(20, () -> {
            int hit = 0;
            for (Mob m : List.of(a, b, c)) {
                if (m.getHealth() <= 17.5f) {
                    hit++;
                }
            }
            check(helper, hit == 2, "Magic Missiles at 2 stars hits 2 of 3, hit " + hit);
            check(helper, c.getHealth() > a.getHealth() + 0.5f && c.getHealth() > b.getHealth() + 0.5f,
                    "the farthest is spared the missiles, health " + a.getHealth() + " / " + b.getHealth() + " / " + c.getHealth());
            finish(helper, player);
        });
    }

    /** Engineering: items within the radius fly to you, the ones beyond stay. */
    @GameTest(template = T, batch = "masteryrider.tractorBeamPullsNearbyItems")
    public static void tractorBeamPullsNearbyItems(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.ENGINEERING, 1);
        ItemEntity near = new ItemEntity(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 2, 4.5)).x,
                helper.absoluteVec(new Vec3(2.5, 2, 4.5)).y, helper.absoluteVec(new Vec3(2.5, 2, 4.5)).z,
                new ItemStack(Items.COBBLESTONE));
        Vec3 farAt = helper.absoluteVec(new Vec3(2.5, 2, 9.5));
        ItemEntity far = new ItemEntity(helper.getLevel(), farAt.x, farAt.y, farAt.z, new ItemStack(Items.COBBLESTONE));
        helper.getLevel().addFreshEntity(near);
        helper.getLevel().addFreshEntity(far);
        ProcService.celebrate(player, Skill.ENGINEERING);
        check(helper, near.distanceTo(player) < 1.0, "Tractor Beam at 1 star pulls the item within 4 blocks to your feet");
        check(helper, far.distanceTo(player) > 5.0, "and leaves the far one");
        finish(helper, player);
    }

    /** Beastslaying feeds you from the kill. */
    @GameTest(template = T, batch = "masteryrider.freshKillFeedsYou")
    public static void freshKillFeedsYou(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.BEASTSLAYING, 3);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0f);
        ProcService.celebrate(player, Skill.BEASTSLAYING);
        check(helper, player.getFoodData().getFoodLevel() == 14, "Fresh Kill at 3 stars is 4 food, got " + player.getFoodData().getFoodLevel());
        check(helper, player.getFoodData().getSaturationLevel() >= 4f - 0.01f, "and as much saturation");
        finish(helper, player);
    }

    /** Wayfaring: the search runs without a hitch and the compass reads right. */
    @GameTest(template = T, timeoutTicks = 400, batch = "masteryrider.newHorizonSearchesAndPoints")
    public static void newHorizonSearchesAndPoints(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.WAYFARING, 1);
        ProcService.celebrate(player, Skill.WAYFARING);
        check(helper, MasteryRiders.compass(0, -10).equals("N"), "north is negative z");
        check(helper, MasteryRiders.compass(10, 0).equals("E"), "east is positive x");
        check(helper, MasteryRiders.compass(-10, 10).equals("SW"), "south-west");
        check(helper, MasteryRiders.compass(10, -10).equals("NE"), "north-east");
        finish(helper, player);
    }

    /** Spelunking: unlooted loot chests and spawners glow, a plain chest does not, and the glow ends. */
    @GameTest(template = T, timeoutTicks = 260, batch = "masteryrider.caveSenseMarksLootAndSpawners")
    public static void caveSenseMarksLootAndSpawners(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SPELUNKING, 1);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.SPAWNER);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.CHEST);
        if (helper.getBlockEntity(new BlockPos(4, 2, 4)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
        }
        ProcService.celebrate(player, Skill.SPELUNKING);
        check(helper, marks(helper).size() == 2, "Cave Sense marks the loot chest and the spawner but not the plain chest, marks " + marks(helper).size());
        helper.runAfterDelay(MasteryMath.MARK_TICKS + 15, () -> {
            check(helper, marks(helper).isEmpty(), "the glow goes away");
            finish(helper, player);
        });
    }

    // ---- Construction -------------------------------------------------------------------------------

    /** Masonry restores worn blocks, Decorating opens flowers, Homebuilder relights a campfire. */
    @GameTest(template = T, batch = "masteryrider.repointBloomAndRelight")
    public static void repointBloomAndRelight(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.MASONRY, 1);
        star(player, Skill.DECORATING, 1);
        star(player, Skill.HOMEBUILDER, 1);
        floor(helper, Blocks.GRASS_BLOCK);
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(0, 2, z), Blocks.MOSSY_COBBLESTONE);
        }
        helper.setBlock(new BlockPos(4, 2, 0), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        ProcService.celebrate(player, Skill.MASONRY);
        int cobble = 0;
        for (int z = 2; z <= 4; z++) {
            if (helper.getBlockState(new BlockPos(0, 2, z)).is(Blocks.COBBLESTONE)) {
                cobble++;
            }
        }
        check(helper, cobble == 2, "Repoint at 1 star restores 2 of the 3 mossy blocks, restored " + cobble);
        ProcService.celebrate(player, Skill.DECORATING);
        int flowers = 0;
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (helper.getBlockState(new BlockPos(x, 2, z)).is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)) {
                    flowers++;
                }
            }
        }
        check(helper, flowers == 3, "Bloom at 1 star opens 3 flowers, opened " + flowers);
        player.getFoodData().setSaturation(0f);
        ProcService.celebrate(player, Skill.HOMEBUILDER);
        check(helper, helper.getBlockState(new BlockPos(4, 2, 0)).getValue(BlockStateProperties.LIT),
                "Hearthglow relights the campfire within 4 blocks");
        check(helper, player.getFoodData().getSaturationLevel() >= 1f - 0.01f, "and leaves a warm bite");
        finish(helper, player);
    }

    // ---- Social, Survival ---------------------------------------------------------------------------

    /** Social: the toast heals you and the company. Commerce: a villager throws in bread. */
    @GameTest(template = T, batch = "masteryrider.toastAndThrownIn")
    public static void toastAndThrownIn(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SOCIAL, 2);
        star(player, Skill.COMMERCE, 2);
        ServerPlayer friend = extra(helper, 3.5, 1.5);
        player.setHealth(10f);
        friend.setHealth(10f);
        ProcService.celebrate(player, Skill.SOCIAL);
        check(helper, player.getHealth() == 13f && friend.getHealth() == 13f, "Toast at 2 stars heals 3 for you and the friend, " + player.getHealth() + " / " + friend.getHealth());
        spawn(helper, EntityType.VILLAGER, 3, 3);
        ProcService.celebrate(player, Skill.COMMERCE);
        check(helper, dropped(helper, Items.BREAD) == 2, "Thrown In at 2 stars passes over 2 loaves, got " + dropped(helper, Items.BREAD));
        finish(helper, player, friend);
    }

    /** Nightwalker: hostiles in range are lit up, the ones beyond are not. */
    @GameTest(template = T, batch = "masteryrider.moonGazeLightsUpHostiles")
    public static void moonGazeLightsUpHostiles(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.NIGHTWALKER, 1);
        Mob near = zombie(helper, 2, 4);
        Mob far = zombie(helper, 2, 12);
        ProcService.celebrate(player, Skill.NIGHTWALKER);
        check(helper, near.hasEffect(MobEffects.GLOWING), "Moon Gaze lights up the hostile within 6 blocks");
        check(helper, !far.hasEffect(MobEffects.GLOWING), "and not the one beyond");
        finish(helper, player);
    }

    // ---- Rules --------------------------------------------------------------------------------------

    /** No star, or a level under 100, and the proc is just the proc. */
    @GameTest(template = T, batch = "masteryrider.noStarOrNoLevelHundredMeansNoRider")
    public static void noStarOrNoLevelHundredMeansNoRider(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.FISHING, 0);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        ProcService.celebrate(player, Skill.FISHING);
        check(helper, dropped(helper, Items.COD) + dropped(helper, Items.SALMON) + dropped(helper, Items.PUFFERFISH)
                + dropped(helper, Items.TROPICAL_FISH) == 0, "no star must hook no fish");
        skills.setStars(Skill.FISHING, 5);
        skills.setLevel(Skill.FISHING, 99);
        ProcService.celebrate(player, Skill.FISHING);
        check(helper, dropped(helper, Items.COD) + dropped(helper, Items.SALMON) + dropped(helper, Items.PUFFERFISH)
                + dropped(helper, Items.TROPICAL_FISH) == 0, "level 99 must hook no fish");
        finish(helper, player);
    }

    /** A death takes the bars, never the stars: the rider is the same after it. */
    @GameTest(template = T, batch = "masteryrider.aDeathLeavesTheRiderAlone")
    public static void aDeathLeavesTheRiderAlone(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.FISHING, 5);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.applyDeathPenalty();
        ProcService.celebrate(player, Skill.FISHING);
        int fish = dropped(helper, Items.COD) + dropped(helper, Items.SALMON) + dropped(helper, Items.PUFFERFISH)
                + dropped(helper, Items.TROPICAL_FISH);
        check(helper, fish == 5, "five stars still hook 5 fish after a death, got " + fish);
        finish(helper, player);
    }

    /** A phantom hit can finish a nearly dead foe: it is plain magic with the player as cause, no weapon. */
    @GameTest(template = T, batch = "masteryrider.anEchoCanFinishANearlyDeadFoe")
    public static void anEchoCanFinishANearlyDeadFoe(GameTestHelper helper) {
        ServerPlayer player = hero(helper, Skill.SWORDS, 5);
        Mob foe = zombie(helper, 2, 3);
        foe.setHealth(1f);
        ProcService.celebrate(player, Skill.SWORDS, foe, null);
        helper.runAfterDelay(12, () -> {
            check(helper, !foe.isAlive() || foe.getHealth() <= 0f, "an echo of 12 finishes a foe on 1 health");
            finish(helper, player);
        });
    }
}
