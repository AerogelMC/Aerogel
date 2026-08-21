package dev.aerogel.loader.runtime;

import dev.aerogel.loader.plugin.PluginManager;
import dev.aerogel.loader.api.AerogelApiRuntime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.function.Consumer;
import dev.aerogel.api.context.ContextSnapshot;
import net.minecraft.server.level.ServerPlayer;

public final class AerogelRuntime {
    private static volatile PluginManager pluginManager;
    private static volatile AerogelApiRuntime apiRuntime;
    private static final AtomicBoolean pluginsLoaded = new AtomicBoolean();

    private AerogelRuntime() {
    }

    public static void install(PluginManager manager) {
        if (pluginManager != null) {
            throw new IllegalStateException("Aerogel runtime is already installed");
        }
        pluginManager = Objects.requireNonNull(manager, "manager");
        apiRuntime = manager.apiRuntime();
    }

    public static PluginManager pluginManager() {
        PluginManager current = pluginManager;
        if (current == null) {
            throw new IllegalStateException("Aerogel runtime is not installed");
        }
        return current;
    }

    public static void loadPluginsAfterBootstrap() {
        if (!pluginsLoaded.compareAndSet(false, true)) return;
        try {
            pluginManager().loadEntrypoints();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Aerogel plugins after Minecraft bootstrap", exception);
        }
    }

    public static void attachServer(Object server) {
        api().attach(server);
    }

    public static void tick(Object server) {
        api().tick(server);
    }

    public static void stopContextDispatch() {
        AerogelApiRuntime current = apiRuntime;
        if (current != null) current.contexts().close();
    }

    public static void worldLoaded(ServerLevel level) {
        AerogelApiRuntime current = apiRuntime;
        if (current != null) current.contexts().worldLoaded(level);
    }

    public static void worldUnloaded(ServerLevel level) {
        AerogelApiRuntime current = apiRuntime;
        if (current != null) current.contexts().worldUnloaded(level);
    }

    public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        AerogelApiRuntime current = apiRuntime;
        if (current != null) current.contexts().chunkLoaded(level, chunk);
    }

    public static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        AerogelApiRuntime current = apiRuntime;
        if (current != null) current.contexts().chunkUnloaded(level, chunk);
    }

    public static boolean drainBeforeChunkUnload(
        ServerLevel level, LevelChunk chunk, Runnable unload
    ) {
        AerogelApiRuntime current = apiRuntime;
        return current != null
            && current.contexts().drainBeforeChunkUnload(level, chunk, unload);
    }

    public static void tickEntities(
        ServerLevel level, List<Entity> entities, Consumer<Entity> action
    ) {
        api().contexts().tickEntities(level, entities, action);
    }

    public static void tickChunks(
        ServerLevel level, ChunkMap chunkMap,
        Consumer<net.minecraft.world.level.chunk.LevelChunk> action
    ) {
        api().contexts().tickChunks(level, chunkMap, action);
    }

    public static void tickSpawningChunk(
        ServerLevel level, LevelChunk chunk, Runnable action
    ) {
        api().contexts().tickSpawningChunk(level, chunk, action);
    }

    public static void tickBlockEntities(
        ServerLevel level,
        java.util.List<net.minecraft.world.level.block.entity.TickingBlockEntity> tickers,
        boolean runsNormally
    ) {
        api().contexts().tickBlockEntities(level, tickers, runsNormally);
    }

    public static boolean routeChunkTask(
        ServerLevel level, LevelChunk chunk, Runnable action
    ) {
        return api().contexts().routeChunkTask(level, chunk, action);
    }

    public static boolean routeBlockTask(
        ServerLevel level, net.minecraft.core.BlockPos position, Runnable action
    ) {
        return api().contexts().routeBlockTask(level, position, action);
    }

    public static boolean routeOutputSignalUpdate(
        ServerLevel level,
        net.minecraft.core.BlockPos position,
        net.minecraft.world.level.block.Block sourceBlock
    ) {
        return api().contexts().routeOutputSignalUpdate(level, position, sourceBlock);
    }

    public static boolean routeBlockEffects(
        ServerLevel level, Iterable<net.minecraft.core.BlockPos> positions, Runnable action
    ) {
        return api().contexts().routeBlockEffects(level, positions, action);
    }

    public static boolean isBlockMutationThread(
        ServerLevel level, net.minecraft.core.BlockPos position
    ) {
        return level.getServer().isSameThread()
            || api().contexts().isBlockOwnerContext(level, position);
    }

    public static boolean isChunkOwnerContext(ServerLevel level, LevelChunk chunk) {
        return api().contexts().isChunkOwnerContext(level, chunk);
    }

    public static boolean routeEntityTask(Entity entity, Runnable action) {
        return api().contexts().routeEntityTask(entity, action);
    }

    public static boolean routeEntityBlockTask(
        Entity entity,
        ServerLevel level,
        net.minecraft.core.BlockPos position,
        Runnable action
    ) {
        return api().contexts().routeEntityBlockTask(entity, level, position, action);
    }

    public static boolean isEntityMutationThread(Entity entity) {
        return entity.level() instanceof ServerLevel level && level.getServer().isSameThread()
            || api().contexts().isEntityOwnerContext(entity);
    }

    public static ContextSnapshot playerChunkSnapshot(ServerPlayer player) {
        ServerLevel level = player.level();
        net.minecraft.world.level.ChunkPos position = player.chunkPosition();
        return api().contexts().world(level)
            .chunk(position.x(), position.z()).snapshot();
    }

    private static AerogelApiRuntime api() {
        AerogelApiRuntime current = apiRuntime;
        if (current == null) {
            throw new IllegalStateException("Aerogel API runtime is not installed");
        }
        return current;
    }
}
