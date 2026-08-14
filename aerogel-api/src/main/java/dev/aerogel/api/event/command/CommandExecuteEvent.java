package dev.aerogel.api.event.command;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.commands.CommandSourceStack;

import java.util.Objects;

/** Fired before vanilla parses and executes a command. */
public final class CommandExecuteEvent implements CancellableEvent {
    private final CommandSourceStack source;
    private String command;
    private boolean cancelled;

    public CommandExecuteEvent(CommandSourceStack source, String command) {
        this.source = source;
        this.command = Objects.requireNonNull(command, "command");
    }

    public CommandSourceStack source() {
        return source;
    }

    public String command() {
        return command;
    }

    public void setCommand(String command) {
        this.command = Objects.requireNonNull(command, "command");
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
