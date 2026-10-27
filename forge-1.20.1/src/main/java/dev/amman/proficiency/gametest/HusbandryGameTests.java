package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.HusbandryEvents;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.HusbandryMath;
import dev.amman.proficiency.skill.HusbandryMath.Job;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import dev.amman.proficiency.Proficiency;

import java.util.ArrayList;
import java.util.List;

/**
 * Husbandry in a real world with mock players: every XP source through the real click or event
 * (breeding, taming, shearing, milking, feeding a baby, honey; the armadillo is 1.21 only), every
 * anti-farm guard (a dispenser, a creative player, the pair cooldown, a milk cooldown, a crowded
 * pen, the spot rule, the taming cap, a coat that had not regrown), Twins, the passive and the
 * tree's mechanics. The arithmetic is in HusbandryMathTest.
 *
 * <p>Each test is its own batch, and every player a test makes leaves the server at the end.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryGameTests {

    /** The mod's own empty template; Forge adds the "proficiency:" namespace itself. */
    private static final String T = "empty";

    private HusbandryGameTests() {
    }

    private static final class Cast {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<BlockPos> hearths = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
                if (other.position().distanceTo(origin) < 96) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            clear();
        }

        /** Leftover animals and drops outside the template would count as pen or loot. */
        void clear() {
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            AABB box = new AABB(origin, origin).inflate(64);
            for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class, box)) {
                mob.discard();
            }
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
                item.discard();
            }
        }

        ServerPlayer player(double x, double z) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            player.setYRot(0f);
            clearSpawnProtection(player);
            HusbandryEvents.forget(player.getUUID());
            players.add(player);
            return player;
        }

        void done() {
            for (ServerPlayer player : players) {
                ProcService.forget(player.getUUID());
                HusbandryEvents.forget(player.getUUID());
                player.server.getPlayerList().remove(player);
            }
            for (BlockPos hearth : hearths) {
                helper.getLevel().setBlock(hearth, Blocks.AIR.defaultBlockState(), 3);
                HearthIndexData.forget(helper.getLevel(), hearth);
            }
            clear();
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

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, double x, double z) {
        return helper.spawnWithNoFreeWill(type, new BlockPos((int) x, 2, (int) z));
    }

    private static float logged(ServerPlayer player, String source) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.HUSBANDRY.ordinal() && source.equals(entry.source())) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static PlayerSkills skills(ServerPlayer player) {
        return ProficiencyAttachments.of(player);
    }

    private static void rank(ServerPlayer player, Skill skill, String talent, int rank) {
        skills(player).setRank(Talents.get(skill, talent), rank);
    }

    /** The click the loader's bridge would have posted for a right click on an animal. */
    private static void click(ServerPlayer player, Entity target, InteractionHand hand) {
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, hand, target));
    }

    /** A right click, whole: the shared event, then what the server does with the packet (the held item first, then the animal). */
    private static void use(ServerPlayer player, Animal animal, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        click(player, animal, InteractionHand.MAIN_HAND);
        player.interactOn(animal, InteractionHand.MAIN_HAND);
    }

    private static void later(GameTestHelper helper, int ticks, Runnable run) {
        helper.runAfterDelay(ticks, run);
    }

    private static void breed(ServerPlayer player, Animal mother, Animal father) {
        mother.setInLove(player);
        father.setInLove(player);
        mother.spawnChildFromBreeding(player.serverLevel(), father);
    }

    private static int babies(GameTestHelper helper, Class<? extends Animal> type) {
        Vec3 origin = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        return helper.getLevel().getEntitiesOfClass(type, new AABB(origin, origin).inflate(12), Animal::isBaby).size();
    }

    // ---- XP source: breeding --------------------------------------------------------------------

    /** Breeding two cows pays; the same pair again at once, a dispenser's breeding and a creative player pay nothing. */
    @GameTest(template = T, batch = "husbandry_breed")
    public static void breedingPaysOncePerPairAndNeedsAPlayer(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Cow a = spawn(helper, EntityType.COW, 1, 3);
        Cow b = spawn(helper, EntityType.COW, 2, 3);
        breed(player, a, b);
        float first = logged(player, HusbandryEvents.SOURCE_BREED);
        helper.assertTrue(first > 0f, "breeding two cows paid no Husbandry XP");

        breed(player, a, b);
        helper.assertTrue(logged(player, HusbandryEvents.SOURCE_BREED) == first, "the same pair paid twice inside 5 minutes");

        Cow c = spawn(helper, EntityType.COW, 3, 3);
        Cow d = spawn(helper, EntityType.COW, 4, 3);
        c.setInLove(null);
        d.setInLove(null);
        c.spawnChildFromBreeding(player.serverLevel(), d);
        helper.assertTrue(logged(player, HusbandryEvents.SOURCE_BREED) == first, "a breeding with no player paid");

        player.setGameMode(GameType.CREATIVE);
        Cow e = spawn(helper, EntityType.COW, 1, 4);
        Cow f = spawn(helper, EntityType.COW, 2, 4);
        breed(player, e, f);
        helper.assertTrue(logged(player, HusbandryEvents.SOURCE_BREED) == first, "a creative player earned Husbandry");
        cast.done();
    }

    /** A crowded pen pays less per job, and the spot rule stops one spot at 12 payouts. */
    @GameTest(template = T, batch = "husbandry_spot")
    public static void aCrowdedPenAndASpotPayLess(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Cow lone = spawn(helper, EntityType.COW, 2, 2);
        float alone = HusbandryEvents.pay(player, Job.BREED, lone, null, HusbandryEvents.SOURCE_BREED);
        helper.assertTrue(alone > 0f, "a lone breeding paid nothing");
        for (int i = 0; i < 40; i++) {
            spawn(helper, EntityType.COW, 1 + i % 4, 1 + i / 10);
        }
        ServerPlayer other = cast.player(0.5, 1.5);
        float crowded = HusbandryEvents.pay(other, Job.BREED, lone, null, HusbandryEvents.SOURCE_BREED);
        helper.assertTrue(crowded > 0f && crowded < alone * 0.5f,
                "a pen of 40 paid " + crowded + " against " + alone + " alone");
        cast.clear();

        ServerPlayer third = cast.player(0.5, 2.5);
        Cow single = spawn(helper, EntityType.COW, 3, 3);
        int paid = 0;
        for (int i = 0; i < HusbandryMath.SPOT_LIMIT + 6; i++) {
            if (HusbandryEvents.pay(third, Job.FEED, single, null, HusbandryEvents.SOURCE_FEED) > 0f) {
                paid++;
            }
        }
        helper.assertTrue(paid == HusbandryMath.SPOT_LIMIT, "one spot paid " + paid + " times, not "
                + HusbandryMath.SPOT_LIMIT);
        cast.done();
    }

    // ---- Twins and the passive ----------------------------------------------------------------------

    /** Twins gives a second baby; without the proc there is one. */
    @GameTest(template = T, batch = "husbandry_twins")
    public static void twinsGivesASecondBaby(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Cow a = spawn(helper, EntityType.COW, 1, 3);
        Cow b = spawn(helper, EntityType.COW, 2, 3);
        breed(player, a, b);
        later(helper, 3, () -> {
            helper.assertTrue(babies(helper, Cow.class) == 1, "an ordinary breeding gave " + babies(helper, Cow.class) + " babies");
            Cow c = spawn(helper, EntityType.COW, 3, 3);
            Cow d = spawn(helper, EntityType.COW, 4, 3);
            ProcService.forceNext(player, Skill.HUSBANDRY);
            breed(player, c, d);
            later(helper, 3, () -> {
                helper.assertTrue(babies(helper, Cow.class) == 3,
                        "Twins should leave 1 + 2 babies, found " + babies(helper, Cow.class));
                cast.done();
            });
        });
    }

    /** The passive: a bred baby of a level-100 player starts at 2/3 of the wait, Herd Master halves it again. */
    @GameTest(template = T, batch = "husbandry_growth")
    public static void bredBabiesGrowFaster(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        skills(player).setLevel(Skill.HUSBANDRY, 100);
        Cow a = spawn(helper, EntityType.COW, 1, 3);
        Cow b = spawn(helper, EntityType.COW, 2, 3);
        breed(player, a, b);
        later(helper, 8, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
            List<Cow> babies = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(at, at).inflate(12), Cow::isBaby);
            helper.assertTrue(babies.size() == 1, "expected one baby, found " + babies.size());
            int age = babies.get(0).getAge();
            helper.assertTrue(age > -16200 && age < -15000, "a level-100 baby started at age " + age + ", not near -16000");
            babies.get(0).discard();

            rank(player, Skill.HUSBANDRY, "herd_master", 1);
            Cow c = spawn(helper, EntityType.COW, 3, 3);
            Cow d = spawn(helper, EntityType.COW, 4, 3);
            HusbandryEvents.forget(player.getUUID());
            breed(player, c, d);
            later(helper, 8, () -> {
                List<Cow> more = helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(at, at).inflate(12), Cow::isBaby);
                helper.assertTrue(more.size() == 1, "expected one baby with Herd Master, found " + more.size());
                int headStart = more.get(0).getAge();
                helper.assertTrue(headStart > -8200 && headStart < -7000,
                        "a Herd Master baby started at age " + headStart + ", not near -8000");
                cast.done();
            });
        });
    }

    /** Pets get more health with your level, and lose it when the level goes. */
    @GameTest(template = T, batch = "husbandry_pethealth")
    public static void petsAreHardierWithYourLevel(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        skills(player).setLevel(Skill.HUSBANDRY, 100);
        Wolf wolf = spawn(helper, EntityType.WOLF, 2, 2);
        wolf.tame(player);
        float base = wolf.getMaxHealth();
        player.tickCount = 107;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        float boosted = wolf.getMaxHealth();
        helper.assertTrue(boosted > base * 1.4f && boosted < base * 1.6f,
                "a level-100 owner's wolf has " + boosted + " health from " + base);
        helper.assertTrue(wolf.getHealth() > base, "the extra health was not given");
        skills(player).setLevel(Skill.HUSBANDRY, 0);
        player.tickCount = 207;
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(Math.abs(wolf.getMaxHealth() - base) < 0.01f, "the bonus stayed after the level went");
        cast.done();
    }

    // ---- XP source: taming ----------------------------------------------------------------------------

    /** A wolf you tame pays, with the first-time bonus once; a wolf that was already tame pays nothing; the cap is 8. */
    @GameTest(template = T, batch = "husbandry_tame")
    public static void tamingPaysOncePerAnimalWithAFirstOfSpeciesBonus(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Wolf wild = spawn(helper, EntityType.WOLF, 2, 2);
        click(player, wild, InteractionHand.MAIN_HAND);
        wild.tame(player);
        later(helper, 3, () -> {
            float paid = logged(player, HusbandryEvents.SOURCE_TAME);
            helper.assertTrue(paid > 0f, "taming a wolf paid nothing");
            helper.assertTrue(skills(player).hasVisited("first:husbandry:entity.minecraft.wolf"),
                    "the first wolf did not count as a first of its species");

            Wolf bred = spawn(helper, EntityType.WOLF, 3, 2);
            bred.tame(player);
            later(helper, 3, () -> {
                helper.assertTrue(logged(player, HusbandryEvents.SOURCE_TAME) == paid,
                        "a wolf that was never clicked while wild (a bred tame one) paid");
                int payouts = 0;
                float before = paid;
                for (int i = 0; i < HusbandryMath.TAME_LIMIT + 3; i++) {
                    Wolf next = spawn(helper, EntityType.WOLF, 1 + i % 4, 3 + i / 4);
                    click(player, next, InteractionHand.MAIN_HAND);
                    next.tame(player);
                }
                later(helper, 3, () -> {
                    float total = logged(player, HusbandryEvents.SOURCE_TAME);
                    // The first wolf paid, and then at most 7 more inside 30 minutes.
                    helper.assertTrue(total > before, "no more wolves paid");
                    helper.assertTrue(total < before * (HusbandryMath.TAME_LIMIT + 0.5f),
                            "more than " + HusbandryMath.TAME_LIMIT + " tamings paid inside 30 minutes: " + total);
                    cast.done();
                });
            });
        });
    }

    /** Gentle Hands: mounting an untamed horse adds temper. */
    @GameTest(template = T, batch = "husbandry_gentle")
    public static void gentleHandsAddsTemperToAnUntamedHorse(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Horse horse = spawn(helper, EntityType.HORSE, 2, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        click(player, horse, InteractionHand.MAIN_HAND);
        helper.assertTrue(horse.getTemper() == 0, "temper moved with no talent: " + horse.getTemper());
        rank(player, Skill.HUSBANDRY, "gentle_hands", 3);
        click(player, horse, InteractionHand.MAIN_HAND);
        helper.assertTrue(horse.getTemper() == HusbandryMath.gentleTemper(3),
                "temper is " + horse.getTemper() + ", not " + HusbandryMath.gentleTemper(3));
        cast.done();
    }

    // ---- XP sources: shearing, milking, brushing, feeding, honey ---------------------------------------------

    /** A sheep with a coat pays, and Wool Gatherer adds wool; a sheared one pays nothing. */
    @GameTest(template = T, batch = "husbandry_shear")
    public static void shearingPaysOnlyForARegrownCoat(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        rank(player, Skill.HUSBANDRY, "wool_gatherer", 3);
        Sheep sheep = spawn(helper, EntityType.SHEEP, 2, 2);
        sheep.setColor(DyeColor.RED);
        use(player, sheep, new ItemStack(Items.SHEARS));
        later(helper, 4, () -> {
            float paid = logged(player, HusbandryEvents.SOURCE_SHEAR);
            helper.assertTrue(sheep.isSheared(), "the sheep was not sheared");
            helper.assertTrue(paid > 0f, "shearing a sheep paid nothing");
            int wool = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, sheep.getBoundingBox().inflate(3))) {
                if (item.getItem().is(Items.RED_WOOL)) {
                    wool += item.getItem().getCount();
                }
            }
            helper.assertTrue(wool >= 1 + 3, "Wool Gatherer 3 should leave at least 4 wool (1 vanilla + 3), found " + wool);

            use(player, sheep, new ItemStack(Items.SHEARS));
            later(helper, 4, () -> {
                helper.assertTrue(logged(player, HusbandryEvents.SOURCE_SHEAR) == paid, "a sheared sheep paid again");
                cast.done();
            });
        });
    }

    /** Milking pays once per cow per 2 minutes. */
    @GameTest(template = T, batch = "husbandry_milk")
    public static void milkingPaysOncePerCowPerTwoMinutes(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Cow cow = spawn(helper, EntityType.COW, 2, 2);
        use(player, cow, new ItemStack(Items.BUCKET));
        later(helper, 4, () -> {
            float paid = logged(player, HusbandryEvents.SOURCE_MILK);
            helper.assertTrue(paid > 0f, "milking a cow paid nothing");
            use(player, cow, new ItemStack(Items.BUCKET));
            later(helper, 4, () -> {
                helper.assertTrue(logged(player, HusbandryEvents.SOURCE_MILK) == paid, "the same cow paid twice in a row");
                Cow other = spawn(helper, EntityType.COW, 3, 2);
                use(player, other, new ItemStack(Items.BUCKET));
                later(helper, 4, () -> {
                    helper.assertTrue(logged(player, HusbandryEvents.SOURCE_MILK) > paid, "a second cow paid nothing");
                    // A baby cow gives no milk and no XP, and a click with no bucket does nothing.
                    Cow calf = spawn(helper, EntityType.COW, 4, 2);
                    calf.setAge(-1000);
                    float before = logged(player, HusbandryEvents.SOURCE_MILK);
                    use(player, calf, new ItemStack(Items.BUCKET));
                    later(helper, 4, () -> {
                        helper.assertTrue(logged(player, HusbandryEvents.SOURCE_MILK) == before, "a calf was milked for XP");
                        cast.done();
                    });
                });
            });
        });
    }

    /** Feeding a baby that grows pays; an adult, or a baby with the wrong food, pays nothing. */
    @GameTest(template = T, batch = "husbandry_feed")
    public static void feedingABabyPaysWhenItGrows(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Cow calf = spawn(helper, EntityType.COW, 2, 2);
        calf.setAge(-24000);
        use(player, calf, new ItemStack(Items.WHEAT, 4));
        later(helper, 4, () -> {
            float paid = logged(player, HusbandryEvents.SOURCE_FEED);
            helper.assertTrue(calf.getAge() > -24000 + 100, "the wheat did not make the calf grow");
            helper.assertTrue(paid > 0f, "feeding a baby paid nothing");

            Cow adult = spawn(helper, EntityType.COW, 3, 2);
            use(player, adult, new ItemStack(Items.WHEAT, 4));
            Cow wrong = spawn(helper, EntityType.COW, 4, 2);
            wrong.setAge(-24000);
            use(player, wrong, new ItemStack(Items.BONE, 4));
            later(helper, 4, () -> {
                helper.assertTrue(logged(player, HusbandryEvents.SOURCE_FEED) == paid,
                        "an adult or a baby fed the wrong food paid");
                cast.done();
            });
        });
    }

    /** Taking honey from a full hive pays; a hive with no honey pays nothing. */
    @GameTest(template = T, batch = "husbandry_honey")
    public static void honeyPaysForAFullHiveOnly(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
        BlockPos pos = helper.absolutePos(rel);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 4));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        later(helper, 4, () -> {
            float paid = logged(player, HusbandryEvents.SOURCE_HONEY);
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL) < 5,
                    "the bottle took no honey");
            helper.assertTrue(paid > 0f, "taking honey paid nothing");
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            later(helper, 4, () -> {
                helper.assertTrue(logged(player, HusbandryEvents.SOURCE_HONEY) == paid, "an emptied hive paid again");
                cast.done();
            });
        });
    }

    // ---- The tree's pet mechanics ----------------------------------------------------------------------------------

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        Zombie mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 2, z));
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(0);
        }
        return mob;
    }

    private static float bite(Wolf wolf, Zombie target, float amount) {
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurt(target.damageSources().mobAttack(wolf), amount);
        return before - target.getHealth();
    }

    /** Apprentice Wolf and Shared Strength make your wolf bite harder; a wild wolf is untouched. */
    @GameTest(template = T, batch = "husbandry_wolf")
    public static void aWolfBitesWithYourWeaponSkillAndOnYourTarget(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Wolf wolf = spawn(helper, EntityType.WOLF, 2, 2);
        wolf.tame(player);
        Zombie zombie = zombie(helper, 3, 3);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        zombie.setHealth(400f);
        float plain = bite(wolf, zombie, 4f);
        helper.assertTrue(Math.abs(plain - 4f) < 0.05f, "a plain bite did " + plain);

        skills(player).setLevel(Skill.SWORDS, 100);
        rank(player, Skill.HUSBANDRY, "wolf_learns", 3);
        float learned = bite(wolf, zombie, 4f);
        double bonus = skills(player).bonus(Skill.SWORDS);
        helper.assertTrue(Math.abs(learned - 4f * (1.0 + bonus)) < 0.1f,
                "an Apprentice Wolf bit for " + learned + ", expected " + 4f * (1.0 + bonus));

        rank(player, Skill.HUSBANDRY, "shared_strength", 3);
        float alone = bite(wolf, zombie, 4f);
        helper.assertTrue(Math.abs(alone - learned) < 0.1f, "Shared Strength worked on a mob its owner had not hit");
        zombie.invulnerableTime = 0;
        zombie.hurt(zombie.damageSources().playerAttack(player), 1f);
        float shared = bite(wolf, zombie, 4f);
        helper.assertTrue(Math.abs(shared - learned * 1.3f) < 0.15f,
                "a bite on the owner's target did " + shared + ", expected " + learned * 1.3f);
        cast.done();
    }

    /** Faithful Friend saves a pet that would die inside a lit Hearth, once per 10 minutes; nowhere else. */
    @GameTest(template = T, batch = "husbandry_faithful")
    public static void aPetAtYourHearthIsSavedOnce(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        rank(player, Skill.HUSBANDRY, "faithful_friend", 1);
        BlockPos rel = new BlockPos(4, 2, 4);
        helper.setBlock(rel, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, 3));
        BlockPos hearth = helper.absolutePos(rel);
        cast.hearths.add(hearth);
        Wolf wolf = spawn(helper, EntityType.WOLF, 3, 3);
        wolf.tame(player);
        helper.assertTrue(dev.amman.proficiency.hearth.HearthRange.hearthEntityAt(helper.getLevel(), wolf.blockPosition()) != null,
                "the test Hearth is not in the index");
        wolf.hurt(wolf.damageSources().generic(), 500f);
        helper.assertTrue(wolf.isAlive() && wolf.getHealth() >= 1f, "the wolf was not saved inside the Hearth");
        wolf.invulnerableTime = 0;
        wolf.hurt(wolf.damageSources().generic(), 500f);
        helper.assertTrue(!wolf.isAlive(), "a second rescue came inside 10 minutes");

        Wolf away = spawn(helper, EntityType.WOLF, 0, 1);
        away.tame(player);
        away.setPos(helper.absoluteVec(new Vec3(40, 2, 40)));
        HusbandryEvents.forget(player.getUUID());
        away.hurt(away.damageSources().generic(), 500f);
        helper.assertTrue(!away.isAlive(), "a pet far from any Hearth was saved");
        cast.done();
    }

    /** Pasture Blessing: pets within range regenerate, Twins fires on every breeding, and the caster gets no buff. */
    @GameTest(template = T, batch = "husbandry_ability")
    public static void pastureBlessingBlessesPetsAndFiresTwinsEveryTime(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        PlayerSkills skills = skills(player);
        skills.setLevel(Skill.HUSBANDRY, 100);
        skills.fillTree(Skill.HUSBANDRY);
        Wolf wolf = spawn(helper, EntityType.WOLF, 2, 2);
        wolf.tame(player);
        Wolf stray = spawn(helper, EntityType.WOLF, 3, 2);
        helper.assertTrue(ActiveService.activate(player, Skill.HUSBANDRY) == null, "Pasture Blessing would not start");
        helper.assertTrue(wolf.hasEffect(MobEffects.REGENERATION), "the pet did not regenerate");
        helper.assertTrue(wolf.hasEffect(MobEffects.MOVEMENT_SPEED), "Herd Master's Speed is missing");
        helper.assertTrue(!stray.hasEffect(MobEffects.REGENERATION), "a wild wolf was blessed");
        helper.assertTrue(player.getActiveEffects().isEmpty(), "the caster got a buff");
        helper.assertTrue(ActiveService.isFrenzied(player, Skill.HUSBANDRY), "Twins is not on every breeding");
        cast.done();
    }

    /** A mooshroom is replaced by a cow when sheared; the shearing still pays, once. */
    @GameTest(template = T, batch = "husbandry_mooshroom")
    public static void shearingAMooshroomPays(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        net.minecraft.world.entity.animal.MushroomCow cow = spawn(helper, EntityType.MOOSHROOM, 2, 2);
        use(player, cow, new ItemStack(Items.SHEARS));
        later(helper, 4, () -> {
            helper.assertTrue(logged(player, HusbandryEvents.SOURCE_SHEAR) > 0f, "shearing a mooshroom paid nothing");
            cast.done();
        });
    }

    /** Feeding the same baby again within a minute pays nothing more. */
    @GameTest(template = T, batch = "husbandry_feed_twice")
    public static void feedingTheSameBabyTwiceInAMinutePaysOnce(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player(0.5, 0.5);
        Cow calf = spawn(helper, EntityType.COW, 2, 2);
        calf.setAge(-24000);
        use(player, calf, new ItemStack(Items.WHEAT, 4));
        later(helper, 4, () -> {
            float paid = logged(player, HusbandryEvents.SOURCE_FEED);
            helper.assertTrue(paid > 0f, "feeding a baby paid nothing");
            use(player, calf, new ItemStack(Items.WHEAT, 4));
            later(helper, 4, () -> {
                helper.assertTrue(logged(player, HusbandryEvents.SOURCE_FEED) == paid, "the same baby paid twice in a minute");
                cast.done();
            });
        });
    }
}
