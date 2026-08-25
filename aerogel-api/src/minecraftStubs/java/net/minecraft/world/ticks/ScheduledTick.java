package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.Hash;
import net.minecraft.core.BlockPos;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public final class ScheduledTick<T> {
    public static final Hash.Strategy<ScheduledTick<?>> UNIQUE_TICK_HASH = null;

    public static <T> ScheduledTick<T> probe(T type, BlockPos pos) { return null; }
    public T type() { return null; }
    public BlockPos pos() { return null; }
    public long triggerTick() { return 0L; }
}
