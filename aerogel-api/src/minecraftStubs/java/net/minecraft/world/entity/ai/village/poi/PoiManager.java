package net.minecraft.world.entity.ai.village.poi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PoiManager {
    public static final int MAX_VILLAGE_DISTANCE = 6;

    public enum Occupancy {
        HAS_SPACE, IS_OCCUPIED, ANY;
        public Predicate<? super PoiRecord> getTest() { return record -> true; }
    }

    public Stream<PoiRecord> getInRange(
        Predicate<Holder<PoiType>> type,
        BlockPos position,
        int radius,
        Occupancy occupancy
    ) { return Stream.empty(); }

    public Optional<BlockPos> take(
        Predicate<Holder<PoiType>> type,
        BiPredicate<Holder<PoiType>, BlockPos> positionFilter,
        BlockPos position,
        int radius
    ) { return Optional.empty(); }

    public boolean release(BlockPos position) { return false; }
    public java.util.concurrent.CompletableFuture<?> prefetch(
        net.minecraft.world.level.ChunkPos position) { return null; }
}
