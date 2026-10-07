package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.GuardianEvents;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Idea 38, Guardian, in a real world with two or more mock players: each XP source, each
 * anti-farm rule, the passive, Intercept, Shield Wall, the tree's mechanics and both synergies.
 * The arithmetic is in GuardianMathTest.
 *
 * <p>Each test is its own batch, so no other test's players stand near. Players left behind by
 * earlier tests are moved away first, and every player a test makes leaves the server at the end,
 * so a level-100 Guardian never shelters (or intercepts for) a later test's players.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardianGameTests {

    private GuardianGameTests() {
    }

    /** The players one test made, to send them away at the end. */
    private static final class Cast {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            // Leftovers from earlier tests (never removed by vanilla) go far up and out of reach.
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
                if (other.position().distanceTo(origin) < 64) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
        }

        /** A survival player at relative (x, 2, z). The empty template's floor is at y 1. */
        ServerPlayer player(double x, double z) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            clearSpawnProtection(player);
            GuardianEvents.forget(player.getUUID());
            players.add(player);
            return player;
        }

        void move(ServerPlayer player, double x, double z) {
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
        }

        void done() {
            for (ServerPlayer player : players) {
                ProcService.forget(player.getUUID());
                player.server.getPlayerList().remove(player);
            }
            helper.succeed();
        }
    }

    private static Mob zombie(GameTestHelper helper, int x, int z, LivingEntity target) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 2, z));
        if (target != null) {
            mob.setTarget(target);
        }
        return mob;
    }

    private static float logged(ServerPlayer player) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.GUARDIAN.ordinal()) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
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

    /** Raises a shield for real: vanilla counts it as blocking after 5 ticks of use. */
    private static void raiseShield(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("useItemRemaining");
            field.setAccessible(true);
            field.setInt(player, player.getUseItem().getUseDuration(player) - 10);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not raise the shield", e);
        }
    }

    private static void hit(LivingEntity victim, Mob by, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(victim.damageSources().mobAttack(by), amount);
    }

    // ---- XP source 1: cover -------------------------------------------------------------------

    /**
     * Cover pays for a mob that is after a friend near you, and more when the friend is in more
     * danger. Nothing for a mob after nobody or only you, a friend too far, a pet, a player's
     * hit, a creative guardian, or past a mob's 40-damage cap.
     */
    @GameTest(template = "empty", batch = "guardian_cover")
    public static void coverPaysOnlyForAMobAfterAFriend(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(3.5, 0.5);

        Mob loose = zombie(helper, 1, 3, null);
        helper.assertTrue(GuardianEvents.payCover(guardian, loose, 4f) == 0f, "a mob after nobody paid cover");
        Mob mine = zombie(helper, 0, 3, guardian);
        helper.assertTrue(GuardianEvents.payCover(guardian, mine, 4f) == 0f, "a mob after only you paid cover");

        Mob hunter = zombie(helper, 2, 2, friend);
        helper.assertTrue(GuardianEvents.payCover(guardian, hunter, 4f) > 0f, "a mob after a friend 3 blocks away paid nothing");

        // The real damage pipeline: the hunter's hit on you pays through LivingDamageEvent.
        float before = logged(guardian);
        hit(guardian, hunter, 3f);
        helper.assertTrue(logged(guardian) > before, "a real hit from a mob after a friend paid no cover");

        // A player's hit, even a friend's, never pays.
        before = logged(guardian);
        guardian.invulnerableTime = 0;
        guardian.hurt(guardian.damageSources().playerAttack(friend), 3f);
        helper.assertTrue(logged(guardian) == before, "a friend's own hit paid cover (PvP farm)");

        // A friend's pet is not danger.
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(3, 2, 3));
        wolf.tame(friend);
        wolf.setTarget(friend);
        helper.assertTrue(GuardianEvents.payCover(guardian, wolf, 4f) == 0f, "a pet paid cover");

        // Too far: the friend 12 blocks away, and the mob never pulled.
        cast.move(friend, 12.5, 0.5);
        Mob far = zombie(helper, 11, 2, friend);
        helper.assertTrue(GuardianEvents.payCover(guardian, far, 4f) == 0f, "a friend 12 blocks away paid cover");
        cast.move(friend, 3.5, 0.5);

        // The cap: a mob pays for 40 damage in all, 20 per hit.
        Mob capped = zombie(helper, 3, 3, friend);
        helper.assertTrue(GuardianEvents.payCover(guardian, capped, 20f) > 0f, "the first 20 paid nothing");
        helper.assertTrue(GuardianEvents.payCover(guardian, capped, 30f) > 0f, "the next 20 paid nothing");
        helper.assertTrue(GuardianEvents.payCover(guardian, capped, 5f) == 0f, "a mob paid past its 40-damage cap");

        guardian.setGameMode(GameType.CREATIVE);
        helper.assertTrue(GuardianEvents.payCover(guardian, zombie(helper, 4, 1, friend), 4f) == 0f,
                "a creative guardian earned Guardian");
        cast.done();
    }

    /** Danger: covering a friend near death pays far more than covering a healthy one. */
    @GameTest(template = "empty", batch = "guardian_danger")
    public static void coverScalesWithTheFriendsDanger(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer calm = cast.player(0.5, 0.5);
        ServerPlayer healthy = cast.player(2.5, 0.5);
        ServerPlayer tense = cast.player(0.5, 4.5);
        ServerPlayer hurt = cast.player(2.5, 4.5);
        hurt.setHealth(2f);
        float easy = GuardianEvents.payCover(calm, zombie(helper, 1, 2, healthy), 4f);
        float hard = GuardianEvents.payCover(tense, zombie(helper, 1, 3, hurt), 4f);
        helper.assertTrue(easy > 0f && hard > easy * 5f,
                "a friend at 2 health should pay about 7x a healthy one: " + hard + " vs " + easy);
        cast.done();
    }

    /** A mob you pull off a friend (your hit, then it turns) pays cover from 16 blocks; one that wanders over does not. */
    @GameTest(template = "empty", batch = "guardian_pull")
    public static void aMobYouPulledOffAFriendPays(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(12.5, 0.5);

        Mob pulled = zombie(helper, 6, 0, friend);
        pulled.hurt(guardian.damageSources().playerAttack(guardian), 1f);
        pulled.setTarget(guardian);
        helper.assertTrue(GuardianEvents.payCover(guardian, pulled, 4f) > 0f,
                "a mob pulled off a friend 12 blocks away paid no cover");

        Mob wandered = zombie(helper, 6, 2, friend);
        wandered.setTarget(guardian);
        helper.assertTrue(GuardianEvents.payCover(guardian, wandered, 4f) == 0f,
                "a mob that turned to you on its own paid cover for a friend 12 blocks away");
        cast.done();
    }

    // ---- XP source 2: block and absorb --------------------------------------------------------

    /** Blocking or absorbing a mob's hit pays with a friend within 4 blocks, and not without. */
    @GameTest(template = "empty", batch = "guardian_block")
    public static void blocksAndAbsorbedHitsPayBesideAFriend(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(12.5, 0.5);

        Mob first = zombie(helper, 0, 3, guardian);
        NeoForge.EVENT_BUS.post(new LivingShieldBlockEvent(guardian,
                new DamageContainer(guardian.damageSources().mobAttack(first), 6f), true));
        helper.assertTrue(logged(guardian) == 0f, "a block with no friend within 4 blocks paid Guardian");

        cast.move(friend, 2.5, 0.5);
        NeoForge.EVENT_BUS.post(new LivingShieldBlockEvent(guardian,
                new DamageContainer(guardian.damageSources().mobAttack(first), 6f), true));
        float blocked = logged(guardian);
        helper.assertTrue(blocked > 0f, "a block beside a friend paid nothing");

        // Absorption hearts soaking a mob's hit, through the real damage pipeline.
        guardian.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(8);
        guardian.setAbsorptionAmount(8f);
        helper.assertTrue(guardian.getAbsorptionAmount() > 0, "no absorption hearts to test with");
        hit(guardian, zombie(helper, 1, 3, null), 3f);
        helper.assertTrue(logged(guardian) > blocked, "an absorbed hit beside a friend paid nothing");
        cast.done();
    }

    // ---- XP source 3: avenger, and Oathkeeper -------------------------------------------------

    /** A kill on a mob that just hurt a friend pays; a mob that hurt nobody, or a friend far off, does not. */
    @GameTest(template = "empty", batch = "guardian_avenger")
    public static void avengingAFriendPays(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(3.5, 0.5);
        PlayerSkills skills = ProficiencyAttachments.of(guardian);

        Mob innocent = zombie(helper, 1, 3, null);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(innocent, guardian.damageSources().playerAttack(guardian)));
        helper.assertTrue(logged(guardian) == 0f, "killing a mob that hurt nobody paid avenger XP");

        Mob biter = zombie(helper, 3, 2, friend);
        hit(friend, biter, 3f);
        helper.assertTrue(GuardianEvents.avenge(friend, biter) == 0f, "the friend avenged themselves");
        // Oathkeeper (Courage + Guardian): the avenger kill also rallies.
        skills.setRank(Talents.get(Skill.COURAGE, "hot_blood"), 5);
        skills.setRank(Talents.get(Skill.GUARDIAN, "warding"), 5);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(biter, guardian.damageSources().playerAttack(guardian)));
        helper.assertTrue(logged(guardian) > 0f, "killing the mob that just hurt a friend paid nothing");
        helper.assertTrue(guardian.hasEffect(MobEffects.DAMAGE_BOOST), "Oathkeeper gave no Rally on an avenger kill");

        Mob other = zombie(helper, 3, 3, friend);
        hit(friend, other, 1f);
        cast.move(friend, 30.5, 0.5);
        helper.assertTrue(GuardianEvents.avenge(guardian, other) == 0f, "a friend 30 blocks away paid avenger XP");
        cast.done();
    }

    // ---- XP source 4: healing -----------------------------------------------------------------

    /**
     * A splash of Healing, a lingering healing cloud and a thrown Regeneration pay the thrower,
     * for real missing health only. Nothing for your own potion, a friend at full health, or a
     * friend who was only hurt by players.
     */
    @GameTest(template = "empty", batch = "guardian_heal")
    public static void healingAFriendWithPotionsPays(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer healer = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(3.5, 0.5);
        Mob biter = zombie(helper, 3, 3, friend);

        // Only a player hurt them (health set by hand, no mob): no heal pays.
        friend.setHealth(10f);
        ThrownPotion early = splash(helper, healer, friend);
        net.minecraft.world.effect.MobEffects.HEAL.value().applyInstantenousEffect(early, healer, friend, 0, 1.0);
        early.discard();
        helper.assertTrue(logged(healer) == 0f, "healing a friend no mob had hurt paid (PvP loop)");

        // Only a fall hurt them: a hazard is not danger, so no heal pays.
        friend.setHealth(20f);
        friend.invulnerableTime = 0;
        friend.hurt(friend.damageSources().fall(), 6f);
        helper.assertTrue(friend.getHealth() < 20f, "the fall did not land");
        ThrownPotion afterFall = splash(helper, healer, friend);
        net.minecraft.world.effect.MobEffects.HEAL.value().applyInstantenousEffect(afterFall, healer, friend, 0, 1.0);
        afterFall.discard();
        helper.assertTrue(logged(healer) == 0f, "healing a friend hurt only by a fall paid");

        friend.setHealth(20f);
        hit(friend, biter, 4f);
        helper.assertTrue(friend.getHealth() < 20f, "the zombie's hit did not land");
        ThrownPotion splash = splash(helper, healer, friend);
        net.minecraft.world.effect.MobEffects.HEAL.value().applyInstantenousEffect(splash, healer, friend, 1, 1.0);
        splash.discard();
        float splashed = logged(healer);
        helper.assertTrue(splashed > 0f, "a splash of Healing on a hurt friend paid nothing");

        // At full health the heal gives nothing back, so it pays nothing.
        friend.setHealth(20f);
        ThrownPotion wasted = splash(helper, healer, friend);
        net.minecraft.world.effect.MobEffects.HEAL.value().applyInstantenousEffect(wasted, healer, friend, 1, 1.0);
        wasted.discard();
        helper.assertTrue(logged(healer) == splashed, "healing a friend at full health paid");

        // Your own potion heals you, not a friend. (A mob after nobody, so no cover is paid.)
        hit(healer, zombie(helper, 0, 4, null), 4f);
        ThrownPotion own = splash(helper, healer, healer);
        net.minecraft.world.effect.MobEffects.HEAL.value().applyInstantenousEffect(own, healer, healer, 1, 1.0);
        own.discard();
        helper.assertTrue(logged(healer) == splashed, "healing yourself paid Guardian");

        // Regeneration you threw: its heals pay you.
        hit(friend, biter, 4f);
        friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), healer);
        friend.heal(1f);
        float regen = logged(healer);
        helper.assertTrue(regen > splashed, "a friend's Regeneration from your potion paid nothing");

        // A heal on a tick where Regeneration does not heal (food, natural regen) pays nothing.
        friend.removeEffect(MobEffects.REGENERATION);
        friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 201, 1), healer);
        friend.heal(1f);
        helper.assertTrue(logged(healer) == regen, "a heal off Regeneration's own tick paid the thrower");

        // Their own Regeneration (a golden apple) replaces yours: its heals pay nobody.
        friend.removeEffect(MobEffects.REGENERATION);
        friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        friend.heal(1f);
        helper.assertTrue(logged(healer) == regen, "the friend's own Regeneration paid the earlier thrower");

        // A lingering healing cloud you threw.
        ServerPlayer third = cast.player(0.5, 4.5);
        hit(third, biter, 4f);
        AreaEffectCloud cloud = new AreaEffectCloud(helper.getLevel(), third.getX(), third.getY(), third.getZ());
        cloud.setOwner(healer);
        cloud.setRadius(3f);
        cloud.setPotionContents(new PotionContents(Potions.HEALING));
        helper.getLevel().addFreshEntity(cloud);
        third.heal(2f);
        cloud.discard();
        helper.assertTrue(logged(healer) > regen, "a lingering healing cloud you threw paid nothing");
        cast.done();
    }

    private static ThrownPotion splash(GameTestHelper helper, ServerPlayer thrower, ServerPlayer at) {
        ThrownPotion potion = new ThrownPotion(helper.getLevel(), thrower);
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        potion.setPos(at.getX(), at.getY() + 1, at.getZ());
        helper.getLevel().addFreshEntity(potion);
        return potion;
    }

    // ---- XP source 5: the close call ----------------------------------------------------------

    /**
     * A friend drops under 30% from a mob and lives 10 s with you beside them: it pays. A guardian
     * who walks off, one too far away, a drop a player caused, and a second drop inside the
     * cooldown pay nothing.
     */
    @GameTest(template = "empty", batch = "guardian_revive", timeoutTicks = 400)
    public static void standingByAFriendThroughACloseCallPays(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer walker = cast.player(1.5, 1.5);
        ServerPlayer far = cast.player(12.5, 0.5);
        ServerPlayer friend = cast.player(2.5, 0.5);
        Mob biter = zombie(helper, 3, 3, friend);

        friend.setHealth(7f);
        hit(friend, biter, 2f);
        helper.assertTrue(friend.getHealth() < 6f && friend.isAlive(), "the friend is not under 30%: " + friend.getHealth());
        helper.assertTrue(GuardianEvents.watching(guardian) == 1, "the guardian beside them is not watching");
        helper.assertTrue(GuardianEvents.watching(walker) == 1, "the second guardian beside them is not watching");
        helper.assertTrue(GuardianEvents.watching(far) == 0, "a guardian 10 blocks away is watching");
        cast.move(walker, 14.5, 4.5);

        helper.runAfterDelay(205, () -> {
            GuardianEvents.tickWatches(helper.getLevel().getServer());
            helper.assertTrue(logged(guardian) > 0f, "staying by a friend through a close call paid nothing");
            helper.assertTrue(logged(walker) == 0f, "a guardian who walked away was paid");
            helper.assertTrue(GuardianEvents.watching(guardian) == 0, "the watch did not end");

            friend.setHealth(7f);
            hit(friend, biter, 2f);
            helper.assertTrue(GuardianEvents.watching(guardian) == 0, "a second close call inside the cooldown started a watch");

            // A fall is not danger: it starts no watch.
            ServerPlayer faller = cast.player(1.5, 0.5);
            faller.setHealth(7f);
            faller.invulnerableTime = 0;
            faller.hurt(faller.damageSources().fall(), 2f);
            helper.assertTrue(faller.getHealth() < 6f, "the fall did not land: " + faller.getHealth());
            helper.assertTrue(GuardianEvents.watching(guardian) == 0, "a close call from a fall started a watch");

            ServerPlayer other = cast.player(0.5, 2.5);
            other.setHealth(7f);
            other.invulnerableTime = 0;
            other.hurt(other.damageSources().playerAttack(far), 2f);
            helper.assertTrue(GuardianEvents.watching(walker) == 0 && GuardianEvents.watching(far) == 0,
                    "a drop a player caused started a watch");
            cast.done();
        });
    }

    // ---- The passive, Intercept and Bulwark ---------------------------------------------------

    /** A level-100 guardian 10 blocks away takes 15% off a friend's hit from a mob. */
    @GameTest(template = "empty", batch = "guardian_passive")
    public static void thePassiveShieldsFriendsNearby(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(10.5, 0.5);
        ServerPlayer friend = cast.player(0.5, 0.5);
        ProficiencyAttachments.of(guardian).setLevel(Skill.GUARDIAN, 100);
        Mob biter = zombie(helper, 0, 3, friend);

        hit(friend, biter, 8f);
        float sheltered = 20f - friend.getHealth();
        friend.setHealth(20f);
        guardian.setPos(guardian.getX() + 40, guardian.getY(), guardian.getZ());
        hit(friend, biter, 8f);
        float bare = 20f - friend.getHealth();
        helper.assertTrue(bare > 0 && Math.abs(sheltered / bare - 0.85f) < 0.02f,
                "the passive should take 15% off: " + sheltered + " vs " + bare);
        cast.done();
    }

    /** Intercept: a landed proc moves a friend's hit onto the guardian, at half damage; Bulwark puts it on the shield. */
    @GameTest(template = "empty", batch = "guardian_intercept")
    public static void interceptTakesTheHitForAFriend(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(3.5, 0.5);
        // Level 0: the proc never rolls on its own, so only the forced one lands.
        Mob biter = zombie(helper, 3, 3, friend);

        ProcService.forceNext(guardian, Skill.GUARDIAN);
        hit(friend, biter, 6f);
        helper.assertTrue(friend.getHealth() == 20f, "the friend still took the intercepted hit");
        float taken = 20f - guardian.getHealth();
        helper.assertTrue(taken > 0f, "the guardian did not take the intercepted hit");
        helper.assertTrue(logged(guardian) > 0f, "the intercepted hit paid no cover");

        // With no proc the friend takes it.
        hit(friend, biter, 6f);
        helper.assertTrue(friend.getHealth() < 20f, "a hit was intercepted without a proc");

        // Bulwark: with a raised shield the shield takes all of it.
        ServerPlayer wall = cast.player(0.5, 4.5);
        ServerPlayer ward = cast.player(2.5, 4.5);
        PlayerSkills skills = ProficiencyAttachments.of(wall);
        skills.setRank(Talents.get(Skill.BLOCKING, "reinforced"), 5);
        skills.setRank(Talents.get(Skill.GUARDIAN, "bodyguard"), 3);
        raiseShield(wall);
        helper.assertTrue(wall.isBlocking(), "the mock could not raise its shield");
        ProcService.forceNext(wall, Skill.GUARDIAN);
        hit(ward, zombie(helper, 2, 6, ward), 6f);
        helper.assertTrue(ward.getHealth() == 20f && wall.getHealth() == 20f,
                "Bulwark: the shield should take it all, got " + ward.getHealth() + " / " + wall.getHealth());
        helper.assertTrue(wall.getUseItem().getDamageValue() > 0, "Bulwark did not wear the shield");
        cast.done();
    }

    // ---- Shield Wall --------------------------------------------------------------------------

    /** Shield Wall lends your armour to friends in reach, turns their mobs on you, and ends when they leave. */
    @GameTest(template = "empty", batch = "guardian_wall", timeoutTicks = 200)
    public static void shieldWallLendsArmourAndDrawsMobs(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(3.5, 0.5);
        ServerPlayer outside = cast.player(20.5, 0.5);
        PlayerSkills skills = ProficiencyAttachments.of(guardian);
        skills.setLevel(Skill.GUARDIAN, 100);
        skills.fillTree(Skill.GUARDIAN);
        guardian.getAttribute(Attributes.ARMOR).setBaseValue(8);
        guardian.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        Mob biter = zombie(helper, 3, 3, friend);

        helper.assertTrue(ActiveService.activate(guardian, Skill.GUARDIAN) == null, "Shield Wall did not start");
        GuardianEvents.shieldWall(guardian);
        helper.assertTrue(friend.getArmorValue() == 8, "the friend got " + friend.getArmorValue() + " armour, not 8");
        helper.assertTrue(friend.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 2, "the friend got no toughness");
        helper.assertTrue(outside.getArmorValue() == 0, "a player 20 blocks away got the wall");
        helper.assertTrue(biter.getTarget() == guardian, "the mob after the friend did not turn on the guardian");
        helper.assertTrue(friend.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Sentinel gave no Resistance");
        helper.assertTrue(guardian.getArmorValue() == 8, "the guardian's own armour changed");

        cast.move(friend, 30.5, 0.5);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(friend.getArmorValue() == 0, "the wall's armour stayed on a friend who left");
            cast.done();
        });
    }

    // ---- The tree -----------------------------------------------------------------------------

    /** Taunt, Mending Guard, Heartshare, Watchful Compass and Sworn Shield, with a full tree. */
    @GameTest(template = "empty", batch = "guardian_tree")
    public static void theGuardianTreeMechanics(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer guardian = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(3.5, 0.5);
        ServerPlayer plain = cast.player(0.5, 3.5);
        // A full tree at level 0: every node works, and Intercept never rolls, so no hit moves.
        PlayerSkills skills = ProficiencyAttachments.of(guardian);
        skills.fillTree(Skill.GUARDIAN);

        // Taunt: hit a mob after a friend, it turns on you and cannot switch back for 5 s.
        Mob taunted = zombie(helper, 2, 3, friend);
        taunted.hurt(guardian.damageSources().playerAttack(guardian), 1f);
        helper.assertTrue(taunted.getTarget() == guardian, "Taunt did not turn the mob");
        taunted.setTarget(friend);
        helper.assertTrue(taunted.getTarget() == guardian, "a taunted mob switched away");
        taunted.discard();

        // Mending Guard 3: a blocked hit heals the most hurt friend within 8 blocks by 3.
        friend.setHealth(10f);
        plain.setHealth(15f);
        Mob blocked = zombie(helper, 0, 5, guardian);
        NeoForge.EVENT_BUS.post(new LivingShieldBlockEvent(guardian,
                new DamageContainer(guardian.damageSources().mobAttack(blocked), 4f), true));
        helper.assertTrue(Math.abs(friend.getHealth() - 13f) < 0.01f, "Mending Guard healed to " + friend.getHealth());

        // Heartshare 3: your Absorption goes to the most hurt friend for all of its time.
        plain.setHealth(8f);
        guardian.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        MobEffectInstance shared = plain.getEffect(MobEffects.ABSORPTION);
        helper.assertTrue(shared != null && shared.getDuration() >= 2390, "Heartshare gave " + shared);
        helper.assertTrue(friend.getEffect(MobEffects.ABSORPTION) == null, "Heartshare went to a less hurt friend");

        // Watchful Compass: register the friend, then their hurt pulses once per 3 s; a player
        // without the node holding the same compass gets nothing.
        ItemStack compass = new ItemStack(ProficiencyItems.FRIEND_COMPASS.get());
        guardian.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.getItem().interactLivingEntity(compass, guardian, friend, InteractionHand.MAIN_HAND);
        ItemStack copy = guardian.getMainHandItem().copy();
        plain.setItemInHand(InteractionHand.MAIN_HAND, copy);
        helper.assertTrue(GuardianEvents.pulseWatchers(friend) == 1, "the compass did not pulse exactly once");
        helper.assertTrue(GuardianEvents.pulseWatchers(friend) == 0, "the compass pulsed twice within 3 s");

        Mob killer = zombie(helper, 4, 3, friend);
        guardian.removeAllEffects();
        guardian.setAbsorptionAmount(0f);
        plain.removeAllEffects();
        plain.setAbsorptionAmount(0f);

        // Sworn Shield ignores a hazard: a fall that kills the friend is not redirected.
        guardian.setHealth(20f);
        friend.setHealth(4f);
        friend.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        friend.invulnerableTime = 0;
        friend.hurt(friend.damageSources().fall(), 10f);
        helper.assertTrue(friend.getOffhandItem().isEmpty() && guardian.getHealth() == 20f,
                "Sworn Shield redirected a fall");

        // It does not trade one death for another: a guardian the half would kill is skipped.
        guardian.setHealth(3f);
        friend.removeAllEffects();
        friend.setAbsorptionAmount(0f);
        friend.setHealth(4f);
        friend.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        hit(friend, killer, 10f);
        helper.assertTrue(friend.getOffhandItem().isEmpty() && guardian.isAlive() && guardian.getHealth() == 3f,
                "Sworn Shield sent a killing half to a 3-health guardian: " + guardian.getHealth());

        // How much the zombie's 10 really is after difficulty, on a player with no armour.
        plain.setHealth(20f);
        hit(plain, killer, 10f);
        float blow = 20f - plain.getHealth();

        // Sworn Shield: a killing blow on the friend lands on the guardian at half damage, and the
        // guardian's own armour does not cut it again (it was already cut on the friend).
        guardian.setHealth(20f);
        guardian.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        friend.removeAllEffects();
        friend.setAbsorptionAmount(0f);
        friend.setHealth(4f);
        hit(friend, killer, 10f);
        helper.assertTrue(friend.isAlive() && friend.getHealth() == 4f, "Sworn Shield did not save the friend: " + friend.getHealth());
        helper.assertTrue(blow > 0f && Math.abs((20f - guardian.getHealth()) - blow / 2f) < 0.01f,
                "the guardian should take half of " + blow + ", took " + (20f - guardian.getHealth()));
        guardian.getAttribute(Attributes.ARMOR).setBaseValue(0.0);

        // Once per 10 minutes: the next killing blow is not redirected. A totem shows it landed.
        plain.removeAllEffects();
        plain.setAbsorptionAmount(0f);
        plain.setHealth(2f);
        plain.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        hit(plain, zombie(helper, 1, 5, plain), 10f);
        helper.assertTrue(plain.getOffhandItem().isEmpty(), "Sworn Shield fired twice inside its cooldown");
        cast.done();
    }
}
