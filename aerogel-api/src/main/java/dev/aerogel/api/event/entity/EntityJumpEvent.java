package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Fired immediately before a living entity performs its vanilla ground jump. */
public final class EntityJumpEvent implements CancellableEvent {
    private final LivingEntity entity;
    private boolean cancelled;

    public EntityJumpEvent(LivingEntity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public LivingEntity entity() { return entity; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
