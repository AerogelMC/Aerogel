package com.mojang.brigadier.tree;

public class RootCommandNode<S> extends CommandNode<S> {
    @Override public com.mojang.brigadier.builder.ArgumentBuilder<S, ?> createBuilder() { return null; }
}
