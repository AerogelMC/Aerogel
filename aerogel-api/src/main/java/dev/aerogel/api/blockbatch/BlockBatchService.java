package dev.aerogel.api.blockbatch;

import net.minecraft.server.level.ServerLevel;

public interface BlockBatchService {
    BlockBatch create(ServerLevel level);
}
