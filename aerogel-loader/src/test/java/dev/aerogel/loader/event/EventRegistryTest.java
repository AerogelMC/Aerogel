package dev.aerogel.loader.event;

import dev.aerogel.api.event.AerogelEvent;
import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.EventPriority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRegistryTest {
    @Test
    void ordersLambdaListenersAndClosesTheEntirePluginScope() {
        EventRegistry registry = new EventRegistry();
        EventRegistry.OwnedEventBus events = registry.owner("test", Logger.getLogger("test"));
        List<String> calls = new ArrayList<>();
        events.listen(TestEvent.class, EventPriority.LATE, event -> calls.add("late"));
        events.listen(TestEvent.class, EventPriority.EARLY, event -> calls.add("early"));
        events.listen(TestEvent.class, event -> calls.add("normal"));

        registry.post(new TestEvent());
        assertEquals(List.of("early", "normal", "late"), calls);

        events.close();
        registry.post(new TestEvent());
        assertEquals(List.of("early", "normal", "late"), calls);
    }

    @Test
    void dispatchesConcreteEventsToBaseEventListeners() {
        EventRegistry registry = new EventRegistry();
        EventRegistry.OwnedEventBus events = registry.owner("test", Logger.getLogger("test"));
        List<AerogelEvent> received = new ArrayList<>();
        events.listen(AerogelEvent.class, received::add);

        TestEvent event = registry.post(new TestEvent());

        assertEquals(List.of(event), received);
    }

    @Test
    void logsAListenerFailureAndContinuesDispatching() {
        EventRegistry registry = new EventRegistry();
        EventRegistry.OwnedEventBus events = registry.owner("test", Logger.getLogger("test"));
        List<String> calls = new ArrayList<>();
        events.listen(TestEvent.class, event -> { throw new IllegalStateException("expected"); });
        events.listen(TestEvent.class, event -> calls.add("continued"));

        registry.post(new TestEvent());
        registry.post(new TestEvent());

        assertEquals(List.of("continued", "continued"), calls);
    }

    @Test
    void skipsCancelledEventsUnlessRequestedAndProtectsMonitorState() {
        EventRegistry registry = new EventRegistry();
        EventRegistry.OwnedEventBus events = registry.owner("test", Logger.getLogger("test"));
        List<String> calls = new ArrayList<>();
        events.listen(CancelEvent.class, EventPriority.EARLY, event -> event.cancel());
        events.listen(CancelEvent.class, event -> calls.add("normal"));
        events.listen(CancelEvent.class, EventPriority.NORMAL, true, event -> calls.add("cancelled"));
        events.listen(CancelEvent.class, EventPriority.MONITOR, true, event -> event.setCancelled(false));

        CancelEvent event = registry.post(new CancelEvent());

        assertEquals(List.of("cancelled"), calls);
        assertTrue(event.isCancelled());
    }

    private static final class TestEvent implements AerogelEvent {
    }

    private static final class CancelEvent implements CancellableEvent {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
