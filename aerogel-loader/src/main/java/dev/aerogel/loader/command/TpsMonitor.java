package dev.aerogel.loader.command;

public final class TpsMonitor {
    private static final int SAMPLE_TICKS = 20;
    private static long sampleStartedNanos;
    private static int sampledTicks;
    private static volatile double oneMinute = 20.0;
    private static volatile double fiveMinutes = 20.0;
    private static volatile double fifteenMinutes = 20.0;

    private TpsMonitor() {
    }

    public static void tick(long nowNanos) {
        if (sampleStartedNanos == 0L) {
            sampleStartedNanos = nowNanos;
            return;
        }
        sampledTicks++;
        if (sampledTicks < SAMPLE_TICKS) {
            return;
        }
        double elapsedSeconds = (nowNanos - sampleStartedNanos) / 1_000_000_000.0;
        if (elapsedSeconds > 0.0) {
            double current = sampledTicks / elapsedSeconds;
            oneMinute = average(oneMinute, current, elapsedSeconds, 60.0);
            fiveMinutes = average(fiveMinutes, current, elapsedSeconds, 300.0);
            fifteenMinutes = average(fifteenMinutes, current, elapsedSeconds, 900.0);
        }
        sampledTicks = 0;
        sampleStartedNanos = nowNanos;
    }

    public static Snapshot snapshot() {
        return new Snapshot(oneMinute, fiveMinutes, fifteenMinutes);
    }

    static void reset() {
        sampleStartedNanos = 0L;
        sampledTicks = 0;
        oneMinute = 20.0;
        fiveMinutes = 20.0;
        fifteenMinutes = 20.0;
    }

    private static double average(double previous, double current, double elapsedSeconds, double windowSeconds) {
        double weight = Math.exp(-elapsedSeconds / windowSeconds);
        return previous * weight + current * (1.0 - weight);
    }

    public record Snapshot(double oneMinute, double fiveMinutes, double fifteenMinutes) {
    }
}
