package dev.aerogel.loader.internal;

import net.minecraft.world.level.pathfinder.Path;

public interface PathNavigationBridge {
    boolean aerogel$hasDelayedRecomputation();
    Path aerogel$path();
}
