package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ServerLevel extends Level {
    public ServerLevel(
        MinecraftServer server,
        Executor executor,
        LevelStorageSource.LevelStorageAccess storage,
        ServerLevelData levelData,
        ResourceKey<Level> levelKey,
        LevelStem stem,
        boolean debug,
        long biomeSeed,
        List<CustomSpawner> customSpawners,
        boolean tickTime
    ) {
    }

    public ServerChunkCache getChunkSource() { return null; }
    public MinecraftServer getServer() { return null; }
    public String identifier() { return null; }
    public Collection<Entity> entities() { return null; }
    public Optional<Entity> findEntity(UUID uniqueId) { return Optional.empty(); }
    public Optional<Entity> findEntity(int entityId) { return Optional.empty(); }
    public Collection<Entity> nearbyEntities(double x, double y, double z, double radius) { return null; }
    public Collection<Entity> nearbyEntities(double x, double y, double z, double radius, Predicate<Entity> filter) {
        return null;
    }
    public void clearWeather(int durationTicks) { }
    public void rain(int durationTicks) { }
    public void thunder(int durationTicks) { }
    public BlockState block(int x, int y, int z) { return null; }
    public boolean block(int x, int y, int z, BlockState state, int flags) { return false; }
    public boolean spawn(Entity entity) { return false; }
    public boolean teleport(ServerPlayer player, double x, double y, double z) { return false; }
    public boolean teleport(ServerPlayer player, double x, double y, double z, float yaw, float pitch) { return false; }

    public BlockState getBlockState(BlockPos position) { return null; }
    public boolean setBlock(BlockPos position, BlockState state, int flags) { return false; }
    public boolean addFreshEntity(Entity entity) { return false; }
    public Iterable<Entity> getAllEntities() { return null; }
    public Entity getEntity(int id) { return null; }
    public Entity getEntityInAnyDimension(UUID uniqueId) { return null; }
    public long getGameTime() { return 0; }
    public long getDayTime() { return 0; }
    public void setDayTime(long time) { }
    public List<ServerPlayer> players() { return null; }
    public void save(ProgressListener listener, boolean flush, boolean skipSave) { }
    public void close() throws IOException { }
    public WorldBorder getWorldBorder() { return null; }
    public Holder<DimensionType> dimensionTypeRegistration() { return null; }
}
