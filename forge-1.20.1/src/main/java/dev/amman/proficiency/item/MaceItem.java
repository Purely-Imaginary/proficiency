package dev.amman.proficiency.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft 1.21's mace, rebuilt for 1.20.1, because the Maces skill needs something to swing.
 *
 * <p>Numbers are 1.21.1's: 6 attack damage, 0.6 attacks a second, 500 uses, epic. The smash: a
 * hit while falling more than 1.5 blocks deals +4 per block for the first 3 blocks, +2 per block
 * for the next 5, and +1 per block after that; it then cancels the fall, knocks back everything
 * within 3.5 blocks of the target and shakes the ground. The bonus is added in
 * {@link SpecialItemEvents#onMaceSmash}, because 1.20.1 has no {@code getAttackDamageBonus} hook.
 *
 * <p>Changed on 1.20.1: the recipe is an iron block over a blaze rod (there is no heavy core or
 * breeze rod), it repairs with blaze rods, and the smash sounds are the anvil's (there are no
 * mace sounds). It has no Density, Breach or Wind Burst, because 1.20.1 has no such enchantments.
 */
public class MaceItem extends DescribedItem {

    public static final float SMASH_MIN_FALL = 1.5f;
    private static final double KNOCKBACK_RANGE = 3.5;

    private final Multimap<Attribute, AttributeModifier> modifiers;

    public MaceItem(Properties properties) {
        super(properties);
        modifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                        "Weapon modifier", 5.0, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
                        "Weapon modifier", -3.4, AttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.BLAZE_ROD);
    }

    public static boolean canSmash(LivingEntity attacker) {
        return attacker.fallDistance > SMASH_MIN_FALL && !attacker.isFallFlying();
    }

    /** 1.21.1's {@code MaceItem.getAttackDamageBonus}, without Density. */
    public static float smashBonus(float fallDistance) {
        if (fallDistance <= 3.0f) {
            return 4.0f * fallDistance;
        }
        if (fallDistance <= 8.0f) {
            return 12.0f + 2.0f * (fallDistance - 3.0f);
        }
        return 22.0f + fallDistance - 8.0f;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, entity -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        if (canSmash(attacker) && attacker.level() instanceof ServerLevel level) {
            boolean heavy = attacker.fallDistance > 5.0f;
            if (attacker instanceof ServerPlayer player) {
                player.setDeltaMovement(player.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0.01));
                player.hurtMarked = true;
            }
            BlockPos below = target.blockPosition().below();
            BlockState ground = level.getBlockState(below);
            if (target.onGround() && !ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                        target.getX(), target.getY(), target.getZ(),
                        heavy ? 60 : 30, 0.6, 0.1, 0.6, 0.15);
                level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, heavy ? 0.7f : 0.45f, heavy ? 0.55f : 0.8f);
            } else {
                level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            knockback(level, attacker, target, heavy);
            attacker.resetFallDistance();
        }
        return true;
    }

    /** 1.21.1's smash knockback: everything near the target is thrown away from it. */
    private static void knockback(ServerLevel level, LivingEntity attacker, Entity target, boolean heavy) {
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(KNOCKBACK_RANGE))) {
            if (near == attacker || near == target || attacker.isAlliedTo(near)
                    || (near instanceof ArmorStand stand && stand.isMarker())
                    || (near instanceof TamableAnimal pet && pet.isOwnedBy(attacker))
                    || near.distanceToSqr(target) > KNOCKBACK_RANGE * KNOCKBACK_RANGE) {
                continue;
            }
            Vec3 away = near.position().subtract(target.position());
            double strength = (KNOCKBACK_RANGE - away.length()) * 0.7 * (heavy ? 2.0 : 1.0)
                    * (1.0 - near.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if (strength <= 0) {
                continue;
            }
            Vec3 push = away.normalize().scale(strength);
            near.push(push.x, 0.7, push.z);
            if (near instanceof ServerPlayer player) {
                player.hurtMarked = true;
            }
        }
    }

    /** True for this mace. */
    public static boolean isMace(ItemStack stack) {
        return stack.getItem() instanceof MaceItem;
    }
}
