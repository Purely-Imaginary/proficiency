package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Forge 1.20.1 only: what Fishing adds to {@code ItemFishedEvent} must come out of the water.
 * Forge's event copies the loot, so without {@code mixin.FishingHookMixin} the proc's treasure was
 * paid for (XP) but never spawned.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FishingGameTests {

    private FishingGameTests() {
    }

    @GameTest(template = "empty", batch = "fishing")
    public static void whatFishingAddsToTheCatchIsReallyCaught(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        var level = helper.getLevel();
        level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16)).forEach(ItemEntity::discard);

        FishingHook hook = new FishingHook(player, level, 0, 0);
        hook.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        try {
            java.lang.reflect.Field nibble = FishingHook.class.getDeclaredField("nibble");
            nibble.setAccessible(true);
            nibble.setInt(hook, 20);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not set a bite on the hook", e);
        }
        // The proc rolls the treasure table at least once: one fish plus at least one treasure.
        ProcService.forceNext(player, Skill.FISHING);
        hook.retrieve(rod);

        int caught = level.getEntitiesOfClass(ItemEntity.class, hook.getBoundingBox().inflate(4)).size();
        helper.assertTrue(caught >= 2, "a Fishing proc's treasure was not caught: " + caught + " item(s)");
        helper.succeed();
    }
}
