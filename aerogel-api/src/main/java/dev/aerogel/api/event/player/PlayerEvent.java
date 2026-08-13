package dev.aerogel.api.event.player;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.server.level.ServerPlayer;

/** Base for events carrying the live vanilla ServerPlayer instance. */
public interface PlayerEvent extends AerogelEvent {
    ServerPlayer player();
}
