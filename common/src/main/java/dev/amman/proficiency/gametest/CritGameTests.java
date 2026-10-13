package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.CombatEvents;
import dev.amman.proficiency.event.CritEvents;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.XpLog;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Critical hits (docs/CRITS-SPEC.md) through the real {@code Player.attack}, so the loader's crit
 * hook (NeoForge's event, the Fabric mixin) is what is tested, not a shortcut around it. Every
 * case has its control: the same swing without the crit or without the talent.
 */
public final class CritGameTests {

    private static final String T = "proficiency:empty";
    /** The bare-hand damage; the plain hits below measure everything else. */
    private static final float FIST = 1f;

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public CritGameTests() {
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, double x, double z, ItemStack held) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
        player.setYRot(0f);
        player.setYHeadRot(0f);
        setInt(ServerPlayer.class, player, "spawnInvulnerableTime", 0);
        forget(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    private static void forget(ServerPlayer player) {
        ProcService.forget(player.getUUID());
        CombatEvents.forget(player.getUUID());
        CritEvents.forget(player.getUUID());
    }

    private static void setInt(Class<?> owner, Object target, String name, int value) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not set " + name, e);
        }
    }

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

    private static void rank(ServerPlayer player, Skill skill, String talent, int rank) {
        ProficiencyAttachments.of(player).setRank(Talents.get(skill, talent), rank);
    }

    /** A fully charged swing: the attack timer is well past its cooldown. */
    private static void charge(ServerPlayer player) {
        setInt(LivingEntity.class, player, "attackStrengthTicker", 200);
    }

    /** A vanilla crit swing: charged, falling, off the ground, not sprinting. */
    private static void jumpSwing(ServerPlayer player, LivingEntity target) {
        charge(player);
        player.setSprinting(false);
        player.setOnGround(false);
        player.fallDistance = 1f;
        target.invulnerableTime = 0;
        player.attack(target);
    }

    /** A charged swing from the ground, sprinting or not. Never a vanilla crit. */
    private static void groundSwing(ServerPlayer player, LivingEntity target, boolean sprinting) {
        charge(player);
        player.setOnGround(true);
        player.fallDistance = 0f;
        player.setSprinting(sprinting);
        target.invulnerableTime = 0;
        player.attack(target);
    }

    /**
     * What a plain charged hit with the held item does, measured on a fresh mob at (x, z). A mock
     * player never ticks, so the held item's attack damage may not be applied: the tests compare
     * against this, not against the item's tooltip number.
     */
    private static float plainHit(GameTestHelper helper, ServerPlayer player, double x, double z) {
        Mob mob = dummy(helper, x, z);
        groundSwing(player, mob, false);
        float base = loss(mob);
        helper.assertTrue(base > 0f, "a plain hit did no damage");
        return base;
    }

    private static float loss(LivingEntity mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 0.05f;
    }

    /** The newest XP log line of a skill: its encoded factors, or null when it earned nothing. */
    private static String newestFactors(ServerPlayer player, Skill skill) {
        XpLog log = SkillService.xpLog(player);
        if (log == null || log.entries(skill.ordinal()).isEmpty()) {
            return null;
        }
        return log.entries(skill.ordinal()).get(0).factors();
    }

    private static void finish(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            forget(player);
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // ---- Tests ----------------------------------------------------------------------------------

    @GameTest(template = T, batch = "crit_vanilla")
    public static void aJumpCritHitsHarderAndPaysCritXp(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_SWORD));
        float SWORD = plainHit(helper, player, 7.5, 1.5);
        Mob crit = dummy(helper, 0.5, 1.5);
        jumpSwing(player, crit);
        helper.assertTrue(near(loss(crit), SWORD * 1.5f), "a jump crit took " + loss(crit) + ", wanted 1.5 x " + SWORD);
        String factors = newestFactors(player, Skill.SWORDS);
        helper.assertTrue(factors != null && factors.contains("crit=1.500"),
                "the crit hit's XP line carries no crit factor: " + factors);

        // Control: the same swing from the ground is a plain hit and pays plain XP.
        Mob plain = dummy(helper, 4.5, 1.5);
        player.setPos(helper.absoluteVec(new Vec3(4.5, 2, 0.5)));
        groundSwing(player, plain, false);
        helper.assertTrue(near(loss(plain), SWORD), "a ground hit took " + loss(plain) + ", wanted " + SWORD);
        String plainFactors = newestFactors(player, Skill.SWORDS);
        helper.assertTrue(plainFactors == null || !plainFactors.contains("crit"),
                "a plain hit's XP line carries a crit factor: " + plainFactors);
        finish(helper, player);
    }

    @GameTest(template = T, batch = "crit_lunge")
    public static void lungeMakesASprintHitCritOncePerCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_SWORD));
        rank(player, Skill.SWORDS, "lunge", 1);
        float SWORD = plainHit(helper, player, 7.5, 1.5);
        Mob first = dummy(helper, 0.5, 1.5);
        groundSwing(player, first, true);
        helper.assertTrue(near(loss(first), SWORD * 1.5f), "a Lunge took " + loss(first) + ", wanted 1.5 x " + SWORD);
        // Rank 1 waits 9 s: the next sprint hit is a plain one.
        Mob second = dummy(helper, 2.5, 1.5);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 0.5)));
        groundSwing(player, second, true);
        helper.assertTrue(near(loss(second), SWORD), "Lunge ignored its cooldown: " + loss(second));

        // Control: no Lunge, no sprint crit (vanilla never allows one).
        ServerPlayer plain = player(helper, 5.5, 0.5, new ItemStack(Items.IRON_SWORD));
        Mob third = dummy(helper, 5.5, 1.5);
        groundSwing(plain, third, true);
        helper.assertTrue(near(loss(third), SWORD), "a sprint hit without Lunge took " + loss(third));
        finish(helper, player, plain);
    }

    @GameTest(template = T, batch = "crit_leaping_blade")
    public static void leapingBladeRaisesASwordCrit(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_SWORD));
        rank(player, Skill.SWORDS, "lunge", 1);
        rank(player, Skill.JUMPING, "high_jump", 3);
        float SWORD = plainHit(helper, player, 7.5, 1.5);
        Mob mob = dummy(helper, 0.5, 1.5);
        jumpSwing(player, mob);
        // The synergy also grants +5% sword proc power, which a plain hit never uses.
        helper.assertTrue(near(loss(mob), SWORD * 1.75f), "a Leaping Blade crit took " + loss(mob) + ", wanted 1.75 x " + SWORD);

        // Control: High Jump 2 leaves the synergy asleep.
        rank(player, Skill.JUMPING, "high_jump", 2);
        Mob control = dummy(helper, 4.5, 1.5);
        player.setPos(helper.absoluteVec(new Vec3(4.5, 2, 0.5)));
        jumpSwing(player, control);
        helper.assertTrue(near(loss(control), SWORD * 1.5f), "a crit without the synergy took " + loss(control));
        finish(helper, player);
    }

    @GameTest(template = T, batch = "crit_rend")
    public static void rendWoundsTheTargetForTheNextHits(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0.5, 0.5, new ItemStack(Items.IRON_AXE));
        float AXE = plainHit(helper, player, 7.5, 1.5);
        rank(player, Skill.AXES, "rend", 1);
        Mob mob = dummy(helper, 0.5, 1.5);
        jumpSwing(player, mob);
        float critLoss = loss(mob);
        helper.assertTrue(near(critLoss, AXE * 1.5f), "the opening axe crit took " + critLoss + ", wanted 1.5 x " + AXE);
        groundSwing(player, mob, false);
        float wounded = loss(mob) - critLoss;
        helper.assertTrue(near(wounded, AXE * 1.15f), "a hit on the wound took " + wounded + ", wanted 1.15 x " + AXE);

        // Control: without Rend the crit opens nothing.
        ServerPlayer plain = player(helper, 5.5, 0.5, new ItemStack(Items.IRON_AXE));
        Mob other = dummy(helper, 5.5, 1.5);
        jumpSwing(plain, other);
        float before = loss(other);
        groundSwing(plain, other, false);
        helper.assertTrue(near(loss(other) - before, AXE), "a hit after a Rend-less crit took " + (loss(other) - before));
        finish(helper, player, plain);
    }

    @GameTest(template = T, batch = "crit_flow")
    public static void flowSpeedsUpACriticalPuncher(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0.5, 0.5, ItemStack.EMPTY);
        rank(player, Skill.UNARMED, "flow", 2);
        Mob mob = dummy(helper, 0.5, 1.5);
        groundSwing(player, mob, false);
        helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED), "a plain punch gave Flow");
        jumpSwing(player, mob);
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.hasEffect(MobEffects.DIG_SPEED),
                "a critical punch gave no Speed and Haste");
        int left = player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration();
        helper.assertTrue(left > 30 && left <= 40, "Flow 2 lasts " + left + " ticks, wanted 40");
        helper.assertTrue(loss(mob) > FIST, "the punches did no damage");
        finish(helper, player);
    }
}
