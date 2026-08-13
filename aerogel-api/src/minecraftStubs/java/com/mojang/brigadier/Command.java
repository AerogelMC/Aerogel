package com.mojang.brigadier;

import com.mojang.brigadier.context.CommandContext;

@FunctionalInterface
public interface Command<S> {
    int run(CommandContext<S> context) throws Exception;
}
