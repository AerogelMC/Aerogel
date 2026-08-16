package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ClientboundSoundPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSoundPacket(Holder<SoundEvent> sound, SoundSource source,
                                  double x, double y, double z,
                                  float volume, float pitch, long seed) { }
}
