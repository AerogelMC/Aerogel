package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import dev.aerogel.loader.internal.EntitySectionStorageBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.entity.EntitySectionStorage;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager")
abstract class PersistentEntitySectionManagerMixin {
    @Shadow @Final @Mutable private Set<UUID> knownUuids;
    @Shadow @Final @Mutable private Long2ObjectMap<Object> chunkVisibility;
    @Shadow @Final @Mutable private Long2ObjectMap<Object> chunkLoadStatuses;
    @Shadow @Final private EntitySectionStorage<?> sectionStorage;

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
        ConcurrentLong2ObjectMap<Object> loads = new ConcurrentLong2ObjectMap<>();
        loads.defaultReturnValue(fresh);
        chunkLoadStatuses = loads;
    }
}
