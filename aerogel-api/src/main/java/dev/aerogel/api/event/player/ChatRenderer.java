package dev.aerogel.api.event.player;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Renders signed chat through a network-compatible chat type template. */
@FunctionalInterface
public interface ChatRenderer {
    ChatRender render(ServerPlayer player, Component message);
}
