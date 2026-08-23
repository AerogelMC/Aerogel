package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.function.Consumer;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public final class NaturalSpawner {
    // Deliberately non-final in the compile stub so javac emits GETSTATIC and
    // reads the Minecraft version's real constant instead of inlining a stub value.
    public static int INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK;

    private NaturalSpawner() { }

    public static SpawnState createState(
        int spawnableChunks, Iterable<Entity> entities,
        ChunkGetter chunkGetter, LocalMobCapCalculator localCaps
    ) { return null; }

    public static List<MobCategory> getFilteredSpawningCategories(
        SpawnState state, boolean spawnEnemies, boolean spawnPersistent
    ) { return null; }

    public static class SpawnState {
        public int getSpawnableChunkCount() { return 0; }
        public Object2IntMap<MobCategory> getMobCategoryCounts() { return null; }
    }

    @FunctionalInterface
    public interface ChunkGetter {
        void query(long chunkPosition, Consumer<LevelChunk> action);
    }
}
