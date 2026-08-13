package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;

/** Fired before a projectile is added to a server level. */
public final class ProjectileLaunchEvent implements CancellableEvent {
    private final ServerLevel level;
    private final Projectile projectile;
    private boolean cancelled;

    public ProjectileLaunchEvent(ServerLevel level, Projectile projectile) {
        this.level = level;
        this.projectile = projectile;
    }

    public ServerLevel level() { return level; }
    public Projectile projectile() { return projectile; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
