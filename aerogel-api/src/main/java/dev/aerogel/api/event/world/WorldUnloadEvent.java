package dev.aerogel.api.event.world;

public record WorldUnloadEvent(Object levelHandle) implements WorldEvent {
}
