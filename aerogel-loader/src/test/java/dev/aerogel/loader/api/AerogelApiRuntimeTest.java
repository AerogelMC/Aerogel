package dev.aerogel.loader.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class AerogelApiRuntimeTest {
    @Test
    void synchronousTasksFollowTicksAndCloseWithPluginScope() {
        AerogelApiRuntime runtime = new AerogelApiRuntime();
        PluginApiScope scope = runtime.openScope("test", Logger.getAnonymousLogger());
        FakeServer server = new FakeServer();
        AtomicInteger calls = new AtomicInteger();
        var once = scope.scheduler().later(2, calls::incrementAndGet);
        var repeating = scope.scheduler().repeat(1, 2, calls::incrementAndGet);

        runtime.attach(server);
        server.tick = 1;
        runtime.tick(server);
        assertEquals(1, calls.get());
        server.tick = 2;
        runtime.tick(server);
        assertEquals(2, calls.get());
        server.tick = 3;
        runtime.tick(server);
        assertEquals(3, calls.get());

        scope.close();
        assertFalse(once.active());
        assertFalse(repeating.active());
        server.tick = 5;
        runtime.tick(server);
        assertEquals(3, calls.get());
    }

    public static final class FakeServer {
        private long tick;
        public int getTickCount() { return (int) tick; }
    }
}
