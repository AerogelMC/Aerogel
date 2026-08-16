package com.mojang.brigadier.tree;

public class ArgumentCommandNode<S, T> extends CommandNode<S> {
    @Override public com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T> createBuilder() { return null; }
}
