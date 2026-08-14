package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

import java.util.Objects;

/** Fired before a projectile applies a block or entity hit. */
public final class ProjectileHitEvent implements CancellableEvent {
    private final Projectile projectile;
    private HitResult hitResult;
    private boolean cancelled;

    public ProjectileHitEvent(Projectile projectile, HitResult hitResult) {
        this.projectile = projectile;
        this.hitResult = Objects.requireNonNull(hitResult, "hitResult");
    }

    public Projectile projectile() { return projectile; }
    public HitResult hitResult() { return hitResult; }
    public void setHitResult(HitResult hitResult) {
        this.hitResult = Objects.requireNonNull(hitResult, "hitResult");
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
