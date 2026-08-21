package net.minecraft.server.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class PlayerChunkSender {
    public void markChunkPendingToSend(LevelChunk chunk) { }
    private static void sendChunk(
        ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk
    ) { }
}
