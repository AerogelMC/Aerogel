package net.minecraft.server.level;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface ChunkResult<T> {
    T orElse(T fallback);
    String getError();
}
