package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Fired before an entity's invisibility flag changes. */
public final class EntityVisibilityChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final boolean previouslyInvisible;
    private boolean invisible;
    private boolean cancelled;

    public EntityVisibilityChangeEvent(
        Entity entity, boolean previouslyInvisible, boolean invisible
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previouslyInvisible = previouslyInvisible;
        this.invisible = invisible;
    }

    public Entity entity() { return entity; }
    public boolean previouslyInvisible() { return previouslyInvisible; }
    public boolean invisible() { return invisible; }
    public void setInvisible(boolean invisible) { this.invisible = invisible; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
