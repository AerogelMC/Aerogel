package net.minecraft.network.protocol.game;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class ClientboundSetEquipmentPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetEquipmentPacket(int entity, List<Pair<EquipmentSlot, ItemStack>> slots) { }
    public int getEntity() { return 0; }
    public List<Pair<EquipmentSlot, ItemStack>> getSlots() { return null; }
}
