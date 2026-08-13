package dev.aerogel.api.event.server;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.MinecraftServer;

/** Fired before a full server save begins. Cancelling skips that save invocation. */
public final class ServerSaveStartEvent implements ServerEvent, CancellableEvent {
    private final MinecraftServer server;
    private final boolean suppressLog;
    private final boolean flush;
    private final boolean force;
    private boolean cancelled;

    public ServerSaveStartEvent(
        MinecraftServer server, boolean suppressLog, boolean flush, boolean force
    ) {
        this.server = server;
        this.suppressLog = suppressLog;
        this.flush = flush;
        this.force = force;
    }

    @Override public MinecraftServer server() { return server; }
    public boolean suppressLog() { return suppressLog; }
    public boolean flush() { return flush; }
    public boolean force() { return force; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
