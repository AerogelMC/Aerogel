package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import dev.aerogel.loader.context.ConcurrentLongSortedSet;
import dev.aerogel.loader.internal.EntitySectionStorageBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.entity.EntitySectionStorage")
abstract class EntitySectionStorageMixin<T> implements EntitySectionStorageBridge {
    @Shadow @Final @Mutable private Long2ObjectFunction<?> intialSectionVisibility;
    @Shadow @Final @Mutable private Long2ObjectMap<T> sections;
    @Shadow @Final @Mutable private LongSortedSet sectionIds;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$publishSectionsConcurrently(CallbackInfo callback) {
        sections = new ConcurrentLong2ObjectMap<>();
        sectionIds = new ConcurrentLongSortedSet();
    }

    @Override
    public void aerogel$visibilitySource(Long2ObjectFunction<?> visibility) {
        intialSectionVisibility = visibility;
    }
}
