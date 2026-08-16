package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;

/** Fired before locale, view distance, skin-part, and related client settings are applied. */
public final class PlayerClientInformationEvent extends TypedPlayerPacketEvent<ServerboundClientInformationPacket> {
    private final ClientInformation previousInformation;
    private final ClientInformation newInformation;

    public PlayerClientInformationEvent(ServerPlayer player, ServerboundClientInformationPacket packet) {
        super(player, packet);
        previousInformation = player.clientInformation();
        newInformation = packet.information();
    }

    public ClientInformation previousInformation() { return previousInformation; }
    public ClientInformation newInformation() { return newInformation; }
}
