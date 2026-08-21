package net.minecraft.world.ticks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.BiConsumer;
import java.util.function.LongPredicate;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class LevelTicks<T> {
    public LevelTicks(LongPredicate tickCheck) { }
    public void addContainer(ChunkPos position, LevelChunkTicks<T> container) { }
    public void removeContainer(ChunkPos position) { }
    public void schedule(ScheduledTick<T> tick) { }
    public void tick(long gameTime, int maxTicks, BiConsumer<BlockPos, T> action) { }
    public boolean hasScheduledTick(BlockPos position, T type) { return false; }
    public boolean willTickThisTick(BlockPos position, T type) { return false; }
    public int count() { return 0; }
}
