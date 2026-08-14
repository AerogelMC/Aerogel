package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

import java.util.Objects;

/** Fired before two animals create their child. */
public final class EntityBreedEvent implements CancellableEvent {
    private final ServerLevel level;
    private final Animal parent;
    private Animal partner;
    private boolean cancelled;

    public EntityBreedEvent(ServerLevel level, Animal parent, Animal partner) {
        this.level = level;
        this.parent = parent;
        this.partner = Objects.requireNonNull(partner, "partner");
    }

    public ServerLevel level() { return level; }
    public Animal parent() { return parent; }
    public Animal partner() { return partner; }
    public void setPartner(Animal partner) { this.partner = Objects.requireNonNull(partner, "partner"); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
