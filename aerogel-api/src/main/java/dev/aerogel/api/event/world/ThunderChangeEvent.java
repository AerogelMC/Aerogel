package dev.aerogel.api.event.world;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;

/** Fired before the natural weather cycle changes whether a level is thundering. */
public final class ThunderChangeEvent implements WorldEvent, CancellableEvent {
    private final ServerLevel level;
    private final boolean thundering;
    private boolean cancelled;

    public ThunderChangeEvent(ServerLevel level, boolean thundering) {
        this.level = level;
        this.thundering = thundering;
    }

    @Override public ServerLevel level() { return level; }
    public boolean thundering() { return thundering; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
