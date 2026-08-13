package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/** Fired before an animal becomes tamed by a player. */
public final class EntityTameEvent implements CancellableEvent {
    private final TamableAnimal entity;
    private final Player player;
    private boolean cancelled;

    public EntityTameEvent(TamableAnimal entity, Player player) {
        this.entity = entity;
        this.player = player;
    }

    public TamableAnimal entity() { return entity; }
    public Player player() { return player; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
