package dev.aerogel.loader.context;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Exact block context that caused a synchronous interaction result such as a menu. */
public final class BlockInteractionScope {
    private static final ContextWorkerLocal<Binding> CURRENT = ContextWorkerLocal.create();

    private BlockInteractionScope() { }

    public static Binding current() {
        return CURRENT.get();
    }

    public static Runnable bind(ServerLevel level, BlockPos position, Runnable action) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        Binding binding = new Binding(level, position.immutable());
        return () -> {
            Binding previous = CURRENT.get();
            CURRENT.set(binding);
            try {
                action.run();
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    public record Binding(ServerLevel level, BlockPos position) { }
}
