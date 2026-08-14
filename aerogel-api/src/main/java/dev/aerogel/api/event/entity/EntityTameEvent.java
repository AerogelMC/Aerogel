package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/** Fired before an animal becomes tamed by a player. */
public final class EntityTameEvent implements CancellableEvent {
    private final TamableAnimal entity;
    private Player player;
    private boolean cancelled;

    public EntityTameEvent(TamableAnimal entity, Player player) {
        this.entity = entity;
        this.player = Objects.requireNonNull(player, "player");
    }

    public TamableAnimal entity() { return entity; }
    public Player player() { return player; }
    public void setPlayer(Player player) { this.player = Objects.requireNonNull(player, "player"); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
