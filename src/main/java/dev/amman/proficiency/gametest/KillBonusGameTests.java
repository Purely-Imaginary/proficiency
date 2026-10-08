package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.skill.KillXp;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * Hits pay their XP and no first-time bonus; the kill pays the kill bonus and the first-time bonus
 * for that kind of mob, once. The arithmetic is in KillXpTest.
 */
public final class KillBonusGameTests implements FabricGameTest {

    private static final String ZOMBIE = EntityType.ZOMBIE.getDescriptionId();
    private static final String HUSK = EntityType.HUSK.getDescriptionId();

    /** Fabric instantiates the entrypoint class itself. */
    public KillBonusGameTests() {
    }

    private static ServerPlayer fighter(GameTestHelper helper, net.minecraft.world.item.Item weapon) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        return player;
    }

    private static Mob mob(GameTestHelper helper, EntityType<? extends Mob> type, int x) {
        return helper.spawnWithNoFreeWill(type, new BlockPos(x, 2, 2));
    }

    /** XP the log holds for one skill and source prefix, and how many gains it merged. */
    private static float logged(ServerPlayer player, Skill skill, String prefix, int[] gains) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == skill.ordinal() && entry.source().startsWith(prefix)) {
                    sum += entry.amount();
                    if (gains != null) {
                        gains[0] += entry.count();
                    }
                }
            }
        }
        return sum;
    }

    private static float rate(Skill skill) {
        return (float) (ProficiencyConfig.xpRate(skill) * ProficiencyConfig.xpMultiplier());
    }

    private static float sixXp(Skill skill) {
        // A 20-health mob at the default formula.
        return 6f * rate(skill);
    }

    private static void hit(ServerPlayer player, Mob target, float damage) {
        target.hurt(player.damageSources().playerAttack(player), damage);
    }

    /** A first hit on a new mob type pays the hit and nothing from the first-time bonus. */
    @GameTest(template = "proficiency:empty")
    public static void aFirstHitPaysNoFirstTimeBonus(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, Items.IRON_SWORD);
        Mob zombie = mob(helper, EntityType.ZOMBIE, 2);
        hit(player, zombie, 1f);
        helper.assertTrue(logged(player, Skill.SWORDS, ZOMBIE, null) > 0f, "the hit paid no Swords XP");
        helper.assertTrue(logged(player, Skill.SWORDS, "first|", null) == 0f,
                "a first hit paid the first-time bonus");
        helper.assertFalse(ProficiencyAttachments.of(player).hasVisited("first:swords:" + ZOMBIE),
                "a first hit marked the kind as seen");
        helper.assertTrue(logged(player, Skill.SWORDS, KillXp.KILL_PREFIX, null) == 0f,
                "a hit that did not kill paid a kill bonus");
        helper.succeed();
    }

    /** The kill pays the kill bonus and the first-time bonus once; the next kill pays only the bonus. */
    @GameTest(template = "proficiency:empty")
    public static void aKillPaysTheBonusAndTheFirstTimeOnce(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, Items.IRON_SWORD);
        hit(player, mob(helper, EntityType.ZOMBIE, 2), 1000f);

        int[] kills = new int[1];
        float paid = logged(player, Skill.SWORDS, KillXp.KILL_PREFIX + ZOMBIE, kills);
        helper.assertTrue(kills[0] == 1 && paid >= sixXp(Skill.SWORDS) - 0.01f,
                "the first kill paid " + paid + " in " + kills[0] + " lines, wanted at least " + sixXp(Skill.SWORDS));
        int[] firsts = new int[1];
        float first = logged(player, Skill.SWORDS, "first|", firsts);
        helper.assertTrue(firsts[0] == 1 && first > 0f, "the first kill paid " + firsts[0] + " first-time lines");
        helper.assertTrue(ProficiencyAttachments.of(player).hasVisited("first:swords:" + ZOMBIE),
                "the kill did not mark the kind as seen under the existing key");

        hit(player, mob(helper, EntityType.ZOMBIE, 3), 1000f);
        kills[0] = 0;
        float both = logged(player, Skill.SWORDS, KillXp.KILL_PREFIX + ZOMBIE, kills);
        helper.assertTrue(kills[0] == 2 && both > paid, "the second kill paid no kill bonus");
        firsts[0] = 0;
        float firstAfter = logged(player, Skill.SWORDS, "first|", firsts);
        helper.assertTrue(firsts[0] == 1 && Math.abs(firstAfter - first) < 0.001f,
                "the second kill paid the first-time bonus again");
        helper.succeed();
    }

    /** A kind already paid before this change stays paid: a stored key blocks the first-time bonus. */
    @GameTest(template = "proficiency:empty")
    public static void anOldFirstTimeKeyIsHonoured(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, Items.IRON_SWORD);
        ProficiencyAttachments.of(player).markVisited("first:swords:" + ZOMBIE);
        hit(player, mob(helper, EntityType.ZOMBIE, 2), 1000f);
        helper.assertTrue(logged(player, Skill.SWORDS, KillXp.KILL_PREFIX + ZOMBIE, null) > 0f,
                "the kill paid no bonus");
        helper.assertTrue(logged(player, Skill.SWORDS, "first|", null) == 0f,
                "a kind that already paid paid again");
        helper.succeed();
    }

    /** A spawn-egg mob pays nothing on the kill, first-time included; a spawner mob pays a quarter. */
    @GameTest(template = "proficiency:empty")
    public static void spawnEggMobsPayNothingAndSpawnerMobsAQuarter(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, Items.IRON_SWORD);
        Mob egg = mob(helper, EntityType.ZOMBIE, 2);
        SpawnOrigin.tag(egg, MobSpawnType.SPAWN_EGG);
        hit(player, egg, 1000f);
        helper.assertTrue(logged(player, Skill.SWORDS, KillXp.KILL_PREFIX, null) == 0f,
                "a spawn-egg kill paid a kill bonus");
        helper.assertTrue(logged(player, Skill.SWORDS, "first|", null) == 0f,
                "a spawn-egg kill paid the first-time bonus");
        helper.assertFalse(ProficiencyAttachments.of(player).hasVisited("first:swords:" + ZOMBIE),
                "a spawn-egg kill used up the first-time bonus");

        Mob spawner = mob(helper, EntityType.HUSK, 3);
        SpawnOrigin.tag(spawner, MobSpawnType.SPAWNER);
        hit(player, spawner, 1000f);
        float quarter = logged(player, Skill.SWORDS, KillXp.KILL_PREFIX + HUSK, null);
        helper.assertTrue(quarter > 0f && quarter >= sixXp(Skill.SWORDS) * 0.25f - 0.01f
                        && quarter < sixXp(Skill.SWORDS) * 0.5f,
                "a spawner kill paid " + quarter + ", wanted about " + sixXp(Skill.SWORDS) * 0.25f);
        helper.succeed();
    }

    /** An arrow's kill goes to Archery even with a sword in hand. */
    @GameTest(template = "proficiency:empty")
    public static void aRangedKillCreditsArchery(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, Items.IRON_SWORD);
        Mob zombie = mob(helper, EntityType.ZOMBIE, 3);
        Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
        zombie.hurt(player.damageSources().arrow(arrow, player), 1000f);
        helper.assertTrue(logged(player, Skill.ARCHERY, KillXp.KILL_PREFIX + ZOMBIE, null) > 0f,
                "an arrow kill paid Archery no kill bonus");
        helper.assertTrue(logged(player, Skill.SWORDS, KillXp.KILL_PREFIX, null) == 0f,
                "an arrow kill paid the sword in hand");
        helper.assertTrue(logged(player, Skill.ARCHERY, "first|", null) > 0f,
                "the arrow kill paid no first-time bonus to Archery");
        helper.succeed();
    }

    /** A death by fire seconds after a sword hit credits the sword; with no recent hit it credits nobody. */
    @GameTest(template = "proficiency:empty")
    public static void anIndirectKillGoesToTheLastHit(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, Items.IRON_SWORD);
        Mob burned = mob(helper, EntityType.ZOMBIE, 2);
        hit(player, burned, 1f);
        burned.hurt(helper.getLevel().damageSources().onFire(), 1000f);
        helper.assertTrue(logged(player, Skill.SWORDS, KillXp.KILL_PREFIX + ZOMBIE, null) > 0f,
                "a fire death after a sword hit paid the sword nothing");

        Mob alone = mob(helper, EntityType.HUSK, 3);
        alone.hurt(helper.getLevel().damageSources().onFire(), 1000f);
        helper.assertTrue(logged(player, Skill.SWORDS, KillXp.KILL_PREFIX + HUSK, null) == 0f,
                "a mob nobody hit paid a kill bonus");
        helper.succeed();
    }
}
