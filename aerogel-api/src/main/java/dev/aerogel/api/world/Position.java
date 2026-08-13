package dev.aerogel.api.world;

public record Position(double x, double y, double z, float yaw, float pitch) {
    public Position(double x, double y, double z) { this(x, y, z, 0, 0); }
}
