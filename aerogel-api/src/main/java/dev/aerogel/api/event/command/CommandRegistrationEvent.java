package dev.aerogel.api.event.command;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.commands.Commands;

/** Fired after vanilla has populated a new Commands instance. */
public record CommandRegistrationEvent(Commands commands) implements AerogelEvent {
}
