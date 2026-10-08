package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.item.MaceItem;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Forge 1.20.1 only: the mace this port adds (1.20.1 has none, and the Maces skill needs one).
 * A swing trains Maces; a swing while falling adds 1.21's smash bonus and ends the fall.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaceGameTests {

    private MaceGameTests() {
    }

    private static ServerPlayer swinger(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        ItemStack mace = new ItemStack(ProficiencyItems.MACE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, mace);
        // A mock player is never ticked, so the held item's attributes are applied by hand.
        player.getAttributes().addTransientAttributeModifiers(
                mace.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
        return player;
    }

    private static Mob dummy(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        mob.setHealth(200f);
        return mob;
    }

    private static void fullCharge(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 100);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not charge the swing", e);
        }
    }

    @GameTest(template = "empty", batch = "mace")
    public static void aMaceSwingTrainsMacesAndASmashHitsHarder(GameTestHelper helper) {
        ServerPlayer player = swinger(helper);
        Mob plain = dummy(helper);
        fullCharge(player);
        player.attack(plain);
        float flat = 200f - plain.getHealth();
        // 6 attack damage, less a zombie's 2 armour points.
        helper.assertTrue(Math.abs(flat - 5.5f) < 1.0f, "a charged mace swing on the ground dealt " + flat);
        helper.assertTrue(ProficiencyAttachments.of(player).progress(Skill.MACES) > 0
                        || ProficiencyAttachments.of(player).level(Skill.MACES) > 0,
                "a mace swing did not train Maces");

        Mob smashed = dummy(helper);
        smashed.invulnerableTime = 0;
        fullCharge(player);
        player.fallDistance = 5f;
        player.attack(smashed);
        float smash = 200f - smashed.getHealth();
        float bonus = MaceItem.smashBonus(5f);
        helper.assertTrue(smash >= bonus + 4f, "a 5-block smash dealt " + smash + ", bonus " + bonus);
        helper.assertTrue(player.fallDistance == 0f, "the smash did not end the fall: " + player.fallDistance);
        helper.succeed();
    }
}
