package dev.aerogel.api.event.command;

import dev.aerogel.api.event.AerogelEvent;

/** Fired after vanilla has populated a new Commands instance. */
public record CommandRegistrationEvent(Object commandsHandle) implements AerogelEvent {
    @SuppressWarnings("unchecked")
    public <C> C commands() {
        return (C) commandsHandle;
    }
}
