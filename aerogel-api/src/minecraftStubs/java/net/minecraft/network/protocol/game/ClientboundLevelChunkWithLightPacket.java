package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import java.util.BitSet;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine light,
                                                BitSet sky, BitSet block) { }
}
