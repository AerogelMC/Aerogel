package net.minecraft.commands;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

public class CommandSourceStack {
    public void sendSuccess(Supplier<Component> message, boolean broadcastToAdmins) {
    }
    public void sendFailure(Component message) { }
    public ServerPlayer getPlayer() { return null; }
}
