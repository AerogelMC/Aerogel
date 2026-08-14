package net.minecraft.server;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.WorldData;

public abstract class MinecraftServer {
    public Collection<ServerPlayer> onlinePlayers() { return null; }
    public Optional<ServerPlayer> findPlayer(String name) { return Optional.empty(); }
    public Optional<ServerPlayer> findPlayer(UUID uniqueId) { return Optional.empty(); }
    public Collection<ServerLevel> loadedLevels() { return null; }
    public void broadcast(Component message) { }
    public void broadcastPacket(Packet<?> packet) { }
    public boolean restart() { return false; }
    public ServerLevel overworld() { return null; }
    public Iterable<ServerLevel> getAllLevels() { return null; }
    public PlayerList getPlayerList() { return null; }
    public RegistryAccess.Frozen registryAccess() { return null; }
    public ServerLevel getLevel(ResourceKey<Level> key) { return null; }
    public boolean isSameThread() { return false; }
    public int getAbsoluteMaxWorldSize() { return 0; }
    public WorldGenSettings getWorldGenSettings() { return null; }
    public WorldData getWorldData() { return null; }

    public boolean acceptsTransfers() {
        return false;
    }
}
