package net.minecraft.network.protocol.game;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;

public class ClientboundLevelParticlesPacket implements Packet<ClientGamePacketListener> {
    public <T extends ParticleOptions> ClientboundLevelParticlesPacket(
        T particle, boolean overrideLimiter, boolean alwaysShow,
        double x, double y, double z, float offsetX, float offsetY, float offsetZ,
        float speed, int count
    ) { }
}
