package dev.aerogel.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;

public interface CommandService {
    CommandRegistration register(LiteralArgumentBuilder<CommandSourceStack> brigadierRoot);

    CommandRegistration register(LiteralCommandNode<CommandSourceStack> brigadierRoot);
}
