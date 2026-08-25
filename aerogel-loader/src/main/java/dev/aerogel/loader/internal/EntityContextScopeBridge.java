package dev.aerogel.loader.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface EntityContextScopeBridge {
    BlockPos aerogel$additionalContextBlock(ServerLevel level);
}
