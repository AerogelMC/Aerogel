package dev.aerogel.loader.internal;

import net.minecraft.world.entity.Entity;

/** An owner-local follow-up operation paired with its authoritative entity. */
public interface ContextOwnedEntityTask {
    Entity aerogel$entity();
    void aerogel$run();
}
