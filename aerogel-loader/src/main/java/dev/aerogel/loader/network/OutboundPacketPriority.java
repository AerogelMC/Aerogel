package dev.aerogel.loader.network;

import java.util.Objects;

/** Carries a packet dependency class across an off-event-loop send task. */
public final class OutboundPacketPriority {
    private static final ThreadLocal<PacketPriority> CURRENT =
        ThreadLocal.withInitial(() -> PacketPriority.INTERACTIVE);

    private OutboundPacketPriority() {
    }

    public static PacketPriority current() {
        return CURRENT.get();
    }

    public static void runBulk(Runnable action) {
        run(PacketPriority.BULK, action);
    }

    public static Runnable carry(Runnable action) {
        Objects.requireNonNull(action, "action");
        PacketPriority captured = current();
        if (captured == PacketPriority.INTERACTIVE) return action;
        return () -> run(captured, action);
    }

    private static void run(PacketPriority priority, Runnable action) {
        Objects.requireNonNull(action, "action");
        PacketPriority previous = CURRENT.get();
        CURRENT.set(priority);
        try {
            action.run();
        } finally {
            if (previous == PacketPriority.INTERACTIVE) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
