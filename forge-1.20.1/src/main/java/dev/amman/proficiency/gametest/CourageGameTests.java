package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.CourageEvents;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillProcEvent;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Idea 37, Courage, in a real world: each XP source, each anti-farm rule, Rally, Stand Your
 * Ground, the tree's mechanics and Fearless Heart. The arithmetic is in CourageMathTest.
 *
 * <p>Silverfish are the foes of choice: 8 health and 1 attack, so one of them is never a
 * "stronger foe" for a 20-health player, and only the crowd makes the odds.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CourageGameTests {

    private CourageGameTests() {
    }

    /** The "empty" template's floor is at relative y 1, so everything stands at y 2. */
    private static ServerPlayer fighter(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        clearSpawnProtection(player);
        CourageEvents.forget(player.getUUID());
        return player;
    }

    private static Mob foe(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at, ServerPlayer target) {
        Mob mob = helper.spawnWithNoFreeWill(type, at);
        if (target != null) {
            mob.setTarget(target);
        }
        return mob;
    }

    private static float courageLogged(ServerPlayer player) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == Skill.COURAGE.ordinal()) {
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

    /**
     * XP source 1 (damage dealt) and its anti-farm rules: no fight window, even odds, a mob that
     * cannot see you, a cow, a player, a used-up mob and creative mode all pay nothing; the same
     * hit while outnumbered pays.
     */
    @GameTest(template = "empty")
    public static void aHitPaysCourageOnlyAgainstTheOdds(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob first = foe(helper, EntityType.SILVERFISH, new BlockPos(0, 2, 3), player);

        helper.assertTrue(CourageEvents.payHit(player, first, 4f) == 0f,
                "a hit with no mob having hurt you (a mob farm) paid Courage");
        CourageEvents.noteHurtByMob(player);
        helper.assertTrue(CourageEvents.inFight(player), "noteHurtByMob did not open the fight window");
        helper.assertTrue(CourageEvents.payHit(player, first, 4f) == 0f,
                "one silverfish against a full-health player (even odds) paid Courage");

        // A second one behind iron bars is after you but never attacked you: not in your fight.
        for (BlockPos wall : new BlockPos[] {new BlockPos(2, 2, 3), new BlockPos(4, 2, 3),
                new BlockPos(3, 2, 2), new BlockPos(3, 2, 4), new BlockPos(3, 3, 3)}) {
            helper.setBlock(wall, Blocks.IRON_BARS);
        }
        Mob watcher = foe(helper, EntityType.SILVERFISH, new BlockPos(3, 2, 3), player);
        helper.assertTrue(CourageEvents.foes(player, first) == 1,
                "a mob that only watches counted as a foe: " + CourageEvents.foes(player, first));
        helper.assertTrue(CourageEvents.payHit(player, first, 4f) == 0f,
                "a foe that never attacked you made the odds");
        watcher.discard();

        Mob second = foe(helper, EntityType.SILVERFISH, new BlockPos(2, 2, 0), player);
        Mob third = foe(helper, EntityType.SILVERFISH, new BlockPos(1, 2, 1), player);
        CourageEvents.noteHurtBy(player, first, second, third);
        helper.assertTrue(CourageEvents.foes(player, null) == 3, "three silverfish that attacked you are not 3 foes");
        float paid = CourageEvents.payHit(player, first, 4f);
        helper.assertTrue(paid > 0f, "a hit while outnumbered 3 to 1 paid no Courage");
        helper.assertTrue(courageLogged(player) > 0f, "the Courage hit has no log line");

        // The silverfish has 8 health: 4 are paid, 4 more may pay, then it is used up.
        helper.assertTrue(CourageEvents.payHit(player, first, 4f) > 0f, "the rest of its health bar paid nothing");
        helper.assertTrue(CourageEvents.payHit(player, first, 4f) == 0f,
                "a mob paid for more than its own health bar (a healing trapped mob would be a farm)");

        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 0));
        helper.assertTrue(CourageEvents.payHit(player, cow, 4f) == 0f, "hitting a cow paid Courage");
        ServerPlayer friend = MockPlayers.make(helper);
        helper.assertTrue(CourageEvents.payHit(player, friend, 4f) == 0f, "hitting a player paid Courage");

        Mob fourth = foe(helper, EntityType.SILVERFISH, new BlockPos(4, 2, 1), player);
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(CourageEvents.payHit(player, fourth, 4f) == 0f, "a creative player earned Courage");
        helper.succeed();
    }

    /** The real damage pipeline: an outnumbered hit through LivingDamageEvent pays; a mob's hit opens the window. */
    @GameTest(template = "empty")
    public static void aMobHitOpensTheFightAndARealHitPays(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob a = foe(helper, EntityType.SILVERFISH, new BlockPos(0, 2, 3), player);
        Mob b = foe(helper, EntityType.SILVERFISH, new BlockPos(2, 2, 0), player);
        foe(helper, EntityType.SILVERFISH, new BlockPos(1, 2, 1), player);

        a.hurt(player.damageSources().playerAttack(player), 2f);
        helper.assertTrue(courageLogged(player) == 0f, "a real hit paid Courage before any mob hurt the player");

        helper.assertTrue(player.hurt(player.damageSources().mobAttack(a), 2f), "the silverfish's hit did not land");
        helper.assertTrue(CourageEvents.inFight(player), "a mob's hit did not open the fight window");
        helper.assertTrue(CourageEvents.foes(player, null) == 1, "the mob that hit you is not counted as a foe");
        // A fresh mob: the first one is still in its hurt cooldown.
        b.hurt(player.damageSources().playerAttack(player), 2f);
        helper.assertTrue(courageLogged(player) > 0f, "a real outnumbered hit paid no Courage");

        // A death ends the fight: the next life is not in it.
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().mobAttack(a)));
        helper.assertFalse(CourageEvents.inFight(player), "a death left the fight window open");
        helper.succeed();
    }

    /** XP source 2 (kills) and Rally: an outnumbered kill pays and rallies; a lone kill does neither. */
    @GameTest(template = "empty")
    public static void anOutnumberedKillPaysAndRallies(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        ProficiencyAttachments.of(player).setLevel(Skill.COURAGE, 30);
        Mob victim = foe(helper, EntityType.SILVERFISH, new BlockPos(0, 2, 3), player);
        Mob other = foe(helper, EntityType.SILVERFISH, new BlockPos(2, 2, 0), player);
        CourageEvents.noteHurtBy(player, other);

        ProcService.forceNext(player, Skill.COURAGE);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(victim, player.damageSources().playerAttack(player)));
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST) && player.hasEffect(MobEffects.MOVEMENT_SPEED),
                "an outnumbered kill with a landed proc gave no Rally");
        helper.assertTrue(courageLogged(player) > 0f, "an outnumbered kill paid no Courage");

        ServerPlayer lone = fighter(helper);
        ProficiencyAttachments.of(lone).setLevel(Skill.COURAGE, 30);
        Mob single = foe(helper, EntityType.SILVERFISH, new BlockPos(4, 2, 4), lone);
        CourageEvents.noteHurtByMob(lone);
        ProcService.forceNext(lone, Skill.COURAGE);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(single, lone.damageSources().playerAttack(lone)));
        helper.assertFalse(lone.hasEffect(MobEffects.DAMAGE_BOOST), "a kill with one foe gave Rally");
        helper.assertTrue(courageLogged(lone) == 0f, "an even kill paid Courage");
        ProcService.forget(lone.getUUID());
        helper.succeed();
    }

    /** The passive counts foes; Stand Your Ground stops knockback and adds damage per nearby enemy. */
    @GameTest(template = "empty")
    public static void thePassiveAndStandYourGround(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.COURAGE, 100);
        Mob target = foe(helper, EntityType.SILVERFISH, new BlockPos(0, 2, 3), player);
        double alone = CourageEvents.damageMultiplier(player, target);
        helper.assertTrue(Math.abs(alone - 1.0) < 1e-6, "one foe should add nothing, got " + alone);
        for (BlockPos at : new BlockPos[] {new BlockPos(2, 2, 0), new BlockPos(1, 2, 1),
                new BlockPos(4, 2, 0), new BlockPos(4, 2, 4)}) {
            CourageEvents.noteHurtBy(player, foe(helper, EntityType.SILVERFISH, at, player));
        }
        double crowded = CourageEvents.damageMultiplier(player, target);
        helper.assertTrue(Math.abs(crowded - 1.4) < 1e-6, "five foes at level 100 should add 40%, got " + crowded);

        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(1.0, 1.0, 0.0);
        helper.assertTrue(player.getDeltaMovement().lengthSqr() > 0, "the mock player cannot be knocked back at all");

        helper.assertTrue(ActiveService.activate(player, Skill.COURAGE) == null, "Stand Your Ground did not start");
        double standing = CourageEvents.damageMultiplier(player, target);
        helper.assertTrue(standing > crowded * 1.2, "Stand Your Ground added too little: " + standing + " vs " + crowded);
        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(1.0, 1.0, 0.0);
        helper.assertTrue(player.getDeltaMovement().lengthSqr() == 0, "Stand Your Ground let knockback through");
        helper.succeed();
    }

    /** Hold the Line, Giant Slayer, Unshaken, Challenge, Spoils of Valor, Rallying Cry, Lionheart. */
    @GameTest(template = "empty")
    public static void theCourageTreeMechanics(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        ServerPlayer plain = fighter(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.COURAGE, 100);
        skills.fillTree(Skill.COURAGE);

        // Hold the Line: three foes after you, 15% less from their hits.
        Mob biter = foe(helper, EntityType.ZOMBIE, new BlockPos(0, 2, 3), player);
        Mob s1 = foe(helper, EntityType.SILVERFISH, new BlockPos(2, 2, 0), player);
        Mob s2 = foe(helper, EntityType.SILVERFISH, new BlockPos(1, 2, 1), player);
        CourageEvents.noteHurtBy(player, s1, s2);
        // Compared with a plain player's loss from the same hit, so the difficulty does not matter.
        player.setHealth(20f);
        plain.setHealth(20f);
        player.hurt(player.damageSources().mobAttack(biter), 8f);
        plain.hurt(plain.damageSources().mobAttack(biter), 8f);
        float held = 20f - player.getHealth();
        float full = 20f - plain.getHealth();
        helper.assertTrue(full > 0 && Math.abs(held / full - 0.85f) < 0.01f,
                "Hold the Line 3 should take 15% off: " + held + " vs " + full);

        // Giant Slayer: an elite (40+ max health) takes 24% more.
        Mob elite = foe(helper, EntityType.ZOMBIE, new BlockPos(4, 2, 4), null);
        elite.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50);
        Mob small = foe(helper, EntityType.ZOMBIE, new BlockPos(4, 2, 0), null);
        double vsElite = CourageEvents.damageMultiplier(player, elite);
        double vsSmall = CourageEvents.damageMultiplier(player, small);
        helper.assertTrue(Math.abs(vsElite / vsSmall - 1.24) < 1e-3, "Giant Slayer: " + vsElite + " vs " + vsSmall);

        // Unshaken: a mob's Slowness never lands at rank 3; your own potion still does.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200), biter);
        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Unshaken let a mob's Slowness land");
        plain.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200), biter);
        helper.assertTrue(plain.hasEffect(MobEffects.WEAKNESS), "Weakness was blocked without Unshaken");
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Unshaken blocked Weakness nobody's mob gave");

        // Challenge: hit a mob that is after a friend 3 blocks away and it turns on you.
        plain.setPos(player.getX() + 3, player.getY(), player.getZ());
        small.setTarget(plain);
        small.hurt(player.damageSources().playerAttack(player), 1f);
        helper.assertTrue(small.getTarget() == player, "Challenge did not pull the mob off the friend");

        // Spoils of Valor: bare hands (1 attack) against a zombie (3) is a stronger foe: +6 health.
        player.setHealth(10f);
        CourageEvents.forget(player.getUUID());
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(elite, player.damageSources().playerAttack(player)));
        helper.assertTrue(player.getHealth() >= 15.9f, "Spoils of Valor healed to " + player.getHealth());

        // Rallying Cry and Lionheart: an outnumbered kill with a Rally buffs the friend within 8
        // blocks and takes 5 s off Stand Your Ground's cooldown.
        helper.assertTrue(ActiveService.activate(player, Skill.COURAGE) == null, "Stand Your Ground did not start");
        long before = ActiveService.cooldownRemaining(player, Skill.COURAGE);
        plain.removeAllEffects();
        CourageEvents.noteHurtBy(player, s1, s2);
        ProcService.forceNext(player, Skill.COURAGE);
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(biter, player.damageSources().playerAttack(player)));
        long after = ActiveService.cooldownRemaining(player, Skill.COURAGE);
        helper.assertTrue(before - after >= 100, "Lionheart took " + (before - after) + " ticks off, not 100");
        helper.assertTrue(plain.hasEffect(MobEffects.DAMAGE_BOOST), "Rallying Cry did not reach the friend");
        helper.succeed();
    }

    /**
     * Fix round: low health alone is not uneven odds; the full-tree passive never passes its own
     * bonus, however many foes; Challenge only pulls on a hit that landed.
     */
    @GameTest(template = "empty")
    public static void courageLimitsHold(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob lone = foe(helper, EntityType.SILVERFISH, new BlockPos(0, 2, 3), player);
        CourageEvents.noteHurtBy(player, lone);
        player.setHealth(2f);
        helper.assertTrue(CourageEvents.payHit(player, lone, 2f) == 0f,
                "one silverfish against a player at 2 health paid Courage (low health alone is even odds)");

        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.COURAGE, 100);
        skills.fillTree(Skill.COURAGE);
        double passive = SkillService.bonus(player, Skill.COURAGE);
        for (int i = 0; i < 8; i++) {
            CourageEvents.noteHurtBy(player, foe(helper, EntityType.SILVERFISH, new BlockPos(1 + i % 4, 2, i / 4), player));
        }
        double crowded = CourageEvents.damageMultiplier(player, lone);
        helper.assertTrue(crowded <= 1.0 + passive + 1e-6,
                "nine foes gave " + crowded + ", more than the passive's own bonus " + passive);

        ServerPlayer friend = fighter(helper);
        friend.setPos(player.getX() + 2, player.getY(), player.getZ());
        Mob shielded = foe(helper, EntityType.ZOMBIE, new BlockPos(4, 2, 4), friend);
        shielded.setInvulnerable(true);
        shielded.hurt(player.damageSources().playerAttack(player), 1f);
        helper.assertTrue(shielded.getTarget() == friend, "Challenge pulled a mob with a hit that never landed");
        shielded.setInvulnerable(false);
        shielded.hurt(player.damageSources().playerAttack(player), 1f);
        helper.assertTrue(shielded.getTarget() == player, "Challenge did not pull the mob with a hit that landed");
        helper.succeed();
    }

    /** Fearless Heart (Endurance + Courage): a Grit gives Strength I, and only with the synergy. */
    @GameTest(template = "empty")
    public static void fearlessHeartTurnsGritIntoStrength(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        ServerPlayer plain = fighter(helper);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        skills.setLevel(Skill.ENDURANCE, 100);
        skills.fillTree(Skill.ENDURANCE);
        skills.setLevel(Skill.COURAGE, 100);
        skills.fillTree(Skill.COURAGE);
        MinecraftForge.EVENT_BUS.post(new SkillProcEvent(player, Skill.ENDURANCE, null, null, true));
        MinecraftForge.EVENT_BUS.post(new SkillProcEvent(plain, Skill.ENDURANCE, null, null, true));
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST), "Fearless Heart gave no Strength on a Grit");
        helper.assertFalse(plain.hasEffect(MobEffects.DAMAGE_BOOST), "a Grit gave Strength without Fearless Heart");
        helper.succeed();
    }
}
