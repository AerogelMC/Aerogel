package dev.aerogel.loader.event;

import dev.aerogel.api.event.block.BlockStateChangeEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Supplier;

/** Carries an exact vanilla operation's block-change origin into Level#setBlock. */
public final class BlockChangeContext {
    private static final ThreadLocal<Deque<Context>> CONTEXTS =
        ThreadLocal.withInitial(ArrayDeque::new);

    private BlockChangeContext() { }

    public static Context current() {
        Context context = CONTEXTS.get().peek();
        return context == null ? Context.DIRECT : context;
    }

    public static <T> T call(
        BlockStateChangeEvent.Reason reason,
        Object sourceEntity,
        Object sourcePosition,
        Object sourceLocation,
        Supplier<T> action
    ) {
        Objects.requireNonNull(action, "action");
        Deque<Context> contexts = CONTEXTS.get();
        contexts.push(new Context(reason, sourceEntity, sourcePosition, sourceLocation));
        try {
            return action.get();
        } finally {
            contexts.pop();
            if (contexts.isEmpty()) CONTEXTS.remove();
        }
    }

    public static void run(
        BlockStateChangeEvent.Reason reason,
        Object sourceEntity,
        Object sourcePosition,
        Object sourceLocation,
        Runnable action
    ) {
        call(reason, sourceEntity, sourcePosition, sourceLocation, () -> {
            action.run();
            return null;
        });
    }

    public record Context(
        BlockStateChangeEvent.Reason reason,
        Object sourceEntity,
        Object sourcePosition,
        Object sourceLocation
    ) {
        private static final Context DIRECT =
            new Context(BlockStateChangeEvent.Reason.DIRECT, null, null, null);

        public Context {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
