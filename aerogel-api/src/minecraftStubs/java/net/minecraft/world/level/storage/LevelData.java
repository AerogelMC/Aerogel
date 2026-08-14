package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;

public interface LevelData {
    RespawnData getRespawnData();

    record RespawnData(BlockPos pos, float yaw, float pitch) {
    }
}
