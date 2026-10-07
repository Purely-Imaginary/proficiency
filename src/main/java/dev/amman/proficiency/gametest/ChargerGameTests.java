package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.ChargerEvents;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Idea 39, Charger, in a real world with mock players: each XP source through the real damage
 * and death events, each anti-farm rule, the passive, the shield and lifesteal, Spearhead, Trust
 * the Line, Shield and Spear, Breach, Charge!, the tree's mechanics and the hooks Tactician uses.
 * The arithmetic is in ChargerMathTest.
 *
 * <p>Every player faces +z (yaw 0), so "ahead" is +z and "behind" is -z. Each test is its own
 * batch, and every player a test makes leaves the server at the end.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChargerGameTests {

    /**
     * The mod's own empty template. NeoForge adds the "proficiency:" namespace itself (a full id
     * here crashes it). The Fabric port must write "proficiency:empty": Fabric reads a bare name
     * as minecraft:empty.
     */
    private static final String T = "empty";

    private ChargerGameTests() {
    }

    /** The players one test made, to send them away at the end. */
    private static final class Cast {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
                if (other.position().distanceTo(origin) < 64) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            clearMobs();
        }

        /**
         * Mobs outside the 5-block template are never cleared by vanilla, and a later batch can
         * land on the same spot, so a leftover zombie would count as "a mob ahead". Each Charger
         * test is its own batch, so nothing else is running here.
         */
        void clearMobs() {
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class,
                    new net.minecraft.world.phys.AABB(origin, origin).inflate(48))) {
                mob.discard();
            }
        }

        /** A survival player at relative (x, 2, z), facing +z. */
        ServerPlayer player(double x, double z) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            player.setYRot(0f);
            player.setYHeadRot(0f);
            clearSpawnProtection(player);
            ChargerEvents.forget(player.getUUID());
            players.add(player);
            return player;
        }

        void move(ServerPlayer player, double x, double z) {
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
        }

        void done() {
            for (ServerPlayer player : players) {
                ProcService.forget(player.getUUID());
                ChargerEvents.forget(player.getUUID());
                player.server.getPlayerList().remove(player);
            }
            clearMobs();
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

    /** A zombie with no armour, so damage reads straight off its health. */
    private static Mob zombie(GameTestHelper helper, int x, int z) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 2, z));
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(0);
        }
        return mob;
    }

    /** A melee hit from the player's own hand, through the real damage pipeline. */
    private static void punch(ServerPlayer player, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().playerAttack(player), amount);
    }

    /** As if the player stood {@code back} blocks behind where they are now, a moment ago. */
    private static void ranUp(ServerPlayer player, double back) {
        long now = player.level().getGameTime();
        ChargerEvents.trail(player).clear();
        ChargerEvents.trail(player).add(now - 10, player.getX(), player.getY(), player.getZ() - back);
    }

    private static float logged(ServerPlayer player, String source) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.CHARGER.ordinal() && source.equals(entry.source())) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static PlayerSkills skills(ServerPlayer player) {
        return ProficiencyAttachments.of(player);
    }

    // ---- XP source 1: first blood --------------------------------------------------------------

    /**
     * First blood pays for the first melee hit on a fresh hostile, once per mob. Nothing for a
     * second hit, a mob another player hit, a softened mob, an arrow, a cow, a pet or a creative
     * player.
     */
    @GameTest(template = T, batch = "charger_first_blood")
    public static void firstBloodPaysOncePerFreshMob(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(6.5, 0.5);
        String src = ChargerEvents.SOURCE_FIRST_BLOOD;

        Mob first = zombie(helper, 1, 2);
        punch(charger, first, 2f);
        float once = logged(charger, src);
        helper.assertTrue(once > 0f, "a first hit on a fresh zombie paid no first blood");

        punch(charger, first, 2f);
        helper.assertTrue(logged(charger, src) == once, "a second hit paid first blood again");

        // Ten quiet seconds later and healed up: the damage bonus comes back, the XP does not.
        first.getPersistentData().putLong("proficiency_charger_hit_at", helper.getLevel().getGameTime() - 400);
        first.setHealth(first.getMaxHealth());
        punch(charger, first, 2f);
        helper.assertTrue(logged(charger, src) == once, "one mob paid first blood twice");

        Mob shared = zombie(helper, 3, 2);
        punch(friend, shared, 2f);
        shared.setHealth(shared.getMaxHealth());
        punch(charger, shared, 2f);
        helper.assertTrue(logged(charger, src) == once, "a mob a friend just hit paid first blood");

        Mob softened = zombie(helper, 2, 3);
        softened.setHealth(10f);
        punch(charger, softened, 2f);
        helper.assertTrue(logged(charger, src) == once, "a softened mob (drop tower) paid first blood");

        Mob shot = zombie(helper, 0, 3);
        Arrow arrow = new Arrow(helper.getLevel(), charger);
        shot.invulnerableTime = 0;
        shot.hurt(shot.damageSources().arrow(arrow, charger), 2f);
        helper.assertTrue(logged(charger, src) == once, "an arrow paid first blood (melee only)");

        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(1, 2, 4));
        punch(charger, cow, 1f);
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(2, 2, 4));
        wolf.tame(friend);
        punch(charger, wolf, 1f);
        helper.assertTrue(logged(charger, src) == once, "a cow or a pet paid first blood");

        charger.setGameMode(GameType.CREATIVE);
        punch(charger, zombie(helper, 3, 4), 2f);
        helper.assertTrue(logged(charger, src) == once, "a creative player earned Charger");
        cast.done();
    }

    /** The passive: at level 100 a first blood hits 30% harder; the next hit does not. */
    @GameTest(template = T, batch = "charger_passive")
    public static void firstBloodHitsHarder(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(0.5, 0.5);
        skills(charger).setLevel(Skill.CHARGER, 100);
        Mob mob = zombie(helper, 1, 2);
        punch(charger, mob, 4f);
        float firstLoss = mob.getMaxHealth() - mob.getHealth();
        helper.assertTrue(Math.abs(firstLoss - 5.2f) < 0.05f, "first blood took " + firstLoss + ", not 5.2");
        float before = mob.getHealth();
        punch(charger, mob, 4f);
        float secondLoss = before - mob.getHealth();
        helper.assertTrue(Math.abs(secondLoss - 4f) < 0.05f, "a second hit took " + secondLoss + ", not 4");
        cast.done();
    }

    // ---- XP source 2: the charge ---------------------------------------------------------------

    /**
     * A melee hit after closing 5 blocks in 2 s pays, more for a sprint attack, and spends the
     * charge. Standing still pays nothing, and one mob pays at most 3 charge hits.
     */
    @GameTest(template = T, batch = "charger_charge")
    public static void chargeHitsPayForTheDistanceClosed(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(0.5, 0.5);
        String src = ChargerEvents.SOURCE_CHARGE;

        Mob still = zombie(helper, 0, 2);
        ChargerEvents.trail(charger).clear();
        punch(charger, still, 1f);
        helper.assertTrue(logged(charger, src) == 0f, "standing still paid a charge");

        // Two fresh players with the same run and the same mob offset, so tempo and company match.
        ServerPlayer walker = cast.player(8.5, 0.5);
        ServerPlayer sprinter = cast.player(14.5, 0.5);
        Mob walked = zombie(helper, 8, 3);
        ranUp(walker, 6);
        punch(walker, walked, 1f);
        float walk = logged(walker, src);
        helper.assertTrue(walk > 0f, "walking 6 blocks at a mob paid no charge (no sprint is needed)");
        helper.assertTrue(ChargerEvents.trail(walker).size() == 0, "a charge hit did not spend the charge");

        punch(walker, walked, 1f);
        helper.assertTrue(logged(walker, src) == walk, "a second hit without a new run paid a charge");

        Mob sprinted = zombie(helper, 14, 3);
        ranUp(sprinter, 6);
        sprinter.setSprinting(true);
        punch(sprinter, sprinted, 1f);
        sprinter.setSprinting(false);
        float sprint = logged(sprinter, src);
        helper.assertTrue(Math.abs(sprint / walk - 1.5f) < 0.05f, "a sprint charge paid " + sprint + " vs " + walk);

        Mob short4 = zombie(helper, 3, 2);
        ranUp(charger, 4);
        float before = logged(charger, src);
        punch(charger, short4, 1f);
        helper.assertTrue(logged(charger, src) == before, "4 blocks paid a charge (it needs 5)");

        Mob capped = zombie(helper, 2, 3);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(ChargerEvents.payCharge(charger, capped, 6, false) > 0f, "charge " + (i + 1) + " paid nothing");
        }
        helper.assertTrue(ChargerEvents.payCharge(charger, capped, 6, false) == 0f, "one mob paid a 4th charge");
        cast.done();
    }

    // ---- XP source 3: Spearhead kills ----------------------------------------------------------

    /** A melee kill with a friend behind and a mob ahead pays; alone, with the friend ahead, or by arrow it does not. */
    @GameTest(template = T, batch = "charger_spearhead_kill")
    public static void spearheadKillsNeedAFriendBehind(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(4.5, 4.5);
        ServerPlayer friend = cast.player(4.5, -4.5);
        String src = ChargerEvents.SOURCE_SPEARHEAD;
        zombie(helper, 4, 12); // a hostile ahead that keeps Spearhead up

        helper.assertTrue(ChargerEvents.inSpearhead(charger), "a friend behind and a zombie ahead is not Spearhead");
        punch(charger, zombie(helper, 4, 6), 100f);
        float paid = logged(charger, src);
        helper.assertTrue(paid > 0f, "a Spearhead kill paid nothing");

        Mob byArrow = zombie(helper, 5, 6);
        Arrow arrow = new Arrow(helper.getLevel(), charger);
        byArrow.hurt(byArrow.damageSources().arrow(arrow, charger), 100f);
        helper.assertTrue(logged(charger, src) == paid, "an arrow kill paid Spearhead (melee only)");

        cast.move(friend, 4.5, 9.5);
        helper.assertTrue(!ChargerEvents.inSpearhead(charger), "a friend ahead of you counted as behind");
        punch(charger, zombie(helper, 3, 6), 100f);
        helper.assertTrue(logged(charger, src) == paid, "a kill with the friend ahead paid Spearhead");

        cast.move(friend, 40.5, -40.5);
        punch(charger, zombie(helper, 5, 7), 100f);
        helper.assertTrue(logged(charger, src) == paid, "a kill with nobody behind paid Spearhead");
        cast.done();
    }

    /** The spot rule: one spot pays 16 times in 5 minutes, then nothing until you move on. */
    @GameTest(template = T, batch = "charger_spot")
    public static void oneSpotPaysSixteenTimes(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(0.5, 0.5);
        for (int i = 0; i < 16; i++) {
            Mob mob = zombie(helper, 1 + i % 4, 2 + i / 4);
            helper.assertTrue(ChargerEvents.payFirstBlood(charger, mob) > 0f, "first blood " + (i + 1) + " paid nothing");
        }
        Mob seventeenth = zombie(helper, 2, 7);
        helper.assertTrue(ChargerEvents.payFirstBlood(charger, seventeenth) == 0f, "a 17th payout in one spot paid (mob farm)");
        helper.assertTrue(ChargerEvents.payCharge(charger, seventeenth, 8, true) == 0f, "the spot rule missed a charge hit");
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(18, 2, 18));
        helper.assertTrue(ChargerEvents.payFirstBlood(charger, far) > 0f, "a new spot 19 blocks away paid nothing");
        cast.done();
    }

    // ---- Spearhead's effects, Trust the Line, Shield and Spear ---------------------------------

    /**
     * Spearhead at level 10: +10% damage and half knockback resistance; with a Guardian (level
     * 10) behind, +15% and full resistance, and first blood's shield goes to the Guardian too.
     */
    @GameTest(template = T, batch = "charger_spearhead_effects")
    public static void spearheadAndShieldAndSpear(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(4.5, 4.5);
        ServerPlayer friend = cast.player(4.5, -3.5);
        skills(charger).setLevel(Skill.CHARGER, 25);
        zombie(helper, 4, 14);

        ChargerEvents.brace(charger);
        double resist = charger.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(Math.abs(resist - 0.5) < 1e-6, "Spearhead gave " + resist + " knockback resistance, not 0.5");

        // The friend hits first, so the Charger's hits are plain ones (no first blood, no shield yet).
        Mob mob = zombie(helper, 4, 6);
        punch(friend, mob, 1f);
        float before = mob.getHealth();
        punch(charger, mob, 10f);
        float plain = before - mob.getHealth();
        helper.assertTrue(Math.abs(plain - 11f) < 0.05f, "Spearhead's hit took " + plain + ", not 11");

        skills(friend).setLevel(Skill.GUARDIAN, 10);
        ChargerEvents.brace(charger);
        resist = charger.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(resist > 0.99, "a Guardian behind did not give full knockback resistance");
        before = mob.getHealth();
        punch(charger, mob, 4f);
        float paired = before - mob.getHealth();
        helper.assertTrue(Math.abs(paired - 4.6f) < 0.05f, "Shield and Spear's hit took " + paired + ", not 4.6");

        // The shield: a first blood gives Absorption to the Charger and to the Guardian behind.
        punch(charger, zombie(helper, 3, 7), 1f);
        helper.assertTrue(charger.hasEffect(MobEffects.ABSORPTION), "first blood at level 25 gave no Absorption");
        helper.assertTrue(friend.hasEffect(MobEffects.ABSORPTION), "the Guardian behind got no Absorption");

        // Walk away: Spearhead ends and so does the resistance.
        cast.move(friend, 40.5, -40.5);
        ChargerEvents.brace(charger);
        resist = charger.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(resist < 1e-6, "Spearhead's knockback resistance stayed after it ended");
        cast.done();
    }

    /** Trust the Line cuts a friend's hit by 80% at rank 3, only in Spearhead, never a mob's. */
    @GameTest(template = T, batch = "charger_trust")
    public static void trustTheLineOnlyInSpearhead(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(4.5, 4.5);
        ServerPlayer friend = cast.player(4.5, -3.5);
        charger.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        charger.setHealth(100f);
        skills(charger).setRank(Talents.get(Skill.CHARGER, "trust_the_line"), 3);
        Mob ahead = zombie(helper, 4, 12);
        boolean pvp = helper.getLevel().getServer().isPvpAllowed();
        helper.getLevel().getServer().setPvpAllowed(true);

        charger.invulnerableTime = 0;
        charger.hurt(charger.damageSources().playerAttack(friend), 10f);
        float cut = 100f - charger.getHealth();
        helper.assertTrue(Math.abs(cut - 2f) < 0.3f, "a friend's 10 in Spearhead took " + cut + ", not 2");

        charger.setHealth(100f);
        charger.invulnerableTime = 0;
        charger.hurt(charger.damageSources().mobAttack(ahead), 10f);
        helper.assertTrue(100f - charger.getHealth() > 5f, "Trust the Line cut a mob's hit");

        ahead.discard();
        charger.setHealth(100f);
        charger.invulnerableTime = 0;
        charger.hurt(charger.damageSources().playerAttack(friend), 10f);
        helper.assertTrue(100f - charger.getHealth() > 5f, "Trust the Line cut a friend's hit outside Spearhead");
        helper.getLevel().getServer().setPvpAllowed(pvp);
        cast.done();
    }

    // ---- Sustain --------------------------------------------------------------------------------

    /**
     * From level 25: first blood's Absorption (not again inside 10 s; Crash In 3 makes it II), and
     * lifesteal only in the 5 s after a charge hit (more with Red Harvest).
     */
    @GameTest(template = T, batch = "charger_sustain")
    public static void shieldAndLifestealFromLevelTwentyFive(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer low = cast.player(0.5, 0.5);
        skills(low).setLevel(Skill.CHARGER, 24);
        punch(low, zombie(helper, 0, 2), 1f);
        helper.assertTrue(!low.hasEffect(MobEffects.ABSORPTION), "first blood at level 24 gave Absorption");

        ServerPlayer charger = cast.player(6.5, 0.5);
        skills(charger).setLevel(Skill.CHARGER, 25);
        punch(charger, zombie(helper, 6, 2), 1f);
        helper.assertTrue(charger.hasEffect(MobEffects.ABSORPTION), "first blood at level 25 gave no Absorption");
        charger.removeEffect(MobEffects.ABSORPTION);
        punch(charger, zombie(helper, 7, 2), 1f);
        helper.assertTrue(!charger.hasEffect(MobEffects.ABSORPTION), "a second shield came inside 10 s");

        ServerPlayer tough = cast.player(12.5, 0.5);
        skills(tough).setLevel(Skill.CHARGER, 25);
        skills(tough).setRank(Talents.get(Skill.CHARGER, "crash_in"), 3);
        punch(tough, zombie(helper, 12, 2), 1f);
        var absorb = tough.getEffect(MobEffects.ABSORPTION);
        helper.assertTrue(absorb != null && absorb.getAmplifier() == 1 && absorb.getDuration() > 300,
                "Crash In 3 did not give a long Absorption II");

        // Lifesteal: none without a charge; 10% after one; none once 5 s passed.
        charger.setHealth(10f);
        helper.assertTrue(ChargerEvents.lifesteal(charger, 8f) == 0f, "lifesteal without a charge");
        Mob target = zombie(helper, 6, 3);
        punch(low, target, 1f); // someone else hit it first: a plain charge hit, no first-blood bonus
        ranUp(charger, 6);
        punch(charger, target, 5f);
        float healed = charger.getHealth() - 10f;
        helper.assertTrue(Math.abs(healed - 0.5f) < 0.05f, "a 5-damage charge hit healed " + healed + ", not 0.5");
        skills(charger).setRank(Talents.get(Skill.CHARGER, "red_harvest"), 3);
        helper.assertTrue(Math.abs(ChargerEvents.lifesteal(charger, 8f) - 2f) < 0.01f, "Red Harvest 3 did not heal 25%");
        cast.done();
    }

    // ---- Breach ---------------------------------------------------------------------------------

    /** Breach on a charge hit staggers the hostile mobs in the cone ahead, not the one behind. */
    @GameTest(template = T, batch = "charger_breach")
    public static void breachStaggersTheConeAhead(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(4.5, 4.5);
        Mob target = zombie(helper, 4, 6);
        Mob beside = zombie(helper, 5, 7);
        Mob behind = zombie(helper, 4, 2);
        ProcService.forceNext(charger, Skill.CHARGER);
        ranUp(charger, 6);
        punch(charger, target, 1f);
        helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Breach did not stagger the target");
        helper.assertTrue(beside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Breach missed a mob in the cone");
        helper.assertTrue(!behind.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Breach hit a mob behind you");
        cast.done();
    }

    // ---- Charge!, Onslaught, Warbringer ---------------------------------------------------------

    /**
     * Charge!: a dash forward, Speed for the friend behind (not the one ahead), no knockback while
     * it runs, and the next target is a first blood even if someone just hit it.
     */
    @GameTest(template = T, batch = "charger_active")
    public static void chargeDashesCriesAndOpensAFirstBlood(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(4.5, 4.5);
        ServerPlayer behind = cast.player(4.5, -2.5);
        ServerPlayer ahead = cast.player(4.5, 12.5);
        skills(charger).setLevel(Skill.CHARGER, 100);
        charger.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(ActiveService.activate(charger, Skill.CHARGER) == null, "Charge! did not start");
        helper.assertTrue(charger.getDeltaMovement().z > 1.0, "Charge! did not dash forward");
        helper.assertTrue(behind.hasEffect(MobEffects.MOVEMENT_SPEED), "the friend behind got no war cry");
        helper.assertTrue(!ahead.hasEffect(MobEffects.MOVEMENT_SPEED), "the friend ahead got the war cry");
        helper.assertTrue(ChargerEvents.nextIsFirstBlood(charger), "Charge! did not promise a first blood");

        charger.setDeltaMovement(Vec3.ZERO);
        charger.knockback(2.0, 1.0, 0.0);
        helper.assertTrue(charger.getDeltaMovement().lengthSqr() < 1e-6, "Charge! did not stop knockback");

        Mob hit = zombie(helper, 3, 6);
        punch(ahead, hit, 2f);
        float before = hit.getHealth();
        punch(charger, hit, 4f);
        float loss = before - hit.getHealth();
        helper.assertTrue(loss > 5f, "Charge!'s next target was no first blood (took " + loss + ")");
        helper.assertTrue(!ChargerEvents.nextIsFirstBlood(charger), "the promise outlived the hit");
        cast.done();
    }

    /**
     * A forced first blood (Charge!) gets the damage but pays no XP on a softened mob: pressing
     * Charge! at a drop tower is no farm. A fresh mob still pays. A hit that never lands (the mob
     * is in its hurt time) leaves an entry that the tick sweep drops.
     */
    @GameTest(template = T, batch = "charger_forced")
    public static void forcedFirstBloodPaysOnlyOnAFreshMob(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(4.5, 4.5);
        PlayerSkills skills = skills(charger);
        skills.setLevel(Skill.CHARGER, ActiveService.unlockLevel() + 5);
        Mob softened = zombie(helper, 4, 6);
        softened.setHealth(softened.getMaxHealth() * 0.5f);
        helper.assertTrue(ActiveService.activate(charger, Skill.CHARGER) == null, "Charge! did not start");
        charger.setDeltaMovement(Vec3.ZERO);
        cast.move(charger, 4.5, 4.5);
        float before = softened.getHealth();
        punch(charger, softened, 2f);
        helper.assertTrue(before - softened.getHealth() > 2f, "the forced first blood hit no harder");
        helper.assertTrue(!ChargerEvents.nextIsFirstBlood(charger), "the promise outlived the hit");
        helper.assertTrue(logged(charger, ChargerEvents.SOURCE_FIRST_BLOOD) == 0f,
                "a forced first blood on a softened mob paid XP");

        Mob fresh = zombie(helper, 2, 6);
        punch(charger, fresh, 1f);
        helper.assertTrue(logged(charger, ChargerEvents.SOURCE_FIRST_BLOOD) > 0f, "a natural first blood paid nothing");

        // A second, weaker hit inside the hurt time never lands: its entry must not stay forever.
        Mob blocked = zombie(helper, 6, 6);
        punch(charger, blocked, 3f);
        blocked.hurt(blocked.damageSources().playerAttack(charger), 1f);
        helper.assertTrue(ChargerEvents.pendingCount() > 0, "the blocked hit left no entry (test setup)");
        long now = helper.getLevel().getGameTime();
        ChargerEvents.dropStalePending(now + 2);
        helper.assertTrue(ChargerEvents.pendingCount() == 0, "a stale entry survived the sweep");
        cast.done();
    }

    /** Onslaught: a kill within 3 s of a first blood makes the next target a first blood. Warbringer: first blood shortens Charge!. */
    @GameTest(template = T, batch = "charger_onslaught")
    public static void onslaughtAndWarbringer(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(0.5, 0.5);
        PlayerSkills skills = skills(charger);
        skills.setLevel(Skill.CHARGER, 100);
        skills.fillTree(Skill.CHARGER);
        Mob first = zombie(helper, 0, 2);
        punch(charger, first, 100f);
        helper.assertTrue(ChargerEvents.nextIsFirstBlood(charger), "Onslaught gave no first blood after a quick kill");

        ServerPlayer plain = cast.player(8.5, 0.5);
        skills(plain).setLevel(Skill.CHARGER, 100);
        punch(plain, zombie(helper, 8, 2), 100f);
        helper.assertTrue(!ChargerEvents.nextIsFirstBlood(plain), "a kill without Onslaught opened a first blood");

        long now = helper.getLevel().getGameTime();
        skills.beginFrenzy(Skill.CHARGER, now, now + 2400);
        long cooldown = skills.cooldownRemaining(Skill.CHARGER, now);
        punch(charger, zombie(helper, 1, 3), 1f);
        long after = skills.cooldownRemaining(Skill.CHARGER, now);
        helper.assertTrue(cooldown - after >= 60, "Warbringer took " + (cooldown - after) + " ticks off, not 60");
        cast.done();
    }

    // ---- The hooks Tactician (idea 40) uses -----------------------------------------------------

    /** A first-blood boost multiplies first blood; reopenFirstBlood reopens one; chargerFighting finds the Charger. */
    @GameTest(template = T, batch = "charger_hooks")
    public static void theHooksForHammerAndAnvil(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer charger = cast.player(0.5, 0.5);
        ServerPlayer archer = cast.player(6.5, 0.5);
        skills(charger).setLevel(Skill.CHARGER, 1);
        Mob marked = zombie(helper, 1, 2);
        ChargerEvents.FirstBloodBoost boost = (player, target) -> target == marked ? 2.0 : 1.0;
        ChargerEvents.addFirstBloodBoost(boost);
        try {
            // The archer's hit would end the quiet; the mark reopens the first blood.
            punch(archer, marked, 1f);
            marked.setHealth(marked.getMaxHealth());
            ChargerEvents.reopenFirstBlood(marked);
            float before = marked.getHealth();
            punch(charger, marked, 3f);
            float loss = before - marked.getHealth();
            helper.assertTrue(loss > 5.9f, "a boosted, reopened first blood took " + loss + ", not about 6");
            helper.assertTrue(ChargerEvents.chargerFighting(marked) == charger, "chargerFighting missed the Charger");
            // The archer's hit was this mob's first blood, so a reopened one pays no XP (once per mob).
            helper.assertTrue(logged(charger, ChargerEvents.SOURCE_FIRST_BLOOD) == 0f,
                    "a reopened first blood paid XP twice on one mob");
        } finally {
            ChargerEvents.removeFirstBloodBoost(boost);
        }
        Mob other = zombie(helper, 3, 3);
        helper.assertTrue(ChargerEvents.chargerFighting(other) == null, "an untouched mob is fighting a Charger");
        cast.done();
    }
}
