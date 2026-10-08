package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import dev.amman.proficiency.platform.damage.DamageContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** A shield stopped (or could stop) a hit. By default the whole hit is blocked and the shield loses that much. */
public class LivingShieldBlockEvent extends LivingEvent implements ICancellableEvent {

    private final DamageContainer container;
    private final boolean originalBlocked;
    private boolean blocked;
    private float blockedDamage;
    private float shieldDamage;

    public LivingShieldBlockEvent(LivingEntity blocker, DamageContainer container, boolean originalBlockedState) {
        super(blocker);
        this.container = container;
        this.originalBlocked = originalBlockedState;
        this.blocked = originalBlockedState;
        this.blockedDamage = container.getNewDamage();
        this.shieldDamage = container.getNewDamage();
    }

    public DamageContainer getDamageContainer() {
        return container;
    }

    public DamageSource getDamageSource() {
        return container.getSource();
    }

    public float getOriginalBlockedDamage() {
        return container.getNewDamage();
    }

    public float getBlockedDamage() {
        return Math.min(blockedDamage, container.getNewDamage());
    }

    public float shieldDamage() {
        return blocked ? shieldDamage : 0;
    }

    public void setBlockedDamage(float blocked) {
        this.blockedDamage = Math.max(0, blocked);
    }

    public void setShieldDamage(float damage) {
        this.shieldDamage = damage;
    }

    public boolean getOriginalBlock() {
        return originalBlocked;
    }

    public boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(boolean isBlocked) {
        this.blocked = isBlocked;
    }
}
