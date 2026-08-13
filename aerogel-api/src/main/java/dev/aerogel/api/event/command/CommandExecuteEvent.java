package dev.aerogel.api.event.command;

import dev.aerogel.api.event.CancellableEvent;

/** Fired before vanilla parses and executes a command. */
public final class CommandExecuteEvent implements CancellableEvent {
    private final Object sourceHandle;
    private final String command;
    private boolean cancelled;

    public CommandExecuteEvent(Object sourceHandle, String command) {
        this.sourceHandle = sourceHandle;
        this.command = command;
    }

    @SuppressWarnings("unchecked")
    public <S> S source() {
        return (S) sourceHandle;
    }

    public String command() {
        return command;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
