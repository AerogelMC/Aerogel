package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.world.entity.Entity;

public record EntityRemoveEvent(Entity entity, Entity.RemovalReason reason) implements AerogelEvent {
}
