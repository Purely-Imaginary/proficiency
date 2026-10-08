package dev.amman.proficiency.platform.event.entity.living;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public abstract class MobEffectEvent extends LivingEvent {

    @Nullable
    protected final MobEffectInstance effectInstance;

    protected MobEffectEvent(LivingEntity living, @Nullable MobEffectInstance effectInstance) {
        super(living);
        this.effectInstance = effectInstance;
    }

    @Nullable
    public MobEffectInstance getEffectInstance() {
        return effectInstance;
    }

    /** From {@code canBeAffected}: decide whether the effect may apply at all. */
    public static class Applicable extends MobEffectEvent {

        public enum Result {
            APPLY, DEFAULT, DO_NOT_APPLY
        }

        @Nullable
        private final Entity source;
        private final boolean vanillaResult;
        private Result result = Result.DEFAULT;

        public Applicable(LivingEntity living, MobEffectInstance effectInstance, @Nullable Entity source,
                boolean vanillaResult) {
            super(living, effectInstance);
            this.source = source;
            this.vanillaResult = vanillaResult;
        }

        @Override
        public MobEffectInstance getEffectInstance() {
            return super.getEffectInstance();
        }

        public void setResult(Result result) {
            this.result = result;
        }

        public Result getResult() {
            return result;
        }

        @Nullable
        public Entity getEffectSource() {
            return source;
        }

        public boolean getApplicationResult() {
            return switch (result) {
                case APPLY -> true;
                case DO_NOT_APPLY -> false;
                case DEFAULT -> vanillaResult;
            };
        }
    }

    /** An effect was added or replaced an old one. */
    public static class Added extends MobEffectEvent {

        @Nullable
        private final MobEffectInstance oldEffectInstance;
        @Nullable
        private final Entity source;

        public Added(LivingEntity living, @Nullable MobEffectInstance oldEffectInstance,
                MobEffectInstance newEffectInstance, @Nullable Entity source) {
            super(living, newEffectInstance);
            this.oldEffectInstance = oldEffectInstance;
            this.source = source;
        }

        @Override
        public MobEffectInstance getEffectInstance() {
            return super.getEffectInstance();
        }

        @Nullable
        public MobEffectInstance getOldEffectInstance() {
            return oldEffectInstance;
        }

        @Nullable
        public Entity getEffectSource() {
            return source;
        }
    }
}
