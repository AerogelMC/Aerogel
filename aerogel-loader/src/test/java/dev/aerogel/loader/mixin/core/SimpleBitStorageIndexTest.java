package dev.aerogel.loader.mixin.core;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SimpleBitStorageIndexTest {
    @Test
    void fixedPointIndexMatchesDivisionForEveryOptimizedIndex() throws Exception {
        Field field = SimpleBitStorageMixin.class.getDeclaredField("AEROGEL_MAGIC");
        field.setAccessible(true);
        int[] magic = (int[]) field.get(null);

        for (int bits = 1; bits <= 32; bits++) {
            int valuesPerLong = 64 / bits;
            int multipliedBits = valuesPerLong * bits;
            for (int index = 0; index < 4096; index++) {
                int full = magic[bits] * index;
                int cell = full >>> 20;
                int bitIndex = (full & 0xFFFFF) * multipliedBits >>> 20;

                assertEquals(index / valuesPerLong, cell, "cell: bits=" + bits + ", index=" + index);
                assertEquals(
                    index % valuesPerLong * bits,
                    bitIndex,
                    "bit: bits=" + bits + ", index=" + index
                );
            }
        }
    }
}
