package dk.arko.api.common.events;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight event bus for internal API events (not Bukkit/Velocity events).
 * Supports priorities, cancellation, async events, and annotation-based registration.
 */
public class EventBus {

    private final Map<Class<?>, List<EventSubscription<?>>> subscribers = new ConcurrentHashMap<>();
    private final Map<Object, List<EventSubscription<?>>> listenerMap = new ConcurrentHashMap<>();

    // ─── Registration ──────────────────────────────────────────

    /**
     * Subscribe to an event with a handler.
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> EventSubscription<T> subscribe(Class<T> eventClass, Consumer<T> handler, EventPriority priority) {
        EventSubscription<T> subscription = new EventSubscription<>(eventClass, handler, priority);
        subscribers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(subscription);
        sortSubscribers(eventClass);
        return subscription;
    }

    public <T extends Event> EventSubscription<T> subscribe(Class<T> eventClass, Consumer<T> handler) {
        return subscribe(eventClass, handler, EventPriority.NORMAL);
    }

    /**
     * Register all @Subscribe-annotated methods in a listener class.
     */
    public void registerListener(Object listener) {
        List<EventSubscription<?>> registeredSubs = new ArrayList<>();
        for (Method method : listener.getClass().getDeclaredMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) continue;
            if (method.getParameterCount() != 1) continue;

            Class<?> eventType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(eventType)) continue;

            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) eventType;

            Consumer<Event> handler = event -> {
                try {
                    method.invoke(listener, event);
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking event handler: " + method.getName(), e);
                }
            };

            EventSubscription<Event> sub = new EventSubscription<>(Event.class, handler, annotation.priority());
            subscribers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(sub);
            registeredSubs.add(sub);
        }
        listenerMap.put(listener, registeredSubs);
        subscribers.keySet().forEach(this::sortSubscribers);
    }

    /**
     * Unregister all handlers from a listener.
     */
    public void unregisterListener(Object listener) {
        List<EventSubscription<?>> subs = listenerMap.remove(listener);
        if (subs == null) return;
        subscribers.values().forEach(list -> list.removeAll(subs));
    }

    /**
     * Unregister a single subscription.
     */
    public void unsubscribe(EventSubscription<?> subscription) {
        subscribers.values().forEach(list -> list.remove(subscription));
    }

    // ─── Dispatching ───────────────────────────────────────────

    /**
     * Fire an event to all subscribers.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Event> T fire(T event) {
        List<EventSubscription<?>> subs = subscribers.get(event.getClass());
        if (subs == null) return event;

        for (EventSubscription sub : subs) {
            if (event instanceof Cancellable c && c.isCancelled() && !sub.ignoreCancelled) continue;
            try {
                sub.handler.accept(event);
            } catch (Exception e) {
                System.err.println("[ArkoAPI EventBus] Error handling " + event.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return event;
    }

    /**
     * Fire and check if the event was cancelled.
     */
    public <T extends Event & Cancellable> boolean fireAndCheck(T event) {
        fire(event);
        return !event.isCancelled();
    }

    // ─── Internal ──────────────────────────────────────────────

    private void sortSubscribers(Class<?> eventClass) {
        List<EventSubscription<?>> subs = subscribers.get(eventClass);
        if (subs != null) {
            subs.sort(Comparator.comparingInt(s -> s.priority.ordinal()));
        }
    }

    // ─── Inner Classes ─────────────────────────────────────────

    /**
     * Base event class.
     */
    public static abstract class Event {}

    /**
     * Interface for cancellable events.
     */
    public interface Cancellable {
        boolean isCancelled();
        void setCancelled(boolean cancelled);
    }

    /**
     * Abstract cancellable event.
     */
    public static abstract class CancellableEvent extends Event implements Cancellable {
        private boolean cancelled = false;

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    /**
     * Event subscription handle.
     */
    public static class EventSubscription<T> {
        final Class<T> eventClass;
        final Consumer<T> handler;
        final EventPriority priority;
        boolean ignoreCancelled = false;

        EventSubscription(Class<T> eventClass, Consumer<T> handler, EventPriority priority) {
            this.eventClass = eventClass;
            this.handler = handler;
            this.priority = priority;
        }

        public EventSubscription<T> ignoreCancelled() {
            this.ignoreCancelled = true;
            return this;
        }
    }

    /**
     * Event priority levels.
     */
    public enum EventPriority {
        LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR
    }

    /**
     * Annotation for registering event handlers.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Subscribe {
        EventPriority priority() default EventPriority.NORMAL;
    }
}
