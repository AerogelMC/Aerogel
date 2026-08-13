package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.restart.RestartCoordinator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerApiMixin {
    @Unique
    public Collection<ServerPlayer> onlinePlayers() {
        Object playerList = EventHooks.call(this, "getPlayerList");
        return List.copyOf(EventHooks.<List<ServerPlayer>>cast(EventHooks.call(playerList, "getPlayers")));
    }

    @Unique
    public Optional<ServerPlayer> findPlayer(String name) {
        Object playerList = EventHooks.call(this, "getPlayerList");
        return Optional.ofNullable(EventHooks.cast(EventHooks.call(playerList, "getPlayerByName",
            Objects.requireNonNull(name, "name"))));
    }

    @Unique
    public Optional<ServerPlayer> findPlayer(UUID uniqueId) {
        Object playerList = EventHooks.call(this, "getPlayerList");
        return Optional.ofNullable(EventHooks.cast(EventHooks.call(playerList, "getPlayer",
            Objects.requireNonNull(uniqueId, "uniqueId"))));
    }

    @Unique
    public Collection<ServerLevel> loadedLevels() {
        Iterable<?> levels = (Iterable<?>) EventHooks.call(this, "getAllLevels");
        List<ServerLevel> result = new ArrayList<>();
        for (Object level : levels) result.add(EventHooks.cast(level));
        return List.copyOf(result);
    }

    @Unique
    public void broadcast(Component message) {
        EventHooks.call(EventHooks.call(this, "getPlayerList"), "broadcastSystemMessage",
            Objects.requireNonNull(message, "message"), false);
    }

    @Unique
    public void broadcastPacket(Packet<?> packet) {
        Objects.requireNonNull(packet, "packet");
        for (ServerPlayer player : onlinePlayers()) player.sendPacket(packet);
    }

    @Unique
    public boolean restart() {
        return RestartCoordinator.request(this);
    }
}
