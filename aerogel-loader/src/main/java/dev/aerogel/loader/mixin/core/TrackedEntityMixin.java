package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.TrackedEntityBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class TrackedEntityMixin implements TrackedEntityBridge {
    @Shadow @Final private Set<?> seenBy;

    @Override
    public boolean aerogel$isSeenBy(Object connection) {
        return seenBy.contains(connection);
    }
}
