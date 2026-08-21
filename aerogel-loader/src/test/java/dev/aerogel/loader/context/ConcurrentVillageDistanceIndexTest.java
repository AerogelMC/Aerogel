package dev.aerogel.loader.context;

import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConcurrentVillageDistanceIndexTest {
    @Test
    void returnsExactChebyshevDistanceWithinTheVanillaLimit() {
        ConcurrentVillageDistanceIndex index = new ConcurrentVillageDistanceIndex();
        long origin = SectionPos.asLong(10, 4, -8);
        index.publish(SectionPos.asLong(14, 2, -5), true);
        index.publish(SectionPos.asLong(9, 10, -8), true);

        assertEquals(4, index.distance(origin, 6));
        assertEquals(4, index.distance(origin, 3));
    }

    @Test
    void removalAndOutOfRangeCentersPreserveVanillaSentinel() {
        ConcurrentVillageDistanceIndex index = new ConcurrentVillageDistanceIndex();
        long origin = SectionPos.asLong(0, 0, 0);
        long center = SectionPos.asLong(2, -1, 2);
        index.publish(center, true);
        assertEquals(2, index.distance(origin, 6));

        index.publish(center, false);
        index.publish(SectionPos.asLong(7, 0, 0), true);
        assertEquals(7, index.distance(origin, 6));
    }
}
