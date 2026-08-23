package dev.aerogel.loader.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

/** Exact common view of every 26.2 inbound packet that targets an entity. */
public final class EntityTargetPackets {
    private EntityTargetPackets() { }

    public static boolean targeted(Packet<?> packet) {
        return packet instanceof ServerboundAttackPacket
            || packet instanceof ServerboundInteractPacket;
    }

    public static int targetEntityId(Packet<?> packet) {
        if (packet instanceof ServerboundAttackPacket attack) return attack.entityId();
        if (packet instanceof ServerboundInteractPacket interact) return interact.entityId();
        throw new IllegalArgumentException(
            "Packet does not target an entity: " + packet.getClass().getName());
    }
}
