package dev.aerogel.api.event.world;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Fired before vanilla starts the first load or generation task for a chunk holder. */
public final class ChunkPreLoadEvent implements WorldEvent, CancellableEvent {
    private final ServerLevel level;
    private final ChunkPos position;
    private final ChunkStatus requestedStatus;
    private boolean cancelled;

    public ChunkPreLoadEvent(
        ServerLevel level, ChunkPos position, ChunkStatus requestedStatus
    ) {
        this.level = level;
        this.position = position;
        this.requestedStatus = requestedStatus;
    }

    @Override public ServerLevel level() { return level; }
    public ChunkPos position() { return position; }
    public ChunkStatus requestedStatus() { return requestedStatus; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
