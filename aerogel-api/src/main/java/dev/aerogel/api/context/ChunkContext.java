package dev.aerogel.api.context;

import net.minecraft.world.level.ChunkPos;

/**
 * Serial ownership domain for one chunk. A context may migrate between workers;
 * it is not permanently bound to a thread.
 */
public interface ChunkContext {
    int chunkX();

    int chunkZ();

    boolean current();

    void execute(Runnable task);

    void executeNeighborhood(int radius, Runnable task);

    /** Executes while owning exactly this context and the supplied chunk contexts. */
    void executeScope(Iterable<ChunkPos> chunks, Runnable task);

    void assertCurrent();

    ContextSnapshot snapshot();
}
