package dev.aerogel.api.event;

/** An event whose associated vanilla action can be prevented before it occurs. */
public interface CancellableEvent extends AerogelEvent {
    boolean isCancelled();

    void setCancelled(boolean cancelled);

    default void cancel() {
        setCancelled(true);
    }
}
