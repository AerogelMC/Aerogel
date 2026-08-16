package dev.aerogel.loader.network;

/**
 * Process-wide measurements for time spent in Minecraft's inbound packet queue.
 * Recording and commands are both confined to the Minecraft server thread.
 */
public final class PacketQueueMetrics {
    private static final LogarithmicHistogram HISTOGRAM = new LogarithmicHistogram();

    private static long startedNanos = System.nanoTime();
    private static long samples;
    private static long idlePumpSamples;
    private static double totalDelayNanos;
    private static long maximumDelayNanos;
    private static volatile boolean idlePumpEnabled = true;

    private PacketQueueMetrics() {
    }

    public static long markQueued() {
        return System.nanoTime();
    }

    public static boolean idlePumpEnabled() {
        return idlePumpEnabled;
    }

    public static void setIdlePumpEnabled(boolean enabled) {
        idlePumpEnabled = enabled;
        reset();
    }

    public static void record(long queuedAtNanos, boolean idlePump) {
        long handledAtNanos = System.nanoTime();
        if (queuedAtNanos < startedNanos) return;
        recordDelay(Math.max(0L, handledAtNanos - queuedAtNanos), idlePump);
    }

    public static Snapshot snapshot() {
        long now = System.nanoTime();
        return new Snapshot(
            samples,
            idlePumpSamples,
            samples - idlePumpSamples,
            samples == 0L ? 0.0D : totalDelayNanos / samples,
            HISTOGRAM.percentile(0.50D),
            HISTOGRAM.percentile(0.95D),
            HISTOGRAM.percentile(0.99D),
            maximumDelayNanos,
            Math.max(0L, now - startedNanos));
    }

    public static void reset() {
        HISTOGRAM.clear();
        samples = 0L;
        idlePumpSamples = 0L;
        totalDelayNanos = 0.0D;
        maximumDelayNanos = 0L;
        startedNanos = System.nanoTime();
    }

    static void recordDelay(long delayNanos, boolean idlePump) {
        if (delayNanos < 0L) throw new IllegalArgumentException("delayNanos must not be negative");
        samples++;
        if (idlePump) idlePumpSamples++;
        totalDelayNanos += delayNanos;
        maximumDelayNanos = Math.max(maximumDelayNanos, delayNanos);
        HISTOGRAM.record(delayNanos);
    }

    public record Snapshot(
        long samples,
        long idlePumpSamples,
        long tickBoundarySamples,
        double averageDelayNanos,
        long p50DelayNanos,
        long p95DelayNanos,
        long p99DelayNanos,
        long maximumDelayNanos,
        long elapsedNanos
    ) {
        public double idlePumpRatio() {
            return samples == 0L ? 0.0D : (double) idlePumpSamples / samples;
        }

        public double tickBoundaryRatio() {
            return samples == 0L ? 0.0D : (double) tickBoundarySamples / samples;
        }
    }

    /** Bounded histogram with roughly 0.8% relative resolution and no latency ceiling. */
    private static final class LogarithmicHistogram {
        private static final int SIGNIFICANT_BITS = 7;
        private static final int SUB_BUCKETS = 1 << SIGNIFICANT_BITS;
        private static final int LINEAR_BUCKETS = SUB_BUCKETS;
        private static final int SHIFT_BUCKETS = Long.SIZE - SIGNIFICANT_BITS;
        private static final int BUCKET_COUNT = LINEAR_BUCKETS + SHIFT_BUCKETS * SUB_BUCKETS;

        private final long[] buckets = new long[BUCKET_COUNT];
        private long count;

        void record(long value) {
            buckets[index(value)]++;
            count++;
        }

        long percentile(double percentile) {
            if (count == 0L) return 0L;
            long target = Math.max(1L, (long) Math.ceil(count * percentile));
            long seen = 0L;
            for (int index = 0; index < buckets.length; index++) {
                seen += buckets[index];
                if (seen >= target) return upperBound(index);
            }
            return Long.MAX_VALUE;
        }

        void clear() {
            java.util.Arrays.fill(buckets, 0L);
            count = 0L;
        }

        private static int index(long value) {
            if (value < LINEAR_BUCKETS) return (int) value;
            int exponent = Long.SIZE - 1 - Long.numberOfLeadingZeros(value);
            int shift = exponent - SIGNIFICANT_BITS;
            int mantissa = (int) (value >>> shift) - SUB_BUCKETS;
            return LINEAR_BUCKETS + shift * SUB_BUCKETS + mantissa;
        }

        private static long upperBound(int index) {
            if (index < LINEAR_BUCKETS) return index;
            int offset = index - LINEAR_BUCKETS;
            int shift = offset / SUB_BUCKETS;
            long mantissa = SUB_BUCKETS + offset % SUB_BUCKETS;
            if (mantissa + 1L > (Long.MAX_VALUE >>> shift)) {
                return Long.MAX_VALUE;
            }
            return ((mantissa + 1L) << shift) - 1L;
        }
    }
}
