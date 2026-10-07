package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.ChargerEvents;
import dev.amman.proficiency.event.TacticianEvents;
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
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.ProjectileImpactEvent;
import dev.amman.proficiency.platform.EntityData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Idea 40, Tactician, in a real world with mock players: each XP source through the real damage
 * and death events, each anti-farm rule, the passive, Called Shot's mark (who is told, how long it
 * lives, the red team, the friends' bonus), Clear Line, Suppressing Fire, Hammer and Anvil,
 * Covering Fire and the tree's mechanics. The arithmetic is in TacticianMathTest.
 *
 * <p>Every player faces +z (yaw 0). Each test is its own batch, and every player a test makes
 * leaves the server at the end. A shot is an arrow the player owns, through the real damage
 * pipeline, without flying.
 */
public final class TacticianGameTests implements FabricGameTest {

    /**
     * The mod's own empty template. NeoForge adds the "proficiency:" namespace itself (a full id
     * here crashes it). The Fabric port must write "proficiency:empty": Fabric reads a bare name
     * as minecraft:empty.
     */
    private static final String T = "proficiency:empty";

    /** Fabric instantiates the entrypoint class itself. */
    public TacticianGameTests() {
    }

    /** The players one test made, to send them away at the end. */
    private static final class Cast {
        final GameTestHelper helper;
        final List<ServerPlayer> players = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(helper.getLevel().players())) {
                if (other.position().distanceTo(origin) < 96) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            clearMobs();
        }

