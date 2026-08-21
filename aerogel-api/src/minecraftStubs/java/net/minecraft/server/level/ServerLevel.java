package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.util.random.WeightedList;
import net.minecraft.sounds.SoundEvent;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ServerLevel extends Level {
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { return null; }
    public void unload(LevelChunk chunk) { }
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
    public SavedDataStorage getDataStorage() { return null; }
    public MinecraftServer getServer() { return null; }
    public String identifier() { return null; }
    public Collection<Entity> entities() { return null; }
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
    public boolean tryAddFreshEntityWithPassengers(Entity entity) { return false; }
    public Iterable<Entity> getAllEntities() { return null; }
    public Entity getEntity(int id) { return null; }
    public Entity getEntityInAnyDimension(UUID uniqueId) { return null; }
    public long getDayTime() { return 0; }
    public long getSeed() { return 0; }
    public boolean isVillage(BlockPos position) { return false; }
    public boolean isVillage(SectionPos position) { return false; }
    public boolean isCloseToVillage(BlockPos position, int sections) { return false; }
    public int sectionsToVillage(SectionPos position) { return 0; }
    public Player getNearestPlayer(
        TargetingConditions conditions, LivingEntity source,
        double x, double y, double z
    ) { return null; }
    public void setDayTime(long time) { }
    public List<ServerPlayer> players() { return null; }
    public void save(ProgressListener listener, boolean flush, boolean skipSave) { }
    public void close() throws IOException { }
    public boolean setChunkForced(int chunkX, int chunkZ, boolean forced) { return false; }
    public WorldBorder getWorldBorder() { return null; }
    public float getRainLevel(float partialTick) { return 0; }
    public float getThunderLevel(float partialTick) { return 0; }
    public Holder<DimensionType> dimensionTypeRegistration() { return null; }
    public WeatherData getWeatherData() { return null; }
    public void explode(Entity source, DamageSource damageSource,
                        ExplosionDamageCalculator calculator,
                        double x, double y, double z, float radius, boolean fire,
                        Level.ExplosionInteraction interaction,
                        ParticleOptions smallParticle, ParticleOptions largeParticle,
                        WeightedList<ExplosionParticleInfo> particles,
                        Holder<SoundEvent> sound) { }
    public void sendBlockUpdated(
        BlockPos position, BlockState oldState, BlockState newState, int flags) { }
    public void blockEvent(BlockPos position, Block block, int type, int data) { }
    public void tickBlock(BlockPos position, Block block) { }
    public void tickFluid(BlockPos position, Fluid fluid) { }
    public void updatePOIOnBlockStateChange(
        BlockPos position, BlockState oldState, BlockState newState) { }
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) { }
}
