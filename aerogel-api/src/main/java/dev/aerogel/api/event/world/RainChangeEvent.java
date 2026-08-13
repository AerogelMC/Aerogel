package dev.aerogel.api.event.world;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;

/** Fired before the natural weather cycle changes whether a level is raining. */
public final class RainChangeEvent implements WorldEvent, CancellableEvent {
    private final ServerLevel level;
    private final boolean raining;
    private boolean cancelled;

    public RainChangeEvent(ServerLevel level, boolean raining) {
        this.level = level;
        this.raining = raining;
    }

    @Override public ServerLevel level() { return level; }
    public boolean raining() { return raining; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
