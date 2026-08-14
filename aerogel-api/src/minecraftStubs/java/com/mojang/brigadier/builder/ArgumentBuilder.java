package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(CommandNode<S> child) { return null; }
    public T executes(Command<S> command) { return null; }
    public abstract CommandNode<S> build();
}
