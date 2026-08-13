package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.AerogelEvent;

public record EntityRemoveEvent(Object entityHandle, Object reasonHandle) implements AerogelEvent {
    @SuppressWarnings("unchecked") public <E> E entity() { return (E) entityHandle; }
    @SuppressWarnings("unchecked") public <R> R reason() { return (R) reasonHandle; }
}
