package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.world.level.entity.EntityLookup")
abstract class EntityLookupMixin<T> {
    @Shadow @Final @Mutable private Int2ObjectMap<T> byId;
    @Shadow @Final @Mutable private Map<UUID, T> byUuid;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$publishEntitiesConcurrently(CallbackInfo callback) {
        byId = new ConcurrentInt2ObjectMap<>();
        byUuid = new ConcurrentHashMap<>();
    }
}
