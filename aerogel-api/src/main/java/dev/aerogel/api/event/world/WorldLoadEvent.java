package dev.aerogel.api.event.world;

public record WorldLoadEvent(Object levelHandle) implements WorldEvent {
}
