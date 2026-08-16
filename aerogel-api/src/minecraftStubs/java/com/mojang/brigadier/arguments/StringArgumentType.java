package com.mojang.brigadier.arguments;

import com.mojang.brigadier.context.CommandContext;

public final class StringArgumentType implements ArgumentType<String> {
    public static StringArgumentType word() { return null; }
    public static <S> String getString(CommandContext<S> context, String name) { return null; }
}
