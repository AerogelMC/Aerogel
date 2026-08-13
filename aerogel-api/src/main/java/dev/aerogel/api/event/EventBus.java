package dev.aerogel.api.event;

import java.util.function.Consumer;

/** Plugin-owned access to Aerogel's typed synchronous event bus. */
public interface EventBus {
    default <E extends AerogelEvent> EventRegistration listen(
        Class<E> eventType, Consumer<? super E> listener
    ) {
        return listen(eventType, EventPriority.NORMAL, false, listener);
    }

    default <E extends AerogelEvent> EventRegistration listen(
        Class<E> eventType, EventPriority priority, Consumer<? super E> listener
    ) {
        return listen(eventType, priority, false, listener);
    }

    <E extends AerogelEvent> EventRegistration listen(
        Class<E> eventType,
        EventPriority priority,
        boolean receiveCancelled,
        Consumer<? super E> listener
    );

    <E extends AerogelEvent> E post(E event);
}
