package net.minecraft.world.level.gameevent;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface PositionSource {
    Optional<Vec3> getPosition(Level level);
}
