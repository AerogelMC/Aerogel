package dev.aerogel.loader.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Separates chunk-local deserialization from the world-owned publications that
 * vanilla performs while constructing a loaded chunk.
 *
 * <p>The scope is active only while one chunk is assembled on its keyed
 * world-generation lane. Light-engine inputs and server-owned index mutations
 * are recorded in encounter order. The caller publishes them on their real
 * owners before exposing the completed chunk future.</p>
 */
public final class ChunkLoadAssemblyScope {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private ChunkLoadAssemblyScope() { }

    public static <T> Result<T> capture(Supplier<? extends T> assembly) {
        Objects.requireNonNull(assembly, "assembly");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested chunk-load assembly");
        }
        Frame frame = new Frame();
        CURRENT.set(frame);
        try {
            return new Result<>(assembly.get(),
                frame.lightPublications.toArray(Runnable[]::new),
                frame.serverPublications.toArray(Runnable[]::new));
        } finally {
            CURRENT.remove();
        }
    }

    public static boolean deferLight(Runnable publication) {
        Frame frame = CURRENT.get();
        if (frame == null) return false;
        frame.lightPublications.add(Objects.requireNonNull(publication, "publication"));
        return true;
    }

    public static boolean deferServer(Runnable publication) {
        Frame frame = CURRENT.get();
        if (frame == null) return false;
        frame.serverPublications.add(Objects.requireNonNull(publication, "publication"));
        return true;
    }

    public record Result<T>(
        T value,
        Runnable[] lightPublications,
        Runnable[] serverPublications
    ) { }

    private static final class Frame {
        private final List<Runnable> lightPublications = new ArrayList<>();
        private final List<Runnable> serverPublications = new ArrayList<>();
    }
}
