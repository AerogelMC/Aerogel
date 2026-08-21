package net.minecraft.world.level.entity;

import net.minecraft.world.entity.Entity;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface EntityInLevelCallback {
    void onMove();
    void onRemove(Entity.RemovalReason reason);
}
