package dev.aerogel.loader.network;

/** Internal bridge added to Minecraft's queued packet entry by Mixin. */
public interface QueuedPacketBridge {
    void aerogel$handleQueuedPacket(boolean idlePump);
}
