package dev.aerogel.api.virtualentity;

import dev.aerogel.api.Registration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public interface VirtualEntity extends Registration {
    Entity entity();
    void show(ServerPlayer player);
    void hide(ServerPlayer player);
    boolean visibleTo(ServerPlayer player);
    Collection<ServerPlayer> viewers();
    /** Sends the entity's complete current vanilla pairing state again. */
    void synchronize();
}
