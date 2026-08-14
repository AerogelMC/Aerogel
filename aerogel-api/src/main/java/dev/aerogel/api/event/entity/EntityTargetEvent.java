package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Fired before a mob changes or clears its current target. */
public final class EntityTargetEvent implements CancellableEvent {
    private final Mob entity;
    private final LivingEntity previousTarget;
    private LivingEntity target;
    private boolean cancelled;

    public EntityTargetEvent(Mob entity, LivingEntity previousTarget, LivingEntity target) {
        this.entity = entity;
        this.previousTarget = previousTarget;
        this.target = target;
    }

    public Mob entity() { return entity; }
    public LivingEntity previousTarget() { return previousTarget; }
    public LivingEntity target() { return target; }
    public void setTarget(LivingEntity target) { this.target = target; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
