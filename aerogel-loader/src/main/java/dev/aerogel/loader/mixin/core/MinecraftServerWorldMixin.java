package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.MinecraftServerWorldBridge;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerWorldMixin implements MinecraftServerWorldBridge {
    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;
    @Shadow @Final private Executor executor;
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    @Override
    public ServerLevel aerogel$createLevel(
        ResourceKey<Level> levelKey, LevelStem stem, long seed
    ) {
        requireServerThread();
        ServerLevel existing = levels.get(levelKey);
        if (existing != null) return existing;
        MinecraftServer server = (MinecraftServer) (Object) this;

        ServerLevel level = new ServerLevel(
            server,
            executor,
            storageSource,
            new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()),
            levelKey,
            stem,
            server.getWorldData().isDebugWorld(),
            BiomeManager.obfuscateSeed(seed),
            List.of(),
            false
        );

        levels.put(levelKey, level);
        try {
            level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
            server.getPlayerList().addWorldborderListener(level);
            return level;
        } catch (RuntimeException exception) {
            levels.remove(levelKey, level);
            try {
                level.close();
            } catch (IOException suppressed) {
                exception.addSuppressed(suppressed);
            }
            throw exception;
        }
    }

    @Override
    public boolean aerogel$unloadLevel(ResourceKey<Level> levelKey) {
        requireServerThread();
        rejectBuiltIn(levelKey);
        ServerLevel level = levels.get(levelKey);
        if (level == null) return false;

        ServerLevel overworld = ((MinecraftServer) (Object) this).overworld();
        if (level == overworld) {
            throw new IllegalArgumentException("The primary server level cannot be unloaded");
        }
        LevelData.RespawnData respawn = overworld.getLevelData().getRespawnData();
        double x = respawn.pos().getX() + 0.5;
        double y = respawn.pos().getY();
        double z = respawn.pos().getZ() + 0.5;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!player.teleport(overworld, x, y, z, respawn.yaw(), respawn.pitch())) {
                throw new IllegalStateException("Could not move every player out of " + levelKey);
            }
        }

        level.save(null, true, false);
        try {
            level.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close world " + levelKey, exception);
        }
        if (!levels.remove(levelKey, level)) {
            throw new IllegalStateException("World changed while it was being unloaded: " + levelKey);
        }
        return true;
    }

    @Override
    public Path aerogel$worldDirectory() {
        return storageSource.getLevelDirectory().path();
    }

    @Override
    public Path aerogel$dimensionDirectory(ResourceKey<Level> levelKey) {
        rejectBuiltIn(levelKey);
        return storageSource.getDimensionPath(levelKey);
    }

    private void requireServerThread() {
        if (!((MinecraftServer) (Object) this).isSameThread()) {
            throw new IllegalStateException("World lifecycle operations must run on the server thread");
        }
    }

    private static void rejectBuiltIn(ResourceKey<Level> levelKey) {
        if (levelKey.equals(Level.OVERWORLD)
            || levelKey.equals(Level.NETHER)
            || levelKey.equals(Level.END)) {
            throw new IllegalArgumentException("Built-in Minecraft levels cannot be unloaded or deleted");
        }
    }
}
