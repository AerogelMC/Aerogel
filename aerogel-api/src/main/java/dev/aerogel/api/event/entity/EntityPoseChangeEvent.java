package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

import java.util.Objects;

/** Fired before an entity's pose changes. */
public final class EntityPoseChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final Pose previous;
    private Pose pose;
    private boolean cancelled;

    public EntityPoseChangeEvent(Entity entity, Pose previous, Pose pose) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previous = Objects.requireNonNull(previous, "previous");
        this.pose = Objects.requireNonNull(pose, "pose");
    }

    public Entity entity() { return entity; }
    public Pose previous() { return previous; }
    public Pose pose() { return pose; }
    public void setPose(Pose pose) { this.pose = Objects.requireNonNull(pose, "pose"); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
