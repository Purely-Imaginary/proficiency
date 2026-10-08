package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class LivingEntityUseItemEvent extends LivingEvent {

    private final ItemStack item;
    @Nullable
    private final InteractionHand hand;
    private int duration;

    private LivingEntityUseItemEvent(LivingEntity entity, ItemStack item, @Nullable InteractionHand hand, int duration) {
        super(entity);
        this.item = item;
        this.hand = hand;
        this.duration = duration;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Nullable
    public InteractionHand getHand() {
        return hand;
    }

    /** {@code startUsingItem}: duration is the use time; cancel to not start. */
    public static class Start extends LivingEntityUseItemEvent implements ICancellableEvent {
        public Start(LivingEntity entity, ItemStack item, InteractionHand hand, int duration) {
            super(entity, item, hand, duration);
        }
    }

    /** Each use tick; duration is the remaining ticks. */
    public static class Tick extends LivingEntityUseItemEvent implements ICancellableEvent {
        public Tick(LivingEntity entity, ItemStack item, int duration) {
            super(entity, item, entity.getUsedItemHand(), duration);
        }
    }

    /** Use completed; {@code item} is a copy of what was used, the result may be replaced. */
    public static class Finish extends LivingEntityUseItemEvent {

        private ItemStack result;

        public Finish(LivingEntity entity, ItemStack item, int duration, ItemStack result) {
            super(entity, item, entity.getUsedItemHand(), duration);
            this.result = result;
        }

        public ItemStack getResultStack() {
            return result;
        }

        public void setResultStack(ItemStack result) {
            this.result = result;
        }
    }
}
