package dev.amman.proficiency.compat;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Who is applying the effect being asked about. NeoForge's {@code MobEffectEvent.Applicable} carries
 * the source entity; Forge 1.20.1's does not, so {@code mixin.EffectSourceMixin} records the source
 * of every {@code addEffect(effect, source)} call while it runs, and the Applicable handlers read it
 * here. A stack, because a handler may itself add an effect.
 */
public final class EffectSource {

    private static final ThreadLocal<Deque<Entity[]>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private EffectSource() {
    }

    public static void push(@Nullable Entity source) {
        STACK.get().push(new Entity[] {source});
    }

    public static void pop() {
        Deque<Entity[]> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /** The source of the effect being applied right now, or null (none, or not inside addEffect). */
    @Nullable
    public static Entity current() {
        Entity[] top = STACK.get().peek();
        return top == null ? null : top[0];
    }
}
