package dev.aerogel.api.virtualentity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public interface VirtualEntityService {
    VirtualEntity show(Entity entity, Collection<? extends ServerPlayer> viewers);
    default VirtualEntity show(Entity entity, ServerPlayer viewer) { return show(entity, java.util.List.of(viewer)); }
}
