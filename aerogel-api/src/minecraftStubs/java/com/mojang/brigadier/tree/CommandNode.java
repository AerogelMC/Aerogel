package com.mojang.brigadier.tree;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import java.util.Collection;

public abstract class CommandNode<S> {
    public String getName() { return null; }
    public Command<S> getCommand() { return null; }
    public Collection<CommandNode<S>> getChildren() { return null; }
    public void addChild(CommandNode<S> child) { }
    public abstract ArgumentBuilder<S, ?> createBuilder();
}
