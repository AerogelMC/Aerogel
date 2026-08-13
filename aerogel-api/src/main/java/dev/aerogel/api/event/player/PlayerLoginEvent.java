package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.network.chat.Component;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.UUID;

/** Runs after vanilla bans and whitelist checks, before a ServerPlayer is created. */
public final class PlayerLoginEvent implements CancellableEvent {
    private final UUID uniqueId;
    private final String name;
    private final SocketAddress address;
    private Component denialReason;

    public PlayerLoginEvent(UUID uniqueId, String name, SocketAddress address, Component denialReason) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.name = Objects.requireNonNull(name, "name");
        this.address = Objects.requireNonNull(address, "address");
        this.denialReason = denialReason;
    }

    public UUID uniqueId() { return uniqueId; }
    public String name() { return name; }
    public SocketAddress address() { return address; }
    public boolean allowed() { return denialReason == null; }
    public Component denialReason() { return denialReason; }
    public void allow() { denialReason = null; }
    public void deny(Component reason) { denialReason = Objects.requireNonNull(reason, "reason"); }
    @Override public boolean isCancelled() { return !allowed(); }
    @Override public void setCancelled(boolean cancelled) {
        if (!cancelled) {
            allow();
        } else if (denialReason == null) {
            denialReason = Component.literal("Connection denied by a plugin.");
        }
    }
}
