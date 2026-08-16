package dev.aerogel.loader.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdlePacketPumpTest {
    @AfterEach
    void restoreAerogelMode() {
        PacketQueueMetrics.setIdlePumpEnabled(true);
    }

    @Test
    void vanillaComparisonModeLeavesPacketsForTheTickBoundary() {
        Queue<Object> packets = new ArrayDeque<>(List.of("first"));
        Queue<Runnable> serverTasks = new ArrayDeque<>();
        IdlePacketPump pump = new IdlePacketPump(packets, ignored -> { });
        pump.configure(() -> true, serverTasks::add);
        PacketQueueMetrics.setIdlePumpEnabled(false);

        pump.request();

        assertEquals(0, serverTasks.size());
        assertEquals(List.of("first"), List.copyOf(packets));
    }

    @Test
    void doesNotCreateAnIdleTaskWhenNoPacketsAreQueued() {
        Queue<Object> packets = new ArrayDeque<>();
        Queue<Runnable> serverTasks = new ArrayDeque<>();
        IdlePacketPump pump = new IdlePacketPump(packets, ignored -> { });
        pump.configure(() -> true, serverTasks::add);

        pump.request();

        assertEquals(0, serverTasks.size());
    }

    @Test
    void doesNotScheduleOutsideTheVanillaIdleWindow() {
        Queue<Object> packets = new ArrayDeque<>(List.of("first"));
        Queue<Runnable> serverTasks = new ArrayDeque<>();
        AtomicBoolean idle = new AtomicBoolean();
        IdlePacketPump pump = new IdlePacketPump(packets, ignored -> { });
        pump.configure(idle::get, serverTasks::add);

        pump.request();

        assertEquals(0, serverTasks.size());
        assertEquals(List.of("first"), List.copyOf(packets));
    }

    @Test
    void preservesOrderAndLimitsEachRunToItsStartingSnapshot() {
        Queue<Object> packets = new ArrayDeque<>(List.of("first", "second"));
        Queue<Runnable> serverTasks = new ArrayDeque<>();
        List<Object> handled = new ArrayList<>();
        AtomicBoolean idle = new AtomicBoolean(true);
        IdlePacketPump pump = new IdlePacketPump(packets, packet -> {
            handled.add(packet);
            if (packet.equals("first")) packets.add("arrived-during-run");
        });
        pump.configure(idle::get, serverTasks::add);

        pump.request();
        serverTasks.remove().run();

        assertEquals(List.of("first", "second"), handled);
        assertEquals(List.of("arrived-during-run"), List.copyOf(packets));
        assertEquals(1, serverTasks.size());

        serverTasks.remove().run();
        assertEquals(List.of("first", "second", "arrived-during-run"), handled);
        assertEquals(0, packets.size());
    }

    @Test
    void stopsAsSoonAsTheServerLeavesItsIdleWindow() {
        Queue<Object> packets = new ArrayDeque<>(List.of("first", "second"));
        Queue<Runnable> serverTasks = new ArrayDeque<>();
        List<Object> handled = new ArrayList<>();
        AtomicBoolean idle = new AtomicBoolean(true);
        IdlePacketPump pump = new IdlePacketPump(packets, packet -> {
            handled.add(packet);
            idle.set(false);
        });
        pump.configure(idle::get, serverTasks::add);

        pump.request();
        serverTasks.remove().run();

        assertEquals(List.of("first"), handled);
        assertEquals(List.of("second"), List.copyOf(packets));
        assertEquals(0, serverTasks.size());
    }
}
