package dev.aerogel.loader.api;

import dev.aerogel.api.persistence.PersistentDataContainer;
import dev.aerogel.api.persistence.PersistentDataService;
import dev.aerogel.loader.internal.PersistentDataHolderBridge;
import dev.aerogel.loader.internal.PersistentDataViews;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;

/** Plugin-scoped facade over the same native views exposed directly on vanilla objects. */
final class DirectPersistentDataService implements PersistentDataService {
    private final PluginApiScope scope;

    DirectPersistentDataService(PluginApiScope scope) {
        this.scope = scope;
    }

    void serverReady() { }

    @Override public PersistentDataContainer server() {
        return PersistentDataViews.server(scope.vanilla()).namespace(scope.pluginId());
    }

    @Override public PersistentDataContainer player(ServerPlayer player) {
        return entity(player);
    }

    @Override public PersistentDataContainer entity(Entity entity) {
        return holder(Objects.requireNonNull(entity, "entity"), "Entity");
    }

    @Override public PersistentDataContainer blockEntity(BlockEntity blockEntity) {
        return holder(Objects.requireNonNull(blockEntity, "blockEntity"), "Block entity");
    }

    @Override public PersistentDataContainer world(ServerLevel level) {
        return PersistentDataViews.world(Objects.requireNonNull(level, "level"))
            .namespace(scope.pluginId());
    }

    @Override public PersistentDataContainer block(ServerLevel level, BlockPos position) {
        return PersistentDataViews.block(Objects.requireNonNull(level, "level"),
            Objects.requireNonNull(position, "position")).namespace(scope.pluginId());
    }

    @Override public PersistentDataContainer item(ItemStack stack) {
        return PersistentDataViews.item(Objects.requireNonNull(stack, "stack"))
            .namespace(scope.pluginId());
    }

    private PersistentDataContainer holder(Object value, String kind) {
        if (!(value instanceof PersistentDataHolderBridge bridge)) {
            throw new IllegalStateException(kind + " persistent-data bridge is unavailable");
        }
        return PersistentDataViews.holder(bridge).namespace(scope.pluginId());
    }
}
