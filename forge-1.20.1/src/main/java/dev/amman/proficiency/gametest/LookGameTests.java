package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.jade.LookText;
import dev.amman.proficiency.look.HearthLookData;
import dev.amman.proficiency.look.LookRules;
import dev.amman.proficiency.look.XpLookData;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * What the optional Jade plugin's server half answers ({@link XpLookData}, {@link HearthLookData}): the same
 * skill and amount the XP code pays, a placed block that pays nothing, an ore the tool cannot harvest that
 * says nothing, the first-time mark until it has been paid, and that looking changes nothing. The Jade
 * classes themselves are never loaded here: the tests run without Jade.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LookGameTests {

    private static final String T = "empty";
    private static final BlockPos ORE = new BlockPos(2, 2, 2);

    private LookGameTests() {
    }

    private static ServerPlayer survivor(GameTestHelper helper, Item held) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2, 2, 4)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(held));
        return player;
    }

    private static CompoundTag block(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        CompoundTag tag = new CompoundTag();
        XpLookData.block(player, helper.getLevel(), helper.absolutePos(rel), tag);
        return tag;
    }

    private static void leave(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    @GameTest(template = T)
    public static void anOreShowsItsSkillItsPriceAndTheFirstTimeMark(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.STONE_PICKAXE);
        helper.setBlock(ORE, Blocks.IRON_ORE);
        CompoundTag tag = block(helper, player, ORE);
        leave(player);
        helper.assertTrue(tag.getInt(XpLookData.SKILL) == Skill.MINING.ordinal(), "skill: " + tag);
        helper.assertTrue(tag.getFloat(XpLookData.XP) > 0f, "an ore should pay: " + tag);
        helper.assertTrue(LookRules.has(tag.getInt(XpLookData.FLAGS), LookRules.FIRST), "the first mark: " + tag);
        helper.assertTrue(tag.getFloat(XpLookData.FIRST) > 0f, "the first-time bonus: " + tag);
        helper.assertFalse(LookRules.has(tag.getInt(XpLookData.FLAGS), LookRules.PLACED), "not placed: " + tag);
        helper.succeed();
    }

    @GameTest(template = T)
    public static void anOreTheToolCannotHarvestShowsNothing(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.STONE_PICKAXE);
        helper.setBlock(ORE, Blocks.DIAMOND_ORE);
        CompoundTag tag = block(helper, player, ORE);
        // No tool at all: no rule for the hand either.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CompoundTag bare = block(helper, player, ORE);
        leave(player);
        helper.assertTrue(tag.isEmpty(), "a stone pickaxe cannot take diamond: " + tag);
        helper.assertTrue(bare.isEmpty(), "a bare hand pays nothing: " + bare);
        helper.succeed();
    }

    @GameTest(template = T)
    public static void aPlacedBlockSaysItPaysNothing(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.STONE_PICKAXE);
        helper.setBlock(ORE, Blocks.STONE);
        PlacedBlocks.markPlacement(helper.getLevel(), helper.absolutePos(ORE), Blocks.STONE.defaultBlockState());
        CompoundTag tag = block(helper, player, ORE);
        leave(player);
        if (ProficiencyConfig.placedBlocksPayXp()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(tag.getInt(XpLookData.SKILL) == Skill.MINING.ordinal(), "skill: " + tag);
        helper.assertTrue(LookRules.has(tag.getInt(XpLookData.FLAGS), LookRules.PLACED), "placed flag: " + tag);
        helper.assertFalse(tag.contains(XpLookData.XP), "a placed block names no amount: " + tag);
        helper.succeed();
    }

    @GameTest(template = T)
    public static void theFirstTimeMarkGoesOnceThePlayerHasBeenPaid(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.STONE_PICKAXE);
        helper.setBlock(ORE, Blocks.IRON_ORE);
        helper.assertTrue(block(helper, player, ORE).getFloat(XpLookData.FIRST) > 0f, "the mark before");
        SkillService.grant(player, Skill.MINING, 1.0, Blocks.IRON_ORE.getDescriptionId());
        CompoundTag tag = block(helper, player, ORE);
        leave(player);
        helper.assertFalse(tag.contains(XpLookData.FIRST), "the mark after it was paid: " + tag);
        helper.assertTrue(tag.getFloat(XpLookData.XP) > 0f, "the block still pays: " + tag);
        helper.succeed();
    }

    @GameTest(template = T)
    public static void lookingChangesNothing(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.STONE_PICKAXE);
        helper.setBlock(ORE, Blocks.IRON_ORE);
        var skills = ProficiencyAttachments.of(player);
        float before = skills.xp(Skill.MINING);
        double tempo = dev.amman.proficiency.skill.Tempo.preview(player, Skill.MINING);
        for (int i = 0; i < 5; i++) {
            block(helper, player, ORE);
        }
        double after = dev.amman.proficiency.skill.Tempo.preview(player, Skill.MINING);
        var log = SkillService.xpLog(player);
        boolean logged = log != null && !log.entries().isEmpty();
        float xp = skills.xp(Skill.MINING);
        helper.assertTrue(tempo == after, "looking took a Tempo step: " + tempo + " -> " + after);
        helper.assertTrue(xp == before, "looking paid XP: " + before + " -> " + xp);
        helper.assertFalse(logged, "looking wrote a log line");
        leave(player);
        helper.succeed();
    }

    @GameTest(template = T)
    public static void aMobShowsAHitAndAKillWithTheHeldWeapon(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.IRON_SWORD);
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 3));
        CompoundTag tag = new CompoundTag();
        XpLookData.mob(player, zombie, tag);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CompoundTag bare = new CompoundTag();
        XpLookData.mob(player, zombie, bare);
        leave(player);
        helper.assertTrue(tag.getInt(XpLookData.SKILL) == Skill.SWORDS.ordinal(), "sword skill: " + tag);
        helper.assertTrue(tag.getFloat(XpLookData.XP) > 0f && tag.getFloat(XpLookData.KILL) > 0f, "hit and kill: " + tag);
        helper.assertTrue(LookRules.has(tag.getInt(XpLookData.FLAGS), LookRules.MOB), "mob flag: " + tag);
        helper.assertTrue(bare.getInt(XpLookData.SKILL) == Skill.UNARMED.ordinal(), "bare hand fights unarmed: " + bare);
        helper.succeed();
    }

    @GameTest(template = T)
    public static void aSpawnEggMobSaysItPaysNothing(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, Items.IRON_SWORD);
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 3));
        SpawnOrigin.tag(zombie, MobSpawnType.SPAWN_EGG);
        CompoundTag tag = new CompoundTag();
        XpLookData.mob(player, zombie, tag);
        leave(player);
        if (ProficiencyConfig.artificialMobXp() > 0) {
            helper.succeed();
            return;
        }
        int flags = tag.getInt(XpLookData.FLAGS);
        helper.assertTrue(LookRules.has(flags, LookRules.MOB) && LookRules.has(flags, LookRules.PLACED),
                "an egg mob pays nothing: " + tag);
        helper.assertFalse(tag.contains(XpLookData.XP), "and names no amount: " + tag);
        helper.succeed();
    }

    @GameTest(template = T, batch = "look_hearth")
    public static void aHearthAnswersWithItsStageAndLines(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        // Players of other tests may stand near: they would count as home for this Hearth.
        Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        for (ServerPlayer other : java.util.List.copyOf(helper.getLevel().players())) {
            if (other.position().distanceTo(origin) < 96) {
                other.setPos(other.getX(), other.getY() + 400, other.getZ());
            }
        }
        ServerPlayer player = survivor(helper, Items.FLINT_AND_STEEL);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 7.5)));
        HearthDesignBuilds.place(helper, ORE, 1);
        int before = ProficiencyConfig.hearthMinSpacing();
        ProficiencyConfig.HEARTH_MIN_SPACING.set(10);
        HearthBlockEntity hearth;
        try {
            helper.setBlock(ORE, Blocks.CAMPFIRE);
            BlockPos abs = helper.absolutePos(ORE);
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            player.setItemInHand(InteractionHand.MAIN_HAND, flint);
            player.gameMode.useItemOn(player, player.serverLevel(), flint, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            if (!(helper.getLevel().getBlockEntity(abs) instanceof HearthBlockEntity found)) {
                throw new GameTestAssertException("the campfire should have become a Hearth");
            }
            hearth = found;
        } finally {
            ProficiencyConfig.HEARTH_MIN_SPACING.set(before);
        }
        CompoundTag tag = new CompoundTag();
        HearthLookData.write(player, hearth, tag);
        var lines = LookText.hearthLines(tag);
        boolean building = HearthLookData.build(tag).on();
        leave(player);
        helper.assertTrue(tag.getInt(HearthLookData.STAGE) >= 1, "a lit Hearth has a stage: " + tag);
        helper.assertTrue(tag.contains(HearthLookData.WARMTH), "the looking player's Warmth: " + tag);
        helper.assertTrue(tag.contains(HearthLookData.LANE), "the design lane: " + tag);
        helper.assertFalse(building, "no self-build is running");
        helper.assertTrue(lines.size() >= 2, "headline and charge: " + lines);
        HearthDesignBuilds.clear(helper, ORE);
        helper.succeed();
    }
}
