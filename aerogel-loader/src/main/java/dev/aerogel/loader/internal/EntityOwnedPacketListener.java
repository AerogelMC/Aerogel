package dev.aerogel.loader.internal;

import net.minecraft.world.entity.Entity;

/** Exposes the entity whose mutable state is owned by a packet listener. */
public interface EntityOwnedPacketListener {
    Entity aerogel$packetOwner();
}
