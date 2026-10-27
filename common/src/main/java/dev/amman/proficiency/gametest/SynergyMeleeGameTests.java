package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.ChargerEvents;
import dev.amman.proficiency.event.CombatEvents;
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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingShieldBlockEvent;

/**
 * Synergies 2, Batch B1: the eight melee and fight synergies, each through the real damage and
 * death events with a control case (the same setup one need short of waking the synergy). Each
 * test is its own batch.
 */
public final class SynergyMeleeGameTests {

    private static final String T = "proficiency:empty";

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public SynergyMeleeGameTests() {
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, double x, double z, ItemStack held) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
        player.setYRot(0f);
        player.setYHeadRot(0f);
        clearSpawnProtection(player);
        ChargerEvents.forget(player.getUUID());
        ProcService.forget(player.getUUID());
        CombatEvents.forget(player.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
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
            ChargerEvents.forget(player.getUUID());
            CombatEvents.forget(player.getUUID());
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A zombie with no armour and a deep health bar, so damage reads straight off its health. */
    private static Mob dummy(GameTestHelper helper, double x, double z) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BlockPos.containing(x, 2, z));
        var armour = mob.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.setBaseValue(0);
        }
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        mob.setHealth(200f);
        return mob;
    }

    private static void punch(ServerPlayer player, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().playerAttack(player), amount);
    }

    private static float loss(LivingEntity mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    private static void crouch(ServerPlayer player) {
        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
    }

    // ---- Turn and Thrust ------------------------------------------------------------------------

    /** Parries until one lands (5% a rank, so rank 3 is 15%); returns whether one did. */
    private static boolean parryOnce(ServerPlayer player, Mob attacker) {
        for (int i = 0; i < 400; i++) {
            player.setHealth(20f);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().mobAttack(attacker), 2f);
            if (player.getHealth() >= 20f) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = T, batch = "syn_turn_and_thrust")
    public static void turnAndThrustMakesTheNextSwordHitCertain(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_SWORD));
        ServerPlayer without = player(helper, 3.5, 0.5, new ItemStack(Items.IRON_SWORD));
        rank(with, Skill.SWORDS, "parry", 3);
        rank(with, Skill.BLOCKING, "counter", 5);
        rank(without, Skill.SWORDS, "parry", 3);
        Mob foe = dummy(helper, 0, 4);
        helper.assertTrue(parryOnce(with, foe), "no parry landed in 400 tries");
        helper.assertTrue(parryOnce(without, foe), "no control parry landed in 400 tries");
        helper.assertFalse(ProcService.fire(without, Skill.SWORDS),
                "a parry without the synergy set up a certain sword hit");
        helper.assertTrue(ProcService.fire(with, Skill.SWORDS),
                "a parry with Turn and Thrust did not make the next sword hit a Perfect Strike");
        finish(helper, with, without);
    }

    // ---- Single Combat --------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_single_combat")
    public static void singleCombatIgnoresHostilesThatAreNotAfterYou(GameTestHelper helper) {
        ServerPlayer duelist = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_SWORD));
        rank(duelist, Skill.SWORDS, "duelist", 1);
        rank(duelist, Skill.COURAGE, "challenge", 3);
        Mob busy = dummy(helper, 4, 4);
        busy.setTarget(helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(5, 2, 1)));

        Mob a = dummy(helper, 1, 3);
        punch(duelist, a, 4f);
        float with = loss(a);

        // Control: one need short, so the crowd counts and the duel is off.
        rank(duelist, Skill.COURAGE, "challenge", 2);
        Mob b = dummy(helper, 2, 3);
        punch(duelist, b, 4f);
        float without = loss(b);
        helper.assertTrue(Math.abs(without - 4f) < 0.05f, "the control hit took " + without + ", not 4");
        helper.assertTrue(Math.abs(with - 5.2f) < 0.05f, "a duel beside a busy zombie took " + with + ", not 5.2");

        // A hostile that is after you still breaks the duel, synergy or not.
        rank(duelist, Skill.COURAGE, "challenge", 3);
        busy.setTarget(duelist);
        Mob c = dummy(helper, 3, 3);
        punch(duelist, c, 4f);
        helper.assertTrue(Math.abs(loss(c) - 4f) < 0.05f, "a hostile hunting the player did not break the duel");
        finish(helper, duelist);
    }

    // ---- Reaver's Rush --------------------------------------------------------------------------

    private static void chargeInto(ServerPlayer player, Mob target) {
        long now = player.level().getGameTime();
        ChargerEvents.trail(player).clear();
        ChargerEvents.trail(player).add(now - 10, player.getX(), player.getY(), player.getZ() - 6);
        punch(player, target, 1f);
    }

    @GameTest(template = T, batch = "syn_reavers_rush")
    public static void reaversRushMakesTheNextAxeHitACleave(GameTestHelper helper) {
        ServerPlayer rusher = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_AXE));
        rank(rusher, Skill.AXES, "sunder", 3);
        rank(rusher, Skill.CHARGER, "breakthrough", 5);
        chargeInto(rusher, dummy(helper, 0, 3));
        Mob first = dummy(helper, 4, 3);
        Mob bystander = dummy(helper, 5, 3);
        punch(rusher, first, 4f);
        helper.assertTrue(loss(bystander) > 0.5f, "the axe hit after a charge did not Cleave (" + loss(bystander) + ")");

        // Control: the same charge and hit one need short.
        rank(rusher, Skill.AXES, "sunder", 2);
        chargeInto(rusher, dummy(helper, 0, 3));
        Mob first2 = dummy(helper, 8, 3);
        Mob bystander2 = dummy(helper, 9, 3);
        punch(rusher, first2, 4f);
        helper.assertTrue(loss(bystander2) == 0f, "a Cleave happened without Reaver's Rush");
        finish(helper, rusher);
    }

    // ---- Falling Hammer -------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_falling_hammer")
    public static void fallingHammerAddsAStompToTheShockwave(GameTestHelper helper) {
        ServerPlayer faller = player(helper, 0.5, 0.5, new ItemStack(Items.MACE));
        rank(faller, Skill.MACES, "meteor", 3);
        rank(faller, Skill.JUMPING, "stomp", 3);
        Mob under = dummy(helper, 0.5, 0.5);
        faller.fallDistance = 4f;
        ProcService.forceNext(faller, Skill.MACES);
        punch(faller, under, 4f);
        float with = loss(under);
        // The stomp runs at the end of the tick.
        helper.runAfterDelay(3, () -> {
            float withStomp = loss(under);
            // Control: Stomp 2 does not wake the synergy, so the Shockwave stands alone.
            rank(faller, Skill.JUMPING, "stomp", 2);
            faller.setPos(helper.absoluteVec(new Vec3(4.5, 2, 0.5)));
            Mob control = dummy(helper, 4.5, 0.5);
            faller.fallDistance = 4f;
            ProcService.forceNext(faller, Skill.MACES);
            punch(faller, control, 4f);
            helper.runAfterDelay(3, () -> {
                float plain = loss(control);
                helper.assertTrue(plain > 0f, "the control mace hit did nothing");
                // The synergy also grants Maces +10% proc power, so the Shockwave itself is up to
                // 10% bigger; everything above that is the 6 damage Stomp.
                float extra = withStomp - plain;
                helper.assertTrue(extra >= 5.9f && extra <= 6f + 0.12f * plain,
                        "Falling Hammer added " + extra + " (" + with + " at once), not a 6 damage Stomp");
                finish(helper, faller);
            });
        });
    }

    // ---- Earthbound Oath ------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_earthbound_oath")
    public static void earthboundOathTurnsTheSmashedOntoYou(GameTestHelper helper) {
        ServerPlayer smasher = player(helper, 0.5, 0.5, new ItemStack(Items.MACE));
        rank(smasher, Skill.MACES, "cratermaker", 1);
        rank(smasher, Skill.GUARDIAN, "hold_fast", 2);
        net.minecraft.world.entity.npc.Villager friend =
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(6, 2, 1));
        Mob main = dummy(helper, 1, 3);
        Mob near = dummy(helper, 2, 3);
        main.setTarget(friend);
        near.setTarget(friend);
        smasher.fallDistance = 8f;
        punch(smasher, main, 4f);
        helper.assertTrue(main.getTarget() == smasher && near.getTarget() == smasher,
                "the smashed mobs did not turn on the player");

        // Control: Hold Fast 1 leaves the synergy asleep, and the mobs keep their quarrel.
        rank(smasher, Skill.GUARDIAN, "hold_fast", 1);
        Mob main2 = dummy(helper, 1, 5);
        Mob near2 = dummy(helper, 2, 5);
        main2.setTarget(friend);
        near2.setTarget(friend);
        smasher.fallDistance = 8f;
        punch(smasher, main2, 4f);
        helper.assertTrue(main2.getTarget() == friend && near2.getTarget() == friend,
                "a smash without Earthbound Oath changed targets");
        finish(helper, smasher);
    }

    // ---- Nerve Strike ---------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_nerve_strike")
    public static void nerveStrikeNumbsFromBehindWhileCrouched(GameTestHelper helper) {
        ServerPlayer sneak = player(helper, 0.5, 0.5, ItemStack.EMPTY);
        rank(sneak, Skill.SNEAKING, "backstab", 2);
        rank(sneak, Skill.UNARMED, "one_inch_punch", 1);
        crouch(sneak);

        Mob behind = dummy(helper, 0.5, 3);
        behind.setYBodyRot(0f); // faces +z, away from the player
        punch(sneak, behind, 2f);
        MobEffectInstance slow = behind.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getAmplifier() == 3, "no Slowness IV from behind");
        helper.assertTrue(behind.hasEffect(MobEffects.WEAKNESS), "no Weakness from behind");

        Mob facing = dummy(helper, 2.5, 3);
        facing.setYBodyRot(180f); // faces the player
        punch(sneak, facing, 2f);
        helper.assertFalse(facing.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a punch from the front numbed");

        rank(sneak, Skill.SNEAKING, "backstab", 1);
        Mob control = dummy(helper, 4.5, 3);
        control.setYBodyRot(0f);
        punch(sneak, control, 2f);
        helper.assertFalse(control.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "numbed without the synergy");
        finish(helper, sneak);
    }

    // ---- Pavise ---------------------------------------------------------------------------------

    private static void blocked(ServerPlayer player, Mob attacker) {
        NeoForge.EVENT_BUS.post(new LivingShieldBlockEvent(player,
                new DamageContainer(player.damageSources().mobAttack(attacker), 4f), true));
    }

    @GameTest(template = T, batch = "syn_pavise")
    public static void paviseLoadsTheNextCrossbowShot(GameTestHelper helper) {
        ServerPlayer with = player(helper, 0.5, 0.5, new ItemStack(Items.SHIELD));
        ServerPlayer without = player(helper, 3.5, 0.5, new ItemStack(Items.SHIELD));
        rank(with, Skill.BLOCKING, "thorns", 3);
        rank(with, Skill.CROSSBOWS, "kickback", 3);
        rank(without, Skill.BLOCKING, "thorns", 3);
        Mob foe = dummy(helper, 0, 4);
        blocked(without, foe);
        helper.assertFalse(ProcService.fire(without, Skill.CROSSBOWS), "a block without Pavise loaded a shot");
        blocked(with, foe);
        helper.assertTrue(ProcService.fire(with, Skill.CROSSBOWS), "a blocked hit did not load a Double Load");
        // Spent, and a second block inside 5 s does not queue another.
        blocked(with, foe);
        helper.assertFalse(ProcService.fire(with, Skill.CROSSBOWS), "Pavise re-armed inside its 5 s");
        finish(helper, with, without);
    }

    // ---- Colossus Bane --------------------------------------------------------------------------

    @GameTest(template = T, batch = "syn_colossus_bane")
    public static void colossusBaneAlwaysRalliesOnAGiantKill(GameTestHelper helper) {
        ServerPlayer slayer = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_SWORD));
        rank(slayer, Skill.BEASTSLAYING, "giant_slayer", 3);
        rank(slayer, Skill.COURAGE, "giant_slayer", 3);

        Mob small = dummy(helper, 1, 3);
        small.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        small.setHealth(1f);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(small, slayer.damageSources().playerAttack(slayer)));
        helper.assertFalse(slayer.hasEffect(MobEffects.DAMAGE_BOOST), "a 20 health kill rallied");

        Mob giant = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(3, 2, 3));
        giant.setHealth(1f);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(giant, slayer.damageSources().playerAttack(slayer)));
        helper.assertTrue(slayer.hasEffect(MobEffects.DAMAGE_BOOST), "killing a ravager did not Rally");

        // Control: one need short.
        slayer.removeAllEffects();
        rank(slayer, Skill.COURAGE, "giant_slayer", 2);
        Mob giant2 = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(5, 2, 3));
        giant2.setHealth(1f);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(giant2, slayer.damageSources().playerAttack(slayer)));
        helper.assertFalse(slayer.hasEffect(MobEffects.DAMAGE_BOOST), "a Rally without Colossus Bane");
        finish(helper, slayer);
    }
}
