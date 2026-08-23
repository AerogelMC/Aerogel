package net.minecraft.server.level;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface ChunkResult<T> {
    static <T> ChunkResult<T> error(String message) { return null; }
    boolean isSuccess();
    T orElse(T fallback);
    String getError();
}
