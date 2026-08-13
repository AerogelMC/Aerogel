package dev.aerogel.api.event.player;

/** Fired when a ServerPlayer begins vanilla death handling. */
public record PlayerDeathEvent(Object playerHandle, Object damageSourceHandle) implements PlayerEvent {
    @SuppressWarnings("unchecked") public <D> D damageSource() { return (D) damageSourceHandle; }
}
