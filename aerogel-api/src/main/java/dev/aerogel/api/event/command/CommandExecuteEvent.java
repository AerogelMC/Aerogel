package dev.aerogel.api.event.command;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.commands.CommandSourceStack;

/** Fired before vanilla parses and executes a command. */
public final class CommandExecuteEvent implements CancellableEvent {
    private final CommandSourceStack source;
    private final String command;
    private boolean cancelled;

    public CommandExecuteEvent(CommandSourceStack source, String command) {
        this.source = source;
        this.command = command;
    }

    public CommandSourceStack source() {
        return source;
    }

    public String command() {
        return command;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
