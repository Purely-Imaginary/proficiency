package dev.amman.proficiency.platform.bus;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * NeoForge's delivery rules, reimplemented: listeners run in priority order, a listener registered
 * for a supertype also hears every subtype, and once an {@link ICancellableEvent} is canceled the
 * rest of the chain only runs for listeners that asked for {@code receiveCanceled}.
 */
public final class EventBus {

    private record Listener(Class<?> type, EventPriority priority, boolean receiveCanceled,
            long order, Consumer<Event> call) {
    }

    private final List<Listener> listeners = new ArrayList<>();
    private final Map<Class<?>, Listener[]> byType = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> masks = new ConcurrentHashMap<>();
    private long registered;

    public synchronized <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled,
            Class<T> type, Consumer<T> listener) {
        @SuppressWarnings("unchecked")
        Consumer<Event> call = e -> listener.accept((T) e);
        listeners.add(new Listener(type, priority, receiveCanceled, registered++, call));
        byType.clear();
        masks.clear();
    }

    public <T extends Event> void addListener(Class<T> type, Consumer<T> listener) {
        addListener(EventPriority.NORMAL, false, type, listener);
    }

    /** Registers every static {@code @SubscribeEvent} method of the given class. */
    public void register(Class<?> holder) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method method : holder.getDeclaredMethods()) {
            SubscribeEvent sub = method.getAnnotation(SubscribeEvent.class);
            if (sub == null) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalStateException("Bad @SubscribeEvent method " + holder.getName() + "#"
                        + method.getName());
            }
            MethodHandle handle;
            try {
                method.setAccessible(true);
                handle = lookup.unreflect(method);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) method.getParameterTypes()[0];
            MethodHandle bound = handle;
            addListener(sub.priority(), sub.receiveCanceled(), type, event -> {
                try {
                    bound.invoke(event);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            });
        }
    }

    public <T extends Event> T post(T event) {
        Listener[] chain = byType.computeIfAbsent(event.getClass(), this::resolve);
        for (Listener listener : chain) {
            if (event.isCanceled() && !listener.receiveCanceled()) {
                continue;
            }
            listener.call().accept(event);
        }
        return event;
    }

    /**
     * Only the listeners of one priority, in the usual order. NeoForge's bridge relays each real
     * event once per priority, so a listener here runs at the same point among other mods'
     * listeners as it did when it was a NeoForge listener itself.
     */
    public <T extends Event> T post(EventPriority priority, T event) {
        Listener[] chain = byType.computeIfAbsent(event.getClass(), this::resolve);
        for (Listener listener : chain) {
            if (listener.priority() != priority || event.isCanceled() && !listener.receiveCanceled()) {
                continue;
            }
            listener.call().accept(event);
        }
        return event;
    }

    /**
     * Whether anything listens for this event type at this priority. NeoForge's bridge asks before
     * it builds a shared event, so a priority nobody listens at costs one lookup.
     */
    public boolean hasListeners(Class<?> eventType, EventPriority priority) {
        return (masks.computeIfAbsent(eventType, this::mask) & (1 << priority.ordinal())) != 0;
    }

    private int mask(Class<?> eventType) {
        int mask = 0;
        for (Listener listener : byType.computeIfAbsent(eventType, this::resolve)) {
            mask |= 1 << listener.priority().ordinal();
        }
        return mask;
    }

    private synchronized Listener[] resolve(Class<?> eventType) {
        return listeners.stream()
                .filter(l -> l.type().isAssignableFrom(eventType))
                .sorted(Comparator.comparing(Listener::priority).thenComparingLong(Listener::order))
                .toArray(Listener[]::new);
    }
}
