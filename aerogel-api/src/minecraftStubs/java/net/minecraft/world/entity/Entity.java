package net.minecraft.world.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.Set;

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
    public float getYRot() { return 0; }
    public float getXRot() { return 0; }
    public int getId() { return 0; }
    public UUID getUUID() { return null; }
    public Vec3 position() { return null; }
    public HitResult pick(double distance, float partialTick, boolean includeFluids) { return null; }
    public Level level() { return null; }
    public boolean teleportTo(ServerLevel destination, double x, double y, double z,
                              Set<Relative> relatives, float yaw, float pitch, boolean resetCamera) {
        return false;
    }
    public Entity teleport(TeleportTransition transition) { return null; }
    public boolean startRiding(Entity vehicle, boolean force, boolean teleport) { return false; }
    public Entity getVehicle() { return null; }
    public void igniteForTicks(int ticks) { }
    public int getAirSupply() { return 0; }
    public void setAirSupply(int airSupply) { }
    public int getTicksFrozen() { return 0; }
    public void setTicksFrozen(int ticks) { }
    public Pose getPose() { return null; }
    public void setPose(Pose pose) { }
    public Component getCustomName() { return null; }
    public void setCustomName(Component name) { }
    public boolean isInvisible() { return false; }
    public void setInvisible(boolean invisible) { }
    public boolean isNoGravity() { return false; }
    public void setNoGravity(boolean noGravity) { }
    public boolean isSilent() { return false; }
    public void setSilent(boolean silent) { }
    public boolean isShiftKeyDown() { return false; }
    public void setShiftKeyDown(boolean shiftKeyDown) { }
    public boolean isSwimming() { return false; }
    public void setSwimming(boolean swimming) { }
    public ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack) { return null; }
    public void discard() { }

    public enum RemovalReason {
    }
}
