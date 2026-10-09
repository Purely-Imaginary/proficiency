package dev.amman.proficiency.gametest;

import dev.amman.proficiency.event.GatheringEvents;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * A tool is recognised by what it can do, not only by what it is tagged: an item that answers the
 * pickaxe, shovel or axe item ability (a Meka-Tool, a paxel, a hammer no tag lists) counts. Needs
 * NeoForge's item abilities, so it lives in the loader module.
 */
public final class NeoForgeToolGameTests {

    private NeoForgeToolGameTests() {
    }

    private static float paid(ServerPlayer player, Skill skill) {
        float sum = 0f;
        var log = SkillService.xpLog(player);
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == skill.ordinal() && entry.source().startsWith("block.")) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    /**
     * A new Item cannot be made once the registries are frozen, so this reads real ones: the
     * loader's item abilities decide, and a pickaxe answers pickaxe but not shovel or hoe.
     */
    @GameTest(template = "proficiency:empty")
    public static void theLoadersItemAbilitiesDecideWhatATraditionalToolCanDo(GameTestHelper helper) {
        var platform = dev.amman.proficiency.platform.Services.platform();
        ItemStack pick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        ItemStack shovel = new ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL);
        ItemStack hoe = new ItemStack(net.minecraft.world.item.Items.IRON_HOE);
        ItemStack axe = new ItemStack(net.minecraft.world.item.Items.IRON_AXE);
        helper.assertTrue(platform.canDig(pick, "pickaxe"), "a pickaxe cannot dig like a pickaxe");
        helper.assertTrue(platform.canDig(shovel, "shovel"), "a shovel cannot dig like a shovel");
        helper.assertTrue(platform.canDig(hoe, "hoe"), "a hoe cannot dig like a hoe");
        helper.assertTrue(platform.canDig(axe, "axe"), "an axe cannot dig like an axe");
        helper.assertFalse(platform.canDig(pick, "shovel"), "a pickaxe digs like a shovel");
        helper.assertFalse(platform.canDig(shovel, "pickaxe"), "a shovel digs like a pickaxe");
        helper.assertFalse(platform.canDig(ItemStack.EMPTY, "pickaxe"), "an empty hand digs");
        helper.assertFalse(platform.canDig(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD), "axe"),
                "a sword digs like an axe");
        // And the paths the skills use agree, tags or not.
        helper.assertTrue(SkillTools.isPickaxe(pick) && !SkillTools.isShovel(pick), "pickaxe classification");
        helper.assertTrue(SkillTools.isHoe(hoe), "hoe classification");
        helper.assertTrue(SkillTools.meleeSkill(pick) == null, "a pickaxe became a weapon");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
        BlockPos dirt = new BlockPos(1, 1, 1);
        helper.setBlock(dirt, Blocks.DIRT);
        GatheringEvents.forgetAoe(player.getUUID());
        player.gameMode.destroyBlock(helper.absolutePos(dirt));
        helper.assertTrue(paid(player, Skill.EXCAVATION) > 0f, "dirt with a shovel paid no Excavation XP");
        helper.assertTrue(paid(player, Skill.MINING) == 0f, "a shovel paid Mining XP for dirt");
        helper.succeed();
    }
}
