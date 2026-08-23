package dev.aerogel.loader.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

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

    /** Classifies a packet before its write is submitted to Netty's event loop. */
    public static PacketPriority classify(Packet<?> packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet.isTerminal()) return PacketPriority.BARRIER;
        if (current() == PacketPriority.BULK
            || packet instanceof ClientboundLevelChunkWithLightPacket
            || packet instanceof ClientboundChunkBatchStartPacket
            || packet instanceof ClientboundChunkBatchFinishedPacket) {
            return PacketPriority.BULK;
        }
        return PacketPriority.INTERACTIVE;
    }

    public static Runnable carry(Runnable action) {
        Objects.requireNonNull(action, "action");
        PacketPriority captured = current();
        if (captured == PacketPriority.INTERACTIVE) return action;
        return () -> run(captured, action);
    }

    public static void run(PacketPriority priority, Runnable action) {
        Objects.requireNonNull(priority, "priority");
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
