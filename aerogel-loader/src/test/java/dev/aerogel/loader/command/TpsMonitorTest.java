package dev.aerogel.loader.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TpsMonitorTest {
    @BeforeEach
    void reset() {
        TpsMonitor.reset();
    }

    @Test
    void movingAveragesReactAtDifferentRates() {
        long time = 1_000_000_000L;
        TpsMonitor.tick(time);
        for (int tick = 0; tick < 20; tick++) {
            time += 100_000_000L;
            TpsMonitor.tick(time);
        }

        TpsMonitor.Snapshot snapshot = TpsMonitor.snapshot();
        assertTrue(snapshot.oneMinute() < snapshot.fiveMinutes());
        assertTrue(snapshot.fiveMinutes() < snapshot.fifteenMinutes());
        assertTrue(snapshot.fifteenMinutes() < 20.0);
    }
}
