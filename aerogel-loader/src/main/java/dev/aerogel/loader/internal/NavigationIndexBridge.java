package dev.aerogel.loader.internal;

import net.minecraft.world.entity.Mob;

public interface NavigationIndexBridge {
    void aerogel$beginNavigationUpdate(Mob mob);
    void aerogel$finishNavigationUpdate(Mob mob);
}
