package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import java.util.function.Predicate;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(CommandNode<S> child) { return null; }
    public T then(ArgumentBuilder<S, ?> child) { return null; }
    public T executes(Command<S> command) { return null; }
    public T requires(Predicate<S> requirement) { return null; }
    public abstract CommandNode<S> build();
}
