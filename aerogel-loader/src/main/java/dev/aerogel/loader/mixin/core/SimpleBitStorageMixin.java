package dev.aerogel.loader.mixin.core;

import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.util.SimpleBitStorage")
abstract class SimpleBitStorageMixin {
    @Unique private static final int[] AEROGEL_MAGIC = aerogel$createMagic();

    @Shadow @Final private long[] data;
    @Shadow @Final private int bits;
    @Shadow @Final private long mask;
    @Shadow @Final private int size;
    @Shadow @Final private int valuesPerLong;
    @Shadow private native int cellIndex(int index);

    @Unique private int aerogel$magic;
    @Unique private int aerogel$mulBits;

    @Unique
    private static int[] aerogel$createMagic() {
        int[] magic = new int[33];
        for (int bits = 1; bits < magic.length; bits++) {
            int divisor = 64 / bits;
            magic[bits] = ((1 << 20) + divisor - 1) / divisor;
        }
        return magic;
    }

    @Inject(method = "<init>(II[J)V", at = @At("RETURN"))
    private void aerogel$initializeFastIndex(int bits, int size, long[] values, CallbackInfo callback) {
        aerogel$magic = AEROGEL_MAGIC[bits];
        aerogel$mulBits = (64 / bits) * bits;
    }

    /**
     * @author Spottedleaf, Aerogel
     * @reason Compute quotient and remainder together with exact fixed-point arithmetic.
     */
    @Overwrite
    public int getAndSet(int index, int value) {
        Validate.inclusiveBetween(0L, size - 1L, (long) index);
        Validate.inclusiveBetween(0L, mask, (long) value);
        if (size > 4096) return aerogel$getAndSetFallback(index, value);

        int full = aerogel$magic * index;
        int cell = full >>> 20;
        int bitIndex = (full & 0xFFFFF) * aerogel$mulBits >>> 20;
        long old = data[cell];
        data[cell] = old & ~(mask << bitIndex) | ((long) value & mask) << bitIndex;
        return (int) (old >>> bitIndex & mask);
    }

    /**
     * @author Spottedleaf, Aerogel
     * @reason Compute quotient and remainder together with exact fixed-point arithmetic.
     */
    @Overwrite
    public void set(int index, int value) {
        Validate.inclusiveBetween(0L, size - 1L, (long) index);
        Validate.inclusiveBetween(0L, mask, (long) value);
        if (size > 4096) {
            aerogel$setFallback(index, value);
            return;
        }

        int full = aerogel$magic * index;
        int cell = full >>> 20;
        int bitIndex = (full & 0xFFFFF) * aerogel$mulBits >>> 20;
        long old = data[cell];
        data[cell] = old & ~(mask << bitIndex) | ((long) value & mask) << bitIndex;
    }

    /**
     * @author Spottedleaf, Aerogel
     * @reason Compute quotient and remainder together with exact fixed-point arithmetic.
     */
    @Overwrite
    public int get(int index) {
        Validate.inclusiveBetween(0L, size - 1L, (long) index);
        if (size > 4096) return aerogel$getFallback(index);

        int full = aerogel$magic * index;
        int cell = full >>> 20;
        int bitIndex = (full & 0xFFFFF) * aerogel$mulBits >>> 20;
        return (int) (data[cell] >>> bitIndex & mask);
    }

    @Unique
    private int aerogel$getFallback(int index) {
        int cell = cellIndex(index);
        int bitIndex = (index - cell * valuesPerLong) * bits;
        return (int) (data[cell] >>> bitIndex & mask);
    }

    @Unique
    private void aerogel$setFallback(int index, int value) {
        int cell = cellIndex(index);
        int bitIndex = (index - cell * valuesPerLong) * bits;
        long old = data[cell];
        data[cell] = old & ~(mask << bitIndex) | ((long) value & mask) << bitIndex;
    }

    @Unique
    private int aerogel$getAndSetFallback(int index, int value) {
        int cell = cellIndex(index);
        int bitIndex = (index - cell * valuesPerLong) * bits;
        long old = data[cell];
        data[cell] = old & ~(mask << bitIndex) | ((long) value & mask) << bitIndex;
        return (int) (old >>> bitIndex & mask);
    }
}
