package net.minecraft.server.players;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class PlayerList {
    public List<ServerPlayer> getPlayers() { return null; }
    public void broadcastSystemMessage(Component message, boolean overlay) {
    }

    public void broadcastChatMessage(
        PlayerChatMessage message, ServerPlayer player, ChatType.Bound chatType
    ) {
    }

    public void addWorldborderListener(ServerLevel level) {
    }
}
