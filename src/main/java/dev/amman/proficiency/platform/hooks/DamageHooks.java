package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.entity.living.LivingDamageEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The {@code actuallyHurt} half of the damage pipeline, shared by LivingEntity and Player (Player
 * overrides actuallyHurt without calling super). As on NeoForge 21.1: Pre sees the damage after
 * armour and enchantments and may change it; absorption comes off after; Post reports what came
 * off health.
 */
public final class DamageHooks {

    private DamageHooks() {
    }

    /** The container of the hurt call in flight, or a fresh one if actuallyHurt was called directly. */
    public static DamageContainer current(LivingEntity entity, DamageSource source, float amount) {
        var stack = ((LivingHooks) entity).proficiency$containers();
        DamageContainer c = stack.peek();
        if (c == null || c.getSource() != source) {
            c = new DamageContainer(source, amount);
        }
        return c;
    }

    public static float pre(LivingEntity entity, DamageContainer container, float afterArmourAndMagic) {
        container.setNewDamage(afterArmourAndMagic);
        NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(entity, container));
        return container.getNewDamage();
    }

    public static void post(LivingEntity entity, DamageContainer container, float toHealth) {
        container.setNewDamage(toHealth);
        NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(entity, container));
    }
}
