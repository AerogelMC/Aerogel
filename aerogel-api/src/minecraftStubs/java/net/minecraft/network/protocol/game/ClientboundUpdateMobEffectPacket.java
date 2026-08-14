package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.effect.MobEffectInstance;

public class ClientboundUpdateMobEffectPacket implements Packet<ClientGamePacketListener> {
    public ClientboundUpdateMobEffectPacket(int entityId, MobEffectInstance effect, boolean blend) { }
}
