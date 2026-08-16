package dev.aerogel.loader.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketQueueMetricsTest {
    @AfterEach
    void resetMetrics() {
        PacketQueueMetrics.reset();
    }

    @Test
    void reportsDistributionAndProcessingPath() {
        PacketQueueMetrics.reset();
        PacketQueueMetrics.recordDelay(1_000_000L, true);
        PacketQueueMetrics.recordDelay(2_000_000L, true);
        PacketQueueMetrics.recordDelay(3_000_000L, false);
        PacketQueueMetrics.recordDelay(4_000_000L, false);

        PacketQueueMetrics.Snapshot snapshot = PacketQueueMetrics.snapshot();

        assertEquals(4L, snapshot.samples());
        assertEquals(2L, snapshot.idlePumpSamples());
        assertEquals(2L, snapshot.tickBoundarySamples());
        assertEquals(2_500_000.0D, snapshot.averageDelayNanos());
        assertTrue(snapshot.p50DelayNanos() >= 2_000_000L);
        assertTrue(snapshot.p50DelayNanos() < 2_020_000L);
        assertTrue(snapshot.p95DelayNanos() >= 4_000_000L);
        assertTrue(snapshot.p95DelayNanos() < 4_040_000L);
        assertEquals(4_000_000L, snapshot.maximumDelayNanos());
        assertEquals(0.5D, snapshot.idlePumpRatio());
        assertEquals(0.5D, snapshot.tickBoundaryRatio());
    }

    @Test
    void resetStartsACompletelyNewComparisonWindow() {
        PacketQueueMetrics.recordDelay(10L, true);

        PacketQueueMetrics.reset();

        assertEquals(0L, PacketQueueMetrics.snapshot().samples());
    }
}
