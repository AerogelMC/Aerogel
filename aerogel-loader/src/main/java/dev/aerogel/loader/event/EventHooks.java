package dev.aerogel.loader.event;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small, allocation-free entry points used by the core mixins.
 *
 * <p>This class deliberately contains no reflective vanilla dispatch. Calls into
 * Minecraft are compiled against the mapped server types so descriptor drift is
 * caught while building Aerogel.</p>
 */
public final class EventHooks {
    private EventHooks() {
    }

    public static void post(AerogelEvent event) {
        AerogelEvents.post(event);
    }

    public static boolean hasListeners(Class<? extends AerogelEvent> eventType) {
        return AerogelEvents.hasListeners(eventType);
    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object value) {
        return (T) value;
    }

    public static void resyncBlock(ServerPlayer player, Level level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        player.sendPacket(new ClientboundBlockUpdatePacket(position, state));

        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity != null) {
            Packet<?> update = blockEntity.getUpdatePacket();
            if (update != null) {
                player.sendPacket(update);
            }
        }

        level.destroyBlockProgress(player.getId(), position, -1);
    }
}
