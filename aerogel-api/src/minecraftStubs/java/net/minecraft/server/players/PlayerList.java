package net.minecraft.server.players;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

public class PlayerList {
    public void broadcastChatMessage(
        PlayerChatMessage message, ServerPlayer player, ChatType.Bound chatType
    ) {
    }
}
