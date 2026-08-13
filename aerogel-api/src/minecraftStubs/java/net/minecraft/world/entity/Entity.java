package net.minecraft.world.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.function.Predicate;

public abstract class Entity {
    public Collection<Entity> nearbyEntities(double radius) { return null; }
    public Collection<Entity> nearbyEntities(double radius, Predicate<Entity> filter) { return null; }
    public boolean teleport(ServerLevel destination, double x, double y, double z) { return false; }
    public boolean teleport(ServerLevel destination, double x, double y, double z, float yaw, float pitch) {
        return false;
    }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public Level level() { return null; }
    public void discard() { }

    public enum RemovalReason {
    }
}
