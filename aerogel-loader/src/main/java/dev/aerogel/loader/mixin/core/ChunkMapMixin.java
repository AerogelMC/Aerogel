package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.server.level.ChunkMap")
abstract class ChunkMapMixin implements ChunkMapTrackingBridge {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Int2ObjectMap<Object> entityMap;

    @Override
    public ServerLevel aerogel$level() {
        return level;
    }

    @Override
    public Object aerogel$trackedEntity(int entityId) {
        return entityMap.get(entityId);
    }
}
