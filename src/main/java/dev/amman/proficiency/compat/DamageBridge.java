package dev.amman.proficiency.compat;

import dev.amman.proficiency.Proficiency;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turns Forge 1.20.1's damage hooks into the three NeoForge events master is written against.
 *
 * <ul>
 *   <li>{@link LivingIncomingDamageEvent}: posted by {@code mixin.LivingEntityDamageMixin} inside
 *       {@code LivingEntity.hurt}, at NeoForge's spot (after the invulnerable, client, dead and
 *       fire-resistance checks, after a player's difficulty scaling, before the shield).</li>
 *   <li>{@link LivingDamageEvent.Pre}: inside {@code actuallyHurt}, after armour and
 *       enchantments, before absorption hearts, as on NeoForge 1.21.1.</li>
 *   <li>{@link LivingDamageEvent.Post}: after health is taken, posted from the return of
 *       {@code actuallyHurt}, with the health that was really lost (Forge's own
 *       {@code LivingDamageEvent}, read last, after every other mod).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class DamageBridge {

    /** What the last Pre for an entity settled on, until its actuallyHurt returns. Server thread. */
    private static final Map<LivingEntity, float[]> PENDING_POST = new WeakHashMap<>();

    /**
     * The amount each hit entered hurt() with, before the shield, armour and absorption: NeoForge's
     * {@code DamageContainer.getOriginalDamage()}. Set by {@link #incoming}, read by Pre and Post.
     */
    private static final Map<LivingEntity, Float> ORIGINAL = new WeakHashMap<>();

    private DamageBridge() {
    }

    /**
     * Called by the mixin. Returns the amount the hit continues with, or NaN when a handler
     * cancelled the hit.
     */
    public static float incoming(LivingEntity entity, DamageSource source, float amount) {
        ORIGINAL.put(entity, amount);
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(entity, source, amount);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            ORIGINAL.remove(entity);
            return Float.NaN;
        }
        return event.getAmount();
    }

    /**
     * Called by the mixins inside actuallyHurt, after armour and enchantments and before
     * absorption hearts, which is where NeoForge 1.21.1 posts its Pre. (Forge's own
     * LivingDamageEvent comes later, after absorption, so it cannot stand in for it.)
     */
    public static float damagePre(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide()) {
            return amount;
        }
        LivingDamageEvent.Pre pre = new LivingDamageEvent.Pre(entity, source,
                ORIGINAL.getOrDefault(entity, amount), amount);
        MinecraftForge.EVENT_BUS.post(pre);
        return pre.getNewDamage();
    }

    /** Last, after every other mod: what the hit really takes. A cancelled event takes nothing. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onForgeDamageFinal(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        float landed = event.isCanceled() ? 0f : event.getAmount();
        PENDING_POST.put(event.getEntity(), new float[] {event.getAmount(), landed});
    }

    /** Called by the mixins at every return of actuallyHurt. */
    public static void afterActuallyHurt(LivingEntity entity, DamageSource source) {
        // NeoForge skips Post for an invulnerable target, as actuallyHurt does.
        if (entity.level().isClientSide() || entity.isInvulnerableTo(source)) {
            PENDING_POST.remove(entity);
            ORIGINAL.remove(entity);
            return;
        }
        float[] pending = PENDING_POST.remove(entity);
        Float entered = ORIGINAL.remove(entity);
        float original = entered != null ? entered : pending == null ? 0f : pending[0];
        float landed = pending == null ? 0f : pending[1];
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent.Post(entity, source, original, landed));
    }
}
