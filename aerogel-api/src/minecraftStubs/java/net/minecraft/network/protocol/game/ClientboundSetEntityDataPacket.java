package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.SynchedEntityData;
import java.util.List;

public record ClientboundSetEntityDataPacket(
    int id, List<SynchedEntityData.DataValue<?>> packedItems
) implements Packet<ClientGamePacketListener> {
}
