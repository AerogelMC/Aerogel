package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import dev.aerogel.loader.context.PublishedLong2ObjectMap;
import dev.aerogel.loader.internal.EntitySectionStorageBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.entity.EntitySectionStorage;
import dev.aerogel.loader.internal.EntityLoadStatusBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import dev.aerogel.loader.runtime.AerogelRuntime;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager")
abstract class PersistentEntitySectionManagerMixin implements EntityLoadStatusBridge {
    @Shadow @Final @Mutable private Set<UUID> knownUuids;
    @Shadow @Final @Mutable private Long2ObjectMap<Object> chunkVisibility;
    @Shadow @Final @Mutable private Long2ObjectMap<Object> chunkLoadStatuses;
    @Shadow @Final private EntitySectionStorage<?> sectionStorage;
    @Unique private ServerLevel aerogel$level;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$publishManagerIndexesConcurrently(CallbackInfo callback) {
        knownUuids = ConcurrentHashMap.newKeySet();

        Object hidden = chunkVisibility.get(Long.MIN_VALUE);
        ConcurrentLong2ObjectMap<Object> visibility = new ConcurrentLong2ObjectMap<>();
        visibility.defaultReturnValue(hidden);
        chunkVisibility = visibility;
        ((EntitySectionStorageBridge) (Object) sectionStorage)
            .aerogel$visibilitySource(visibility);

        Object fresh = chunkLoadStatuses.get(Long.MIN_VALUE);
        PublishedLong2ObjectMap<Object> loads = new PublishedLong2ObjectMap<>();
        loads.defaultReturnValue(fresh);
        chunkLoadStatuses = loads;
    }

    @Override
    public void aerogel$loadStatusListener(LongConsumer listener) {
        ((PublishedLong2ObjectMap<Object>) chunkLoadStatuses).changeListener(listener);
    }

    @Override
    public void aerogel$level(ServerLevel level) {
        aerogel$level = level;
    }

    /**
     * Entity serialization must observe the same owner-local ordering as movement.
     * Each chunk save is appended to that chunk's Context lane; unrelated chunks
     * remain fully parallel and the server thread never waits for the batch.
     */
    @Redirect(
        method = "autoSave()V",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/"
            + "LongSet;forEach(Lit/unimi/dsi/fastutil/longs/LongConsumer;)V")
    )
    private void aerogel$saveEntityChunksByOwner(
        LongSet chunks, LongConsumer saveChunk
    ) {
        ServerLevel level = aerogel$level;
        if (level == null || !AerogelRuntime.saveEntityChunks(level, chunks, saveChunk)) {
            chunks.forEach(saveChunk);
        }
    }
}
