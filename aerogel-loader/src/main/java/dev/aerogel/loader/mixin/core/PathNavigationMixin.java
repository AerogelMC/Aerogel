package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.PathNavigationBridge;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.ai.navigation.PathNavigation")
abstract class PathNavigationMixin implements PathNavigationBridge {
    @Override
    @Accessor("hasDelayedRecomputation")
    public abstract boolean aerogel$hasDelayedRecomputation();

    @Override
    @Accessor("path")
    public abstract Path aerogel$path();
}
