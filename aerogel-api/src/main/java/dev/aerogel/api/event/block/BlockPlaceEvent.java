package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class BlockPlaceEvent implements CancellableEvent {
    private final BlockItem blockItem;
    private final BlockPlaceContext context;
    private boolean cancelled;

    public BlockPlaceEvent(BlockItem blockItem, BlockPlaceContext context) {
        this.blockItem = blockItem;
        this.context = context;
    }

    public BlockItem blockItem() { return blockItem; }
    public BlockPlaceContext context() { return context; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
