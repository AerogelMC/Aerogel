package dev.aerogel.api.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Persistent containers owned and automatically namespaced by the current plugin. */
public interface PersistentDataService {
    /** Data saved in the overworld's vanilla saved-data storage. */
    PersistentDataContainer server();
    /** Data saved with this player's vanilla entity data. */
    PersistentDataContainer player(net.minecraft.server.level.ServerPlayer player);
    /** Data saved with this entity's vanilla entity data. */
    PersistentDataContainer entity(Entity entity);
    /** Data saved with this block entity's own vanilla NBT payload. */
    PersistentDataContainer blockEntity(BlockEntity blockEntity);
    /** Data saved in this world's vanilla saved-data storage. */
    PersistentDataContainer world(ServerLevel level);
    /** Coordinate data saved in this world's vanilla saved-data storage. */
    PersistentDataContainer block(ServerLevel level, BlockPos position);
    /** Data is stored inside the stack's vanilla custom-data component and follows copies of that stack. */
    PersistentDataContainer item(ItemStack stack);
}
