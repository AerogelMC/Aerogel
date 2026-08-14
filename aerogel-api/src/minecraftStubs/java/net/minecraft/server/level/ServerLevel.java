package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ServerLevel extends Level {
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
}
