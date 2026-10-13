package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.GuardianEvents;
import dev.amman.proficiency.event.TacticianEvents;
import dev.amman.proficiency.event.TalentRangedEvents;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import dev.amman.proficiency.Proficiency;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Synergies 2, Batch B2: the six ranged and protecting synergies, each with a control case (the
 * same setup one need short of waking the synergy). Each test is its own batch.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SynergyRangedGameTests {

    private static final String T = "empty";

    private SynergyRangedGameTests() {
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, y, z)));
        player.setYRot(0f);
        player.setYHeadRot(0f);
        clearSpawnProtection(player);
        ProcService.forget(player.getUUID());
        GuardianEvents.forget(player.getUUID());
        return player;
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

    private static void rank(ServerPlayer player, Skill skill, String talent, int rank) {
        ProficiencyAttachments.of(player).setRank(Talents.get(skill, talent), rank);
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ProcService.forget(player.getUUID());
            GuardianEvents.forget(player.getUUID());
            TacticianEvents.forget(player.getUUID(), player.server);
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A zombie with no armour and a deep health bar, so damage reads straight off its health. */
    private static Mob dummy(GameTestHelper helper, double x, double z, float health) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos.containing(x, 2, z));
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(0);
        }
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        mob.setHealth(health);
        return mob;
    }

    private static float loss(LivingEntity mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    /** An arrow the player owns; a crossbow bolt when {@code crossbow}. Not added to the world. */
    private static Arrow arrow(ServerPlayer shooter, boolean crossbow) {
        Arrow arrow = new Arrow(shooter.serverLevel(), shooter);
        arrow.setShotFromCrossbow(crossbow);
        return arrow;
    }

    private static void shoot(ServerPlayer shooter, Arrow arrow, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().arrow(arrow, shooter), amount);
    }

    // ---- Shared Quarry --------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_shared_quarry")
    public static void sharedQuarryAlsoCallsTheTacticianMark(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 4.5, 2, 0.5);
        rank(with, Skill.ARCHERY, "hunters_mark", 3);
        rank(with, Skill.TACTICIAN, "painted_target", 1);
        rank(without, Skill.ARCHERY, "hunters_mark", 3);
        Mob marked = dummy(helper, 1, 5, 200f);
        Mob control = dummy(helper, 5, 5, 200f);

        ProcService.forceNext(without, Skill.ARCHERY);
        shoot(without, arrow(without, false), control, 4f);
        helper.assertTrue(TacticianEvents.liveMark(control) == null,
                "a Hunter's Mark without the synergy placed a Tactician mark");

        ProcService.forceNext(with, Skill.ARCHERY);
        shoot(with, arrow(with, false), marked, 4f);
        TacticianEvents.Mark mark = TacticianEvents.liveMark(marked);
        helper.assertTrue(mark != null, "Hunter's Mark did not also place the Tactician mark");
        helper.assertTrue(mark.marker().equals(with.getUUID()), "the Tactician mark belongs to somebody else");
        finish(helper, with, without);
    }

    // ---- Hanging Shot ---------------------------------------------------------------------------

    private static void loose(GameTestHelper helper, ServerPlayer player, boolean fullDraw) {
        Arrow arrow = arrow(player, false);
        arrow.setCritArrow(fullDraw);
        helper.getLevel().addFreshEntity(arrow);
        arrow.discard();
    }

    @GameTest(template = T, batch = "syn_hanging_shot")
    public static void hangingShotGivesSlowFallingOncePerAirtime(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 7, 0.5);
        ServerPlayer without = player(helper, 4.5, 7, 0.5);
        rank(with, Skill.ARCHERY, "skyhunter", 2);
        rank(with, Skill.JUMPING, "feather_weight", 3);
        rank(without, Skill.ARCHERY, "skyhunter", 2);
        rank(without, Skill.JUMPING, "feather_weight", 2);

        loose(helper, without, true);
        helper.assertFalse(without.hasEffect(MobEffects.SLOW_FALLING), "a full draw without the synergy gave Slow Falling");

        loose(helper, with, false);
        helper.assertFalse(with.hasEffect(MobEffects.SLOW_FALLING), "a quick shot (no full draw) gave Slow Falling");

        loose(helper, with, true);
        MobEffectInstance slow = with.getEffect(MobEffects.SLOW_FALLING);
        helper.assertTrue(slow != null && slow.getDuration() <= 30 && slow.getDuration() > 20,
                "a mid-air full draw did not give 1.5 s of Slow Falling (" + slow + ")");

        // The same airtime: the effect is not given again.
        with.removeEffect(MobEffects.SLOW_FALLING);
        loose(helper, with, true);
        helper.assertFalse(with.hasEffect(MobEffects.SLOW_FALLING), "a second shot in the same airtime refreshed Slow Falling");
        finish(helper, with, without);
    }

    // ---- Dragonbolt -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_dragonbolt")
    public static void dragonboltHitsABossHarderAndMakesItGlow(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 2, 0.5);
        ServerPlayer without = player(helper, 4.5, 2, 0.5);
        rank(with, Skill.CROSSBOWS, "ballista", 1);
        rank(with, Skill.BEASTSLAYING, "boss_bane", 1);
        rank(without, Skill.CROSSBOWS, "ballista", 1);
        // Over the 150 health backstop, so the mob counts as a boss.
        Mob boss = dummy(helper, 1, 5, 400f);
        Mob controlBoss = dummy(helper, 5, 5, 400f);
        Mob smallFry = dummy(helper, 2, 5, 100f);

        ProcService.forceNext(without, Skill.CROSSBOWS);
        shoot(without, arrow(without, true), controlBoss, 10f);
        TalentRangedEvents.flushDragonbolt(without);
        helper.assertFalse(controlBoss.hasEffect(MobEffects.GLOWING), "a Ballista boss hit glowed without the synergy");
        float plain = loss(controlBoss);
        helper.assertTrue(plain > 9f, "the control bolt did only " + plain);

        ProcService.forceNext(with, Skill.CROSSBOWS);
        shoot(with, arrow(with, true), boss, 10f);
        helper.assertTrue(boss.hasEffect(MobEffects.GLOWING), "the boss did not glow");
        MobEffectInstance glow = boss.getEffect(MobEffects.GLOWING);
        helper.assertTrue(glow.getDuration() > 80 && glow.getDuration() <= 100, "glow lasted " + glow.getDuration());
        float first = loss(boss);
        TalentRangedEvents.flushDragonbolt(with);
        float extra = loss(boss) - first;
        helper.assertTrue(extra >= 2.0f && extra <= 3.5f, "the Dragonbolt hit added " + extra + ", not about 2.5");

        // Not a boss: the Ballista still fires, the synergy queues and glows nothing.
        ProcService.forceNext(with, Skill.CROSSBOWS);
        shoot(with, arrow(with, true), smallFry, 10f);
        float shot = loss(smallFry);
        TalentRangedEvents.flushDragonbolt(with);
        helper.assertFalse(smallFry.hasEffect(MobEffects.GLOWING), "an ordinary mob glowed");
        helper.assertTrue(loss(smallFry) == shot, "an ordinary mob took a Dragonbolt hit (" + shot + " then " + loss(smallFry) + ")");
        finish(helper, with, without);
    }

    // ---- First Responder ------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_first_responder")
    public static void firstResponderSpeedsYouToAFriendInTrouble(GameTestHelper helper) {
        ServerPlayer runner = player(helper, 0.5, 2, 0.5);
        ServerPlayer plain = player(helper, 4.5, 2, 0.5);
        ServerPlayer friend = player(helper, 0.5, 2, 15.5);
        rank(runner, Skill.RUNNING, "windwalker", 1);
        rank(runner, Skill.GUARDIAN, "watchful_eye", 5);
        rank(plain, Skill.RUNNING, "windwalker", 1);
        runner.setSprinting(true);
        plain.setSprinting(true);
        plain.setPos(helper.absoluteVec(new Vec3(0.5, 2, 4.5)));

        friend.setHealth(4f);
        GuardianEvents.tickSynergies(plain);
        helper.assertFalse(plain.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed without the synergy");
        GuardianEvents.tickSynergies(runner);
        MobEffectInstance speed = runner.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(speed != null && speed.getAmplifier() == 1 && speed.getDuration() <= 40,
                "no Speed II for 2 s while a friend 15 blocks away was at 4/20 (" + speed + ")");

        // Walking: no. A healthy friend: no. Healed, it lapses and is not given again.
        runner.removeEffect(MobEffects.MOVEMENT_SPEED);
        runner.setSprinting(false);
        GuardianEvents.tickSynergies(runner);
        helper.assertFalse(runner.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed while walking");
        runner.setSprinting(true);
        friend.setHealth(20f);
        GuardianEvents.tickSynergies(runner);
        helper.assertFalse(runner.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed for a friend at full health");
        finish(helper, runner, plain, friend);
    }

    // ---- Lifeguard ------------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_lifeguard")
    public static void lifeguardGivesAGaspingFriendWaterBreathingOncePerMinute(GameTestHelper helper) {
        ServerPlayer guard = player(helper, 0.5, 2, 0.5);
        ServerPlayer plain = player(helper, 0.5, 2, 20.5);
        ServerPlayer friend = player(helper, 3.5, 2, 0.5);
        ServerPlayer far = player(helper, 0.5, 2, 12.5);
        rank(guard, Skill.SWIMMING, "current_rider", 3);
        rank(guard, Skill.GUARDIAN, "warding", 5);
        rank(plain, Skill.SWIMMING, "current_rider", 3);
        // Control: a medic-less player beside a friend who is gasping.
        ServerPlayer gasper = player(helper, 3.5, 2, 20.5);
        gasper.setAirSupply(50);
        GuardianEvents.tickSynergies(plain);
        helper.assertFalse(gasper.hasEffect(MobEffects.WATER_BREATHING), "Water Breathing without the synergy");

        // Out of reach and not low enough: no.
        far.setAirSupply(50);
        friend.setAirSupply(200);
        GuardianEvents.tickSynergies(guard);
        helper.assertFalse(far.hasEffect(MobEffects.WATER_BREATHING), "a friend 12 blocks away got Water Breathing");
        helper.assertFalse(friend.hasEffect(MobEffects.WATER_BREATHING), "a friend with plenty of air got Water Breathing");

        friend.setAirSupply(50);
        GuardianEvents.tickSynergies(guard);
        MobEffectInstance breath = friend.getEffect(MobEffects.WATER_BREATHING);
        helper.assertTrue(breath != null && breath.getDuration() <= 100 && breath.getDuration() > 80,
                "the gasping friend did not get 5 s of Water Breathing (" + breath + ")");

        // Once a minute: gasping again straight away gets nothing.
        friend.removeEffect(MobEffects.WATER_BREATHING);
        friend.setAirSupply(50);
        GuardianEvents.tickSynergies(guard);
        helper.assertFalse(friend.hasEffect(MobEffects.WATER_BREATHING), "Lifeguard fired twice inside a minute");
        finish(helper, guard, plain, friend, far, gasper);
    }

    // ---- Field Medic ----------------------------------------------------------------------------

    private static ThrownPotion healingSplash(ServerPlayer thrower, ServerPlayer at) {
        ThrownPotion potion = new ThrownPotion(at.serverLevel(), thrower);
        potion.setItem(net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                new ItemStack(Items.SPLASH_POTION), net.minecraft.world.item.alchemy.Potions.HEALING));
        potion.setPos(at.getX(), at.getY(), at.getZ());
        at.serverLevel().addFreshEntity(potion);
        return potion;
    }

    @GameTest(template = T, batch = "syn_field_medic")
    public static void fieldMedicCuresAHarmfulEffectOnTheFriendHealed(GameTestHelper helper) {
        ServerPlayer medic = player(helper, 0.5, 2, 0.5);
        ServerPlayer plain = player(helper, 0.5, 2, 20.5);
        ServerPlayer friend = player(helper, 3.5, 2, 0.5);
        ServerPlayer control = player(helper, 3.5, 2, 20.5);
        rank(medic, Skill.ALCHEMY, "panacea", 1);
        rank(medic, Skill.GUARDIAN, "heartshare", 2);
        rank(plain, Skill.ALCHEMY, "panacea", 1);
        for (ServerPlayer hurt : new ServerPlayer[] {friend, control}) {
            hurt.setHealth(10f);
            hurt.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
            hurt.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
        }

        ThrownPotion controlSplash = healingSplash(plain, control);
        control.heal(4f);
        helper.assertTrue(control.hasEffect(MobEffects.POISON) && control.hasEffect(MobEffects.WEAKNESS),
                "a healing splash without the synergy cured an effect");
        controlSplash.discard();

        ThrownPotion splash = healingSplash(medic, friend);
        friend.heal(4f);
        helper.assertTrue(friend.hasEffect(MobEffects.POISON) != friend.hasEffect(MobEffects.WEAKNESS),
                "Field Medic did not cure exactly one harmful effect");
        helper.assertTrue(friend.hasEffect(MobEffects.POISON), "Field Medic cured Poison, not the longest effect (Weakness)");

        // At most once per 2 s per friend: a second heal straight away cures nothing more.
        friend.heal(1f);
        helper.assertTrue(friend.hasEffect(MobEffects.POISON), "Field Medic cured twice inside 2 s");
        splash.discard();

        // Your own Regeneration or a heal with no splash in the air is not a medic's.
        control.removeEffect(MobEffects.POISON);
        control.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        control.heal(2f);
        helper.assertTrue(control.hasEffect(MobEffects.POISON), "a plain heal cured an effect");
        finish(helper, medic, plain, friend, control);
    }
}
