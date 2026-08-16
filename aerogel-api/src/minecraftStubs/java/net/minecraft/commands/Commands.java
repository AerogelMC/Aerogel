package net.minecraft.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionProviderCheck;

public class Commands {
    public static final PermissionCheck LEVEL_ALL = null;
    public static final PermissionCheck LEVEL_GAMEMASTERS = null;
    public static final PermissionCheck LEVEL_OWNERS = null;
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String literal) { return null; }
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(
        String name, ArgumentType<T> type
    ) { return null; }
    public static PermissionProviderCheck<CommandSourceStack> hasPermission(PermissionCheck check) {
        return null;
    }
    public CommandDispatcher<CommandSourceStack> getDispatcher() { return null; }
    public void sendCommands(ServerPlayer player) { }
}
