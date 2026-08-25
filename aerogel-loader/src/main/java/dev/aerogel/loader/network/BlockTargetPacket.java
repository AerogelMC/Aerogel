package dev.aerogel.loader.network;

import net.minecraft.core.BlockPos;

/** Semantic target shared by every serverbound packet that acts on one block. */
public interface BlockTargetPacket {
    BlockPos aerogel$targetBlock();
}
