package dev.aerogel.api.event.block;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Base type for events involving a player and a block in a server level. */
public abstract class PlayerBlockEvent implements AerogelEvent {
    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos position;
    private final BlockState state;

    protected PlayerBlockEvent(
        ServerPlayer player, ServerLevel level, BlockPos position, BlockState state
    ) {
        this.player = player;
        this.level = level;
        this.position = position;
        this.state = state;
    }

    public final ServerPlayer player() { return player; }
    public final ServerLevel level() { return level; }
    public final BlockPos position() { return position; }
    public final BlockState state() { return state; }
}
