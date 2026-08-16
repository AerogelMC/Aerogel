package dev.aerogel.loader.api;

import dev.aerogel.api.virtualentity.VirtualEntity;
import dev.aerogel.api.virtualentity.VirtualEntityService;
import dev.aerogel.loader.internal.NoopServerEntitySynchronizer;
import dev.aerogel.api.event.EventBus;
import dev.aerogel.api.event.EventPriority;
import dev.aerogel.api.event.player.PlayerQuitEvent;
import dev.aerogel.api.event.player.PlayerTeleportEvent;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectVirtualEntityService implements VirtualEntityService {
    private final PluginApiScope scope;
    private final Set<Virtual> instances = ConcurrentHashMap.newKeySet();
    DirectVirtualEntityService(PluginApiScope scope) { this.scope = scope; }

    void bind(EventBus events) {
        events.listen(PlayerQuitEvent.class, event -> instances.forEach(value -> value.forget(event.player())));
        events.listen(PlayerTeleportEvent.class, EventPriority.MONITOR, true, event -> {
            if (!event.isCancelled() && event.destinationLevel() != event.player().level()) {
                instances.forEach(value -> value.hide(event.player()));
            }
        });
    }

    @Override public VirtualEntity show(Entity entity, Collection<? extends ServerPlayer> viewers) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(viewers, "viewers");
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("A virtual entity must be created for a ServerLevel");
        }
        if (level.getEntity(entity.getId()) == entity) {
            throw new IllegalArgumentException("The entity is already spawned in the level; use per-player view APIs instead");
        }
        Virtual instance = new Virtual(entity, level);
        instances.add(instance);
        for (ServerPlayer viewer : viewers) instance.show(viewer);
        return scope.own(instance);
    }

    private final class Virtual implements VirtualEntity {
        private final Entity entity;
        private final ServerEntity pairing;
        private final Set<ServerPlayer> viewers = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Virtual(Entity entity, ServerLevel level) {
            this.entity = entity;
            pairing = new ServerEntity(level, entity, entity.getType().updateInterval(),
                entity.getType().trackDeltas(), NoopServerEntitySynchronizer.INSTANCE);
        }
        @Override public Entity entity() { return entity; }
        @Override public void show(ServerPlayer player) {
            check(); Objects.requireNonNull(player, "player");
            if (player.level() != entity.level()) {
                throw new IllegalArgumentException("Viewer and virtual entity must be in the same level");
            }
            if (viewers.add(player)) pair(player);
        }
        @Override public void hide(ServerPlayer player) {
            if (viewers.remove(player)) player.sendPacket(new ClientboundRemoveEntitiesPacket(entity.getId()));
        }
        @Override public boolean visibleTo(ServerPlayer player) { return viewers.contains(player); }
        @Override public Collection<ServerPlayer> viewers() { return Set.copyOf(viewers); }
        @Override public void synchronize() {
            check();
            for (ServerPlayer viewer : viewers) {
                viewer.sendPacket(new ClientboundRemoveEntitiesPacket(entity.getId()));
                pair(viewer);
            }
        }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            for (ServerPlayer viewer : Set.copyOf(viewers)) hide(viewer);
            instances.remove(this);
        }
        private void forget(ServerPlayer player) { viewers.remove(player); }
        private void pair(ServerPlayer player) {
            pairing.sendPairingData(player, packet -> player.sendPacket(packet));
        }
        private void check() { if (!active()) throw new IllegalStateException("Virtual entity is closed"); }
    }
}
