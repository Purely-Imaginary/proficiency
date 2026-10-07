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
    private long registered;

    public synchronized <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled,
            Class<T> type, Consumer<T> listener) {
        @SuppressWarnings("unchecked")
        Consumer<Event> call = e -> listener.accept((T) e);
        listeners.add(new Listener(type, priority, receiveCanceled, registered++, call));
        byType.clear();
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

    private synchronized Listener[] resolve(Class<?> eventType) {
        return listeners.stream()
                .filter(l -> l.type().isAssignableFrom(eventType))
                .sorted(Comparator.comparing(Listener::priority).thenComparingLong(Listener::order))
                .toArray(Listener[]::new);
    }
}
