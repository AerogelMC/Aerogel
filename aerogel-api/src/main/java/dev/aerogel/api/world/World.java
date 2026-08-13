package dev.aerogel.api.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public interface World {
    String identifier();
    ServerLevel vanilla();
    long gameTime();
    long dayTime();
    World dayTime(long value);
    World weather(Weather value, int durationTicks);
    BlockState block(int x, int y, int z);
    boolean block(int x, int y, int z, BlockState vanillaBlockState, int flags);
    boolean spawn(Entity vanillaEntity);
    void teleport(ServerPlayer vanillaPlayer, Position position);
}
