package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.entity.living.LivingBreatheEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDropsEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEntityUseItemEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingFallEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingHealEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingKnockBackEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingShieldBlockEvent;
import dev.amman.proficiency.platform.event.entity.living.MobEffectEvent;
import dev.amman.proficiency.platform.hooks.DamageHooks;
import dev.amman.proficiency.platform.hooks.LivingHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Posts the living-entity events at the points NeoForge 21.1's LivingEntity patch posts them.
 * Each injector names the NeoForge line it stands in for.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingHooks {

    @Shadow @Final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;
    @Shadow protected int useItemRemaining;

    @Shadow protected abstract int decreaseAirSupply(int air);
    @Shadow protected abstract int increaseAirSupply(int air);
    @Shadow public abstract boolean canBreatheUnderwater();
    @Shadow public abstract int getUseItemRemainingTicks();

    @Unique private final ArrayDeque<DamageContainer> proficiency$containers = new ArrayDeque<>();
    @Unique @Nullable private List<ItemEntity> proficiency$capturedDrops;

    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public ArrayDeque<DamageContainer> proficiency$containers() {
        return proficiency$containers;
    }

    @Override
    public @Nullable List<ItemEntity> proficiency$capturedDrops() {
        return proficiency$capturedDrops;
    }

    @Override
    public void proficiency$setCapturedDrops(@Nullable List<ItemEntity> drops) {
        proficiency$capturedDrops = drops;
    }

    @Unique
    private LivingEntity proficiency$self() {
        return (LivingEntity) (Object) this;
    }

    // ------------------------------------------------------------------------ hurt

    /** NeoForge: push a DamageContainer and post LivingIncomingDamageEvent after the early outs. */
    @Inject(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", ordinal = 0), cancellable = true)
    private void proficiency$incomingDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true) LocalFloatRef amountRef,
            @Share("proficiencyContainer") LocalRef<DamageContainer> shared) {
        DamageContainer container = new DamageContainer(source, amount);
        proficiency$containers.push(container);
        shared.set(container);
        if (NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(proficiency$self(), container)).isCanceled()) {
            proficiency$containers.remove(container);
            shared.set(null);
            cir.setReturnValue(false);
            return;
        }
        amountRef.set(container.getNewDamage());
    }

    /** NeoForge: LivingShieldBlockEvent, for every hit with damage left, blocked or not. */
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean proficiency$shieldBlock(LivingEntity self, DamageSource source, Operation<Boolean> original,
            @Share("proficiencyContainer") LocalRef<DamageContainer> shared,
            @Share("proficiencyShield") LocalRef<LivingShieldBlockEvent> shield) {
        boolean blocked = original.call(self, source);
        DamageContainer container = shared.get();
        if (container == null) {
            return blocked;
        }
        LivingShieldBlockEvent event = NeoForge.EVENT_BUS.post(new LivingShieldBlockEvent(self, container, blocked));
        if (event.getBlocked()) {
            container.setBlocked(event.getBlockedDamage(), event.shieldDamage());
            shield.set(event);
        }
        return event.getBlocked();
    }

    /** NeoForge: the shield loses {@code shieldDamage()}, which listeners may have lowered to zero. */
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurtCurrentlyUsedShield(F)V"))
    private void proficiency$shieldDamage(LivingEntity self, float amount, Operation<Void> original,
            @Share("proficiencyShield") LocalRef<LivingShieldBlockEvent> shield) {
        LivingShieldBlockEvent event = shield.get();
        if (event == null) {
            original.call(self, amount);
        } else if (event.shieldDamage() > 0) {
            original.call(self, event.shieldDamage());
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void proficiency$popContainer(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
            @Share("proficiencyContainer") LocalRef<DamageContainer> shared) {
        DamageContainer container = shared.get();
        if (container != null) {
            proficiency$containers.remove(container);
        }
    }

    // ------------------------------------------------------------------------ actuallyHurt

    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float proficiency$damagePre(float afterMagic, DamageSource source,
            @Share("proficiencyDamage") LocalRef<DamageContainer> shared) {
        DamageContainer container = DamageHooks.current(proficiency$self(), source, afterMagic);
        shared.set(container);
        return DamageHooks.pre(proficiency$self(), container, afterMagic);
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"))
    private void proficiency$damagePost(DamageSource source, float ignored, CallbackInfo ci,
            @Local(argsOnly = true) float toHealth,
            @Share("proficiencyDamage") LocalRef<DamageContainer> shared) {
        if (shared.get() != null) {
            DamageHooks.post(proficiency$self(), shared.get(), toHealth);
        }
    }

    // ------------------------------------------------------------------------ knockback, fall, death

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void proficiency$knockback(double strength, double x, double z, CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) LocalDoubleRef strengthRef,
            @Local(argsOnly = true, ordinal = 1) LocalDoubleRef xRef,
            @Local(argsOnly = true, ordinal = 2) LocalDoubleRef zRef) {
        LivingKnockBackEvent event = NeoForge.EVENT_BUS.post(
                new LivingKnockBackEvent(proficiency$self(), (float) strength, x, z));
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        strengthRef.set(event.getStrength());
        xRef.set(event.getRatioX());
        zRef.set(event.getRatioZ());
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void proficiency$fall(float distance, float multiplier, DamageSource source,
            CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true, ordinal = 0) LocalFloatRef distanceRef,
            @Local(argsOnly = true, ordinal = 1) LocalFloatRef multiplierRef) {
        LivingFallEvent event = NeoForge.EVENT_BUS.post(new LivingFallEvent(proficiency$self(), distance, multiplier));
        if (event.isCanceled()) {
            cir.setReturnValue(false);
            return;
        }
        distanceRef.set(event.getDistance());
        multiplierRef.set(event.getDamageMultiplier());
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void proficiency$death(DamageSource source, CallbackInfo ci) {
        if (NeoForge.EVENT_BUS.post(new LivingDeathEvent(proficiency$self(), source)).isCanceled()) {
            ci.cancel();
        }
    }

    /** NeoForge captures every death drop, posts LivingDropsEvent, then spawns what is left. */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void proficiency$beginDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        proficiency$capturedDrops = new ArrayList<>();
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void proficiency$endDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        List<ItemEntity> drops = proficiency$capturedDrops;
        proficiency$capturedDrops = null;
        if (drops == null) {
            return;
        }
        boolean recentlyHit = ((LivingEntityAccessor) this).proficiency$lastHurtByPlayerTime() > 0;
        if (!NeoForge.EVENT_BUS.post(new LivingDropsEvent(proficiency$self(), source, drops, recentlyHit)).isCanceled()) {
            for (ItemEntity drop : drops) {
                level.addFreshEntity(drop);
            }
        }
    }

    // ------------------------------------------------------------------------ breathing

    /**
     * NeoForge replaces the vanilla air block with CommonHooks.onLivingBreathe. Same thing here: run
     * that logic at the vanilla block's first test, then make the vanilla block do nothing.
     */
    @Inject(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
    private void proficiency$breathe(CallbackInfo ci) {
        LivingEntity self = proficiency$self();
        int air = self.getAirSupply();
        int consume = air - decreaseAirSupply(air);
        int refill = increaseAirSupply(air) - air;
        boolean inWater = self.isEyeInFluid(FluidTags.WATER);
        boolean inLava = self.isEyeInFluid(FluidTags.LAVA);
        boolean bubbles = self.level().getBlockState(BlockPos.containing(self.getX(), self.getEyeY(), self.getZ()))
                .is(Blocks.BUBBLE_COLUMN);
        boolean isAir = (!inWater && !inLava) || bubbles;
        boolean canBreathe = isAir;
        // Water breathing, a fish, a creative player, or a fluid nobody drowns in (lava): no
        // drowning, but no refill either.
        if (!isAir && (MobEffectUtil.hasWaterBreathing(self) || !inWater || canBreatheUnderwater()
                || (self instanceof Player player && player.getAbilities().invulnerable))) {
            canBreathe = true;
            refill = 0;
        }
        LivingBreatheEvent event = NeoForge.EVENT_BUS.post(new LivingBreatheEvent(self, canBreathe, consume, refill));
        if (event.canBreathe()) {
            self.setAirSupply(Math.min(self.getAirSupply() + event.getRefillAirAmount(), self.getMaxAirSupply()));
        } else {
            self.setAirSupply(self.getAirSupply() - event.getConsumeAirAmount());
        }
        if (self.getAirSupply() <= -20) {
            self.setAirSupply(0);
            Vec3 motion = self.getDeltaMovement();
            for (int i = 0; i < 8; i++) {
                double dx = self.getRandom().nextDouble() - self.getRandom().nextDouble();
                double dy = self.getRandom().nextDouble() - self.getRandom().nextDouble();
                double dz = self.getRandom().nextDouble() - self.getRandom().nextDouble();
                self.level().addParticle(ParticleTypes.BUBBLE, self.getX() + dx, self.getY() + dy, self.getZ() + dz,
                        motion.x, motion.y, motion.z);
            }
            self.hurt(self.damageSources().drown(), 2.0F);
        }
        if (inWater && !bubbles && !self.level().isClientSide && self.isPassenger() && self.getVehicle() != null
                && self.getVehicle().dismountsUnderwater()) {
            self.stopRiding();
        }
    }

    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
    private boolean proficiency$skipVanillaDrowning(LivingEntity self, net.minecraft.tags.TagKey<?> tag,
            Operation<Boolean> original) {
        return false;
    }

    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;increaseAirSupply(I)I"))
    private int proficiency$skipVanillaRefill(LivingEntity self, int air, Operation<Integer> original) {
        return air;
    }

    // ------------------------------------------------------------------------ healing

    /** NeoForge: LivingHealEvent. Canceled or zeroed means nothing heals. */
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float proficiency$heal(float amount) {
        LivingEntity self = proficiency$self();
        LivingHealEvent event = NeoForge.EVENT_BUS.post(new LivingHealEvent(self, amount));
        return event.isCanceled() ? 0f : event.getAmount();
    }

    // ------------------------------------------------------------------------ effects

    @WrapOperation(method = {"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            "forceAddEffect"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean proficiency$effectApplicable(LivingEntity self, MobEffectInstance effect, Operation<Boolean> original,
            @Local(argsOnly = true) @Nullable Entity source) {
        boolean vanilla = original.call(self, effect);
        return NeoForge.EVENT_BUS.post(new MobEffectEvent.Applicable(self, effect, source, vanilla))
                .getApplicationResult();
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0, shift = At.Shift.AFTER))
    private void proficiency$effectAdded(MobEffectInstance effect, @Nullable Entity source,
            CallbackInfoReturnable<Boolean> cir) {
        NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(proficiency$self(),
                activeEffects.get(effect.getEffect()), effect, source));
    }

    // ------------------------------------------------------------------------ using items

    @Inject(method = "startUsingItem", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;useItem:Lnet/minecraft/world/item/ItemStack;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD), cancellable = true)
    private void proficiency$useStart(InteractionHand hand, CallbackInfo ci, @Local ItemStack stack,
            @Share("proficiencyUse") LocalIntRef duration, @Share("proficiencyUseSet") LocalBooleanRef set) {
        var event = NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Start(
                proficiency$self(), stack, hand, stack.getUseDuration(proficiency$self())));
        if (event.isCanceled() || event.getDuration() < 0) {
            ci.cancel();
            return;
        }
        duration.set(event.getDuration());
        set.set(true);
    }

    @WrapOperation(method = "startUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int proficiency$useDuration(ItemStack stack, LivingEntity entity, Operation<Integer> original,
            @Share("proficiencyUse") LocalIntRef duration, @Share("proficiencyUseSet") LocalBooleanRef set) {
        return set.get() ? duration.get() : original.call(stack, entity);
    }

    @Inject(method = "updateUsingItem", at = @At("HEAD"))
    private void proficiency$useTick(ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) {
            return;
        }
        int remaining = getUseItemRemainingTicks();
        var event = NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Tick(proficiency$self(), stack, remaining));
        if (event.isCanceled()) {
            useItemRemaining = -1;
        } else if (event.getDuration() != remaining) {
            useItemRemaining = event.getDuration();
        }
    }

    @WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack proficiency$useFinish(ItemStack stack, Level level, LivingEntity entity,
            Operation<ItemStack> original) {
        ItemStack copy = stack.copy();
        ItemStack result = original.call(stack, level, entity);
        return NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(
                entity, copy, getUseItemRemainingTicks(), result)).getResultStack();
    }

    // ------------------------------------------------------------------------ jump, visibility

    @Inject(method = "jumpFromGround", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasImpulse:Z",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void proficiency$jump(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(proficiency$self()));
    }

    @ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
    private double proficiency$visibility(double original, @Local(argsOnly = true) @Nullable Entity looking) {
        var event = NeoForge.EVENT_BUS.post(new LivingEvent.LivingVisibilityEvent(proficiency$self(), looking, original));
        return Math.max(0, event.getVisibilityModifier());
    }
}