        /** Leftover mobs outside the 5-block template would count as targets or company. */
        void clearMobs() {
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class,
                    new net.minecraft.world.phys.AABB(origin, origin).inflate(64))) {
                mob.discard();
            }
        }

        /** A survival player at relative (x, 2, z), facing +z. */
        ServerPlayer player(double x, double z) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            player.setYRot(0f);
            player.setYHeadRot(0f);
            clearSpawnProtection(player);
            TacticianEvents.forget(player.getUUID(), player.server);
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
                TacticianEvents.forget(player.getUUID(), player.server);
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

    /** A zombie with a lot of health, for damage checks. */
    private static Mob bigZombie(GameTestHelper helper, int x, int z) {
        Mob mob = zombie(helper, x, z);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200f);
        return mob;
    }

    private static Arrow arrow(ServerPlayer shooter) {
        return new Arrow(shooter.serverLevel(), shooter, new ItemStack(Items.ARROW), null);
    }

    /** A ranged hit: an arrow the player owns, through the real damage pipeline. */
    private static void shoot(ServerPlayer shooter, LivingEntity target, float amount) {
        shoot(shooter, arrow(shooter), target, amount);
    }

    private static void shoot(ServerPlayer shooter, Arrow arrow, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().arrow(arrow, shooter), amount);
    }

    /** A melee hit from the player's own hand. */
    private static void punch(ServerPlayer player, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().playerAttack(player), amount);
    }

    /** A mob's hit on a player, which is what "the mob hurt a friend" remembers. */
    private static void bite(Mob mob, ServerPlayer player, float amount) {
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().mobAttack(mob), amount);
    }

    private static float logged(ServerPlayer player, String source) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.TACTICIAN.ordinal() && source.equals(entry.source())) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static PlayerSkills skills(ServerPlayer player) {
        return ProficiencyAttachments.of(player);
    }

    private static void rank(ServerPlayer player, String talent, int rank) {
        skills(player).setRank(Talents.get(Skill.TACTICIAN, talent), rank);
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 0.05f;
    }

    /**
     * Arrows also train Archery, and its passive (+0.8% per level) creeps into arrow damage as the
     * test goes, so arrow damage is checked within 2%.
     */
    private static boolean nearShot(float actual, float expected) {
        return Math.abs(actual - expected) <= expected * 0.02f;
    }

    // ---- XP source 1: support ------------------------------------------------------------------

    /**
     * A ranged hit on a mob that is after a friend pays; a mob after nobody or only you, a melee
     * hit and a creative player do not; one mob pays for 40 damage at most.
     */
    @GameTest(template = T, batch = "tactician_support")
    public static void supportPaysOnlyForAMobAfterAFriend(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(4.5, 0.5);
        String src = TacticianEvents.SOURCE_SUPPORT;

        Mob hunting = zombie(helper, 1, 4);
        hunting.setTarget(friend);
        shoot(tactician, hunting, 4f);
        float once = logged(tactician, src);
        helper.assertTrue(once > 0f, "a shot at a zombie after a friend paid no support");

        Mob idle = zombie(helper, 2, 4);
        shoot(tactician, idle, 4f);
        Mob onMe = zombie(helper, 3, 4);
        onMe.setTarget(tactician);
        shoot(tactician, onMe, 4f);
        helper.assertTrue(logged(tactician, src) == once, "a mob after nobody or only you paid support");

        Mob punched = zombie(helper, 1, 5);
        punched.setTarget(friend);
        punch(tactician, punched, 4f);
        helper.assertTrue(logged(tactician, src) == once, "a melee hit paid support (ranged only)");

        Mob capped = bigZombie(helper, 2, 5);
        capped.setTarget(friend);
        helper.assertTrue(TacticianEvents.paySupport(tactician, capped, 20f) > 0f, "the first 20 paid nothing");
        helper.assertTrue(TacticianEvents.paySupport(tactician, capped, 30f) > 0f, "the next 20 paid nothing");
        helper.assertTrue(TacticianEvents.paySupport(tactician, capped, 5f) == 0f, "one mob paid past 40 damage");

        tactician.setGameMode(GameType.CREATIVE);
        float before = logged(tactician, src);
        Mob creative = zombie(helper, 3, 5);
        creative.setTarget(friend);
        shoot(tactician, creative, 4f);
        helper.assertTrue(logged(tactician, src) == before, "a creative player earned Tactician");
        cast.done();
    }

    // ---- XP source 2: rescue -------------------------------------------------------------------

    /**
     * A ranged kill of a mob that hurt a friend in the last 5 s pays. Not a mob that hurt nobody,
     * not one whose hit was 10 s ago, not a melee kill, not with the friend 40 blocks away.
     */
    @GameTest(template = T, batch = "tactician_rescue")
    public static void rescueKillsNeedAMobThatJustHurtAFriend(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(4.5, 0.5);
        friend.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        friend.setHealth(100f);
        String src = TacticianEvents.SOURCE_RESCUE;

        Mob biter = zombie(helper, 1, 4);
        bite(biter, friend, 1f);
        shoot(tactician, biter, 100f);
        float paid = logged(tactician, src);
        helper.assertTrue(paid > 0f, "a ranged kill of a mob that just hurt a friend paid no rescue");

        shoot(tactician, zombie(helper, 2, 4), 100f);
        helper.assertTrue(logged(tactician, src) == paid, "a kill of a mob that hurt nobody paid rescue");

        Mob old = zombie(helper, 3, 4);
        bite(old, friend, 1f);
        EntityData.of(old).putLong("proficiency_tactician_hurt_at", helper.getLevel().getGameTime() - 200);
        shoot(tactician, old, 100f);
        helper.assertTrue(logged(tactician, src) == paid, "a hit 10 s ago still paid rescue");

        Mob melee = zombie(helper, 1, 5);
        bite(melee, friend, 1f);
        punch(tactician, melee, 100f);
        helper.assertTrue(logged(tactician, src) == paid, "a melee kill paid rescue (ranged only)");

        cast.move(friend, 40.5, 0.5);
        Mob far = zombie(helper, 2, 5);
        bite(far, friend, 1f);
        shoot(tactician, far, 100f);
        helper.assertTrue(logged(tactician, src) == paid, "a rescue with the friend 40 blocks away paid");
        cast.done();
    }

    // ---- XP source 3: Overwatch ----------------------------------------------------------------

    /**
     * A ranged hit with a friend between you and a mob fighting them pays by distance. Not with
     * the friend to the side, not on a mob after nobody, not from under 6 blocks, 3 per mob.
     */
    @GameTest(template = T, batch = "tactician_overwatch")
    public static void overwatchNeedsAFriendInFront(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(4.5, 0.5);
        ServerPlayer friend = cast.player(4.5, 6.5);
        String src = TacticianEvents.SOURCE_OVERWATCH;

        Mob ahead = zombie(helper, 4, 14);
        ahead.setTarget(friend);
        helper.assertTrue(TacticianEvents.overwatch(tactician, ahead), "a friend straight in front is not Overwatch");
        shoot(tactician, ahead, 1f);
        float paid = logged(tactician, src);
        helper.assertTrue(paid > 0f, "an Overwatch shot paid nothing");

        cast.move(friend, 12.5, 0.5);
        Mob aside = zombie(helper, 5, 14);
        aside.setTarget(friend);
        helper.assertTrue(!TacticianEvents.overwatch(tactician, aside), "a friend off to the side counted as the front line");
        shoot(tactician, aside, 1f);
        helper.assertTrue(logged(tactician, src) == paid, "a shot with no friend in front paid Overwatch");

        cast.move(friend, 4.5, 6.5);
        Mob idle = zombie(helper, 3, 14);
        shoot(tactician, idle, 1f);
        helper.assertTrue(logged(tactician, src) == paid, "a mob after nobody paid Overwatch");

        Mob capped = bigZombie(helper, 4, 15);
        capped.setTarget(friend);
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(TacticianEvents.payOverwatch(tactician, capped, 14) > 0f, "Overwatch " + (i + 1) + " paid nothing");
        }
        helper.assertTrue(TacticianEvents.payOverwatch(tactician, capped, 14) == 0f, "one mob paid a 4th Overwatch");

        Mob close = zombie(helper, 3, 15);
        close.setTarget(friend);
        helper.assertTrue(TacticianEvents.payOverwatch(tactician, close, 4.5) == 0f, "a shot from 4.5 blocks paid Overwatch");
        cast.done();
    }

    /** The spot rule: one spot pays 48 base XP in 5 minutes, then nothing until you move on. */
    @GameTest(template = T, batch = "tactician_spot")
    public static void oneSpotPaysFortyEight(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(4.5, 0.5);
        // 20 damage is 3 base XP: 16 of them fill the spot.
        for (int i = 0; i < 16; i++) {
            Mob mob = bigZombie(helper, 1 + i % 4, 2 + i / 4);
            mob.setTarget(friend);
            helper.assertTrue(TacticianEvents.paySupport(tactician, mob, 20f) > 0f, "payout " + (i + 1) + " paid nothing");
        }
        Mob full = bigZombie(helper, 2, 7);
        full.setTarget(friend);
        helper.assertTrue(TacticianEvents.paySupport(tactician, full, 20f) == 0f, "a full spot paid again (mob farm)");
        Mob far = bigZombie(helper, 20, 20);
        far.setTarget(friend);
        helper.assertTrue(TacticianEvents.paySupport(tactician, far, 20f) > 0f, "a new spot 25 blocks away paid nothing");
        cast.done();
    }

    // ---- The passive ----------------------------------------------------------------------------

    /** At level 100 a ranged hit on a mob after a friend is 25% harder; on one after nobody it is not. */
    @GameTest(template = T, batch = "tactician_passive")
    public static void thePassiveHitsMobsAfterSomeoneElse(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(4.5, 0.5);
        skills(tactician).setLevel(Skill.TACTICIAN, 100);
        Mob hunting = bigZombie(helper, 1, 4);
        hunting.setTarget(friend);
        shoot(tactician, hunting, 8f);
        float loss = 200f - hunting.getHealth();
        helper.assertTrue(nearShot(loss, 10f), "a shot at a mob after a friend took " + loss + ", not 10");
        Mob idle = bigZombie(helper, 2, 4);
        shoot(tactician, idle, 8f);
        loss = 200f - idle.getHealth();
        helper.assertTrue(nearShot(loss, 8f), "a shot at a mob after nobody took " + loss + ", not 8");
        cast.done();
    }

    // ---- Called Shot: the mark ------------------------------------------------------------------

    /**
     * Called Shot marks the mob: it glows, joins the red team, and the marker and a friend 6
     * blocks off are told, a player 60 blocks off is not. A friend deals 15% more to it and the
     * marker does not. After 8 s the mark, the team and the icon's audience are gone.
     */
    @GameTest(template = T, batch = "tactician_mark_life", timeoutTicks = 400)
    public static void theMarkIsSharedWithFriendsNearAndEndsAfterEightSeconds(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(6.5, 0.5);
        ServerPlayer far = cast.player(60.5, 0.5);
        Mob mob = bigZombie(helper, 2, 4);
        ProcService.forceNext(tactician, Skill.TACTICIAN);
        shoot(tactician, mob, 1f);

        TacticianEvents.Mark mark = TacticianEvents.liveMark(mob);
        helper.assertTrue(mark != null, "Called Shot left no mark");
        helper.assertTrue(mark.receivers().contains(tactician.getUUID()), "the marker was not told");
        helper.assertTrue(mark.receivers().contains(friend.getUUID()), "a friend 6 blocks off was not told");
        helper.assertTrue(!mark.receivers().contains(far.getUUID()), "a player 60 blocks off was told");
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), "the marked mob does not glow");
        helper.assertTrue(TacticianEvents.inRedTeam(mob), "the marked mob is not in the red team");
        helper.assertTrue(mob.getTeamColor() != 0xFFFFFF, "the mark glows white, like Hunter's Mark");

        float before = mob.getHealth();
        punch(friend, mob, 10f);
        float byFriend = before - mob.getHealth();
        helper.assertTrue(near(byFriend, 11.5f), "a friend's 10 on the mark took " + byFriend + ", not 11.5");
        before = mob.getHealth();
        punch(tactician, mob, 10f);
        float byMarker = before - mob.getHealth();
        helper.assertTrue(near(byMarker, 10f), "the marker's own 10 took " + byMarker + " (the bonus is for friends)");

        helper.runAfterDelay(175, () -> {
            helper.assertTrue(TacticianEvents.liveMark(mob) == null, "the mark outlived its 8 s");
            boolean kept = TacticianEvents.marks().stream().anyMatch(m -> m.mob().equals(mob.getUUID()));
            helper.assertTrue(!kept, "the sweep kept an ended mark (the icon would stay)");
            helper.assertTrue(!TacticianEvents.inRedTeam(mob), "the mob stayed in the red team");
            cast.done();
        });
    }

    /** A marked mob's death ends the mark at once: no mark, no red team, no audience left. */
    @GameTest(template = T, batch = "tactician_mark_death")
    public static void theMarkEndsWhenTheMobDies(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        cast.player(6.5, 0.5);
        Mob mob = zombie(helper, 2, 4);
        ProcService.forceNext(tactician, Skill.TACTICIAN);
        shoot(tactician, mob, 1f);
        helper.assertTrue(TacticianEvents.liveMark(mob) != null, "Called Shot left no mark");
        shoot(tactician, mob, 100f);
        helper.assertTrue(!mob.isAlive(), "the mob survived (test setup)");
        boolean kept = TacticianEvents.marks().stream().anyMatch(m -> m.mob().equals(mob.getUUID()));
        helper.assertTrue(!kept, "a dead mob kept its mark");
        helper.assertTrue(!TacticianEvents.inRedTeam(mob), "a dead mob stayed in the red team");
        cast.done();
    }

    // ---- Clear Line --------------------------------------------------------------------------------

    /** Clear Line: your arrow flies through a friend, and nothing you shoot hurts them; others' arrows still do. */
    @GameTest(template = T, batch = "tactician_clear_line")
    public static void clearLineShootsThroughFriends(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(0.5, 4.5);
        ServerPlayer other = cast.player(6.5, 0.5);
        rank(tactician, "clear_line", 1);
        boolean pvp = helper.getLevel().getServer().isPvpAllowed();
        helper.getLevel().getServer().setPvpAllowed(true);
        try {
            ProjectileImpactEvent mine = new ProjectileImpactEvent(arrow(tactician), new EntityHitResult(friend));
            NeoForge.EVENT_BUS.post(mine);
            helper.assertTrue(mine.isCanceled(), "Clear Line did not let the arrow through the friend");
            ProjectileImpactEvent theirs = new ProjectileImpactEvent(arrow(other), new EntityHitResult(friend));
            NeoForge.EVENT_BUS.post(theirs);
            helper.assertTrue(!theirs.isCanceled(), "an arrow from a player without Clear Line went through");

            float health = friend.getHealth();
            friend.invulnerableTime = 0;
            friend.hurt(friend.damageSources().arrow(arrow(tactician), tactician), 4f);
            helper.assertTrue(friend.getHealth() == health, "a Clear Line arrow still hurt a friend");
            friend.invulnerableTime = 0;
            friend.hurt(friend.damageSources().arrow(arrow(other), other), 4f);
            helper.assertTrue(friend.getHealth() < health, "an arrow from a player without Clear Line did not hurt");
        } finally {
            helper.getLevel().getServer().setPvpAllowed(pvp);
        }
        cast.done();
    }

    // ---- Suppressing Fire ------------------------------------------------------------------------

    /** Suppressing Fire: each shot slows, pulls the mob off the friend onto you, and marks it. */
    @GameTest(template = T, batch = "tactician_active")
    public static void suppressingFireSlowsPullsAndMarks(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(4.5, 0.5);
        skills(tactician).setLevel(Skill.TACTICIAN, ActiveService.unlockLevel() + 5);
        helper.assertTrue(ActiveService.activate(tactician, Skill.TACTICIAN) == null, "Suppressing Fire did not start");
        Mob mob = bigZombie(helper, 2, 5);
        mob.setTarget(friend);
        shoot(tactician, mob, 1f);
        helper.assertTrue(mob.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Suppressing Fire did not slow");
        helper.assertTrue(mob.getTarget() == tactician, "Suppressing Fire did not pull the mob off the friend");
        helper.assertTrue(TacticianEvents.liveMark(mob) != null, "Suppressing Fire's shot was not a Called Shot");
        helper.assertTrue(TacticianEvents.engagedFriend(tactician, mob) == friend,
                "a mob pulled off a friend stopped counting as fighting them");
        cast.done();
    }

    // ---- Hammer and Anvil (with Charger) -----------------------------------------------------------

    /**
     * A Charger's first blood on a mob a Tactician marked hits x1.5 harder (Opening reopened the
     * first blood), and a Tactician's hits on a mob fighting a Charger pay double XP.
     */
    @GameTest(template = T, batch = "tactician_hammer")
    public static void hammerAndAnvil(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        // The Tactician stands ahead of the Charger, not behind, so Spearhead stays out of the numbers.
        ServerPlayer charger = cast.player(4.5, 4.5);
        ServerPlayer tactician = cast.player(8.5, 8.5);
        skills(charger).setLevel(Skill.CHARGER, 10);
        rank(tactician, "opening", 1);

        Mob marked = bigZombie(helper, 4, 6);
        ProcService.forceNext(tactician, Skill.TACTICIAN);
        shoot(tactician, marked, 1f);
        helper.assertTrue(TacticianEvents.liveMark(marked) != null, "no mark (test setup)");
        float before = marked.getHealth();
        punch(charger, marked, 4f);
        float loss = before - marked.getHealth();
        // First blood x(1 + Charger's level-10 passive) x Hammer and Anvil 1.5 x the mark's 1.15.
        float expected = 4f * (float) (1.0 + SkillService.bonus(charger, Skill.CHARGER)) * 1.5f * 1.15f;
        helper.assertTrue(Math.abs(loss - expected) < 0.1f, "a first blood on the mark took " + loss + ", not " + expected);

        // Double XP: two fresh Tacticians at mirrored spots, one mob fighting the Charger, one not.
        ServerPlayer left = cast.player(0.5, 0.5);
        ServerPlayer right = cast.player(12.5, 0.5);
        ServerPlayer friend = cast.player(6.5, -6.5);
        Mob fought = bigZombie(helper, 2, 3);
        Mob plain = bigZombie(helper, 10, 3);
        fought.setTarget(friend);
        plain.setTarget(friend);
        punch(charger, fought, 1f);
        helper.assertTrue(TacticianEvents.fightingACharger(left, fought), "a mob the Charger just hit is not fighting a Charger");
        float doubled = TacticianEvents.paySupport(left, fought, 10f);
        float single = TacticianEvents.paySupport(right, plain, 10f);
        helper.assertTrue(single > 0f && Math.abs(doubled / single - 2f) < 0.1f,
                "Hammer and Anvil paid " + doubled + " vs " + single);

        skills(charger).setLevel(Skill.CHARGER, 9);
        helper.assertTrue(!TacticianEvents.fightingACharger(left, fought), "a Charger under level 10 counted");
        cast.done();
    }

    // ---- Covering Fire (with Guardian) -------------------------------------------------------------

    /** A Tactician's shot on a mob after a Guardian makes its hits on players 25% softer; under level 10, no. */
    @GameTest(template = T, batch = "tactician_covering")
    public static void coveringFireSoftensTheMobOnTheGuardian(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer guardian = cast.player(4.5, 0.5);
        guardian.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        guardian.setHealth(100f);
        skills(guardian).setLevel(Skill.GUARDIAN, 10);
        skills(tactician).setLevel(Skill.TACTICIAN, 10);

        Mob plain = zombie(helper, 5, 3);
        plain.setTarget(guardian);
        bite(plain, guardian, 8f);
        float plainLoss = 100f - guardian.getHealth();

        guardian.setHealth(100f);
        Mob covered = zombie(helper, 4, 3);
        covered.setTarget(guardian);
        helper.assertTrue(TacticianEvents.coveringFire(tactician, covered), "Covering Fire did not cover (test setup)");
        bite(covered, guardian, 8f);
        float coveredLoss = 100f - guardian.getHealth();
        helper.assertTrue(plainLoss > 0f && Math.abs(coveredLoss / plainLoss - 0.75f) < 0.03f,
                "a covered mob hit for " + coveredLoss + " vs " + plainLoss);

        skills(tactician).setLevel(Skill.TACTICIAN, 9);
        Mob low = zombie(helper, 3, 3);
        low.setTarget(guardian);
        helper.assertTrue(!TacticianEvents.coveringFire(tactician, low), "a Tactician under level 10 covered");
        cast.done();
    }

    // ---- The tree's mechanics --------------------------------------------------------------------

    /** Headshot 3 is +30% at the head and nothing at the feet; Crossfire 3 is +9% per friend in the line. */
    @GameTest(template = T, batch = "tactician_aim")
    public static void headshotAndCrossfire(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(4.5, 0.5);
        rank(tactician, "headshot", 3);

        Mob head = bigZombie(helper, 4, 8);
        Arrow high = arrow(tactician);
        high.setPos(head.getX(), head.getEyeY(), head.getZ() - 1.0);
        high.setDeltaMovement(0, 0, 0);
        shoot(tactician, high, head, 10f);
        float loss = 200f - head.getHealth();
        helper.assertTrue(nearShot(loss, 13f), "a headshot took " + loss + ", not 13");

        Mob feet = bigZombie(helper, 6, 8);
        Arrow low = arrow(tactician);
        low.setPos(feet.getX(), feet.getY() + 0.2, feet.getZ() - 1.0);
        low.setDeltaMovement(0, 0, 0);
        shoot(tactician, low, feet, 10f);
        loss = 200f - feet.getHealth();
        helper.assertTrue(nearShot(loss, 10f), "a shot at the feet took " + loss + ", not 10");

        rank(tactician, "headshot", 0);
        rank(tactician, "crossfire", 3);
        cast.player(4.5, 5.5);
        Mob behindFriend = bigZombie(helper, 4, 12);
        Arrow body = arrow(tactician);
        body.setPos(behindFriend.getX(), behindFriend.getY() + 0.2, behindFriend.getZ() - 1.0);
        body.setDeltaMovement(0, 0, 0);
        shoot(tactician, body, behindFriend, 10f);
        loss = 200f - behindFriend.getHealth();
        helper.assertTrue(nearShot(loss, 10.9f), "Crossfire with one friend in the line took " + loss + ", not 10.9");
        cast.done();
    }

    /**
     * Quartermaster 3 gives the arrow back on your kill of your mark; Focus Fire moves the mark to
     * the nearest hostile; Field Marshal takes 3 s off Suppressing Fire when a friend kills it.
     */
    @GameTest(template = T, batch = "tactician_mark_talents")
    public static void quartermasterFocusFireAndFieldMarshal(GameTestHelper helper) {
        Cast cast = new Cast(helper);
        ServerPlayer tactician = cast.player(0.5, 0.5);
        ServerPlayer friend = cast.player(6.5, 0.5);
        rank(tactician, "quartermaster", 3);
        rank(tactician, "focus_fire", 1);

        Mob first = zombie(helper, 2, 5);
        Mob next = zombie(helper, 4, 6);
        TacticianEvents.mark(tactician, first);
        int arrows = tactician.getInventory().countItem(Items.ARROW);
        shoot(tactician, first, 100f);
        helper.assertTrue(tactician.getInventory().countItem(Items.ARROW) == arrows + 1,
                "Quartermaster 3 gave no arrow back");
        helper.assertTrue(TacticianEvents.liveMark(next) != null, "Focus Fire did not move the mark");

        rank(tactician, "quartermaster", 0);
        rank(tactician, "focus_fire", 0);
        rank(tactician, "field_marshal", 1);
        PlayerSkills skills = skills(tactician);
        long now = helper.getLevel().getGameTime();
        skills.beginFrenzy(Skill.TACTICIAN, now, now + 2400);
        long cooldown = skills.cooldownRemaining(Skill.TACTICIAN, now);
        punch(friend, next, 100f);
        long after = skills.cooldownRemaining(Skill.TACTICIAN, now);
        helper.assertTrue(cooldown - after >= 60, "Field Marshal took " + (cooldown - after) + " ticks off, not 60");
        cast.done();
    }
}
