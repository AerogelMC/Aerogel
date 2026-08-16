package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.restart.RestartCoordinator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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
        return List.copyOf(server().getPlayerList().getPlayers());
    }

    @Unique
    public Optional<ServerPlayer> findPlayer(String name) {
        return Optional.ofNullable(server().getPlayerList().getPlayerByName(
            Objects.requireNonNull(name, "name")));
    }

    @Unique
    public Optional<ServerPlayer> findPlayer(UUID uniqueId) {
        return Optional.ofNullable(server().getPlayerList().getPlayer(
            Objects.requireNonNull(uniqueId, "uniqueId")));
    }

    @Unique
    public Collection<ServerLevel> loadedLevels() {
        Iterable<ServerLevel> levels = server().getAllLevels();
        List<ServerLevel> result = new ArrayList<>();
        for (ServerLevel level : levels) result.add(level);
        return List.copyOf(result);
    }

    @Unique
    public void broadcast(Component message) {
        server().getPlayerList().broadcastSystemMessage(
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

    @Unique
    private MinecraftServer server() {
        return (MinecraftServer) (Object) this;
    }
}
