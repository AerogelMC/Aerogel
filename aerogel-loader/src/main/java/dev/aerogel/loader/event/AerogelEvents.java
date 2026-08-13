package dev.aerogel.loader.event;

import dev.aerogel.api.event.AerogelEvent;

import java.util.Objects;

public final class AerogelEvents {
    private static volatile EventRegistry registry;

    private AerogelEvents() {
    }

    public static void install(EventRegistry eventRegistry) {
        if (registry != null) {
            throw new IllegalStateException("Aerogel event registry is already installed");
        }
        registry = Objects.requireNonNull(eventRegistry, "eventRegistry");
    }

    public static <E extends AerogelEvent> E post(E event) {
        EventRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("Aerogel event registry is not installed");
        }
        return current.post(event);
    }
}
