package com.mojang.brigadier.tree;

public class LiteralCommandNode<S> extends CommandNode<S> {
    public String getLiteral() { return null; }
    @Override public com.mojang.brigadier.builder.LiteralArgumentBuilder<S> createBuilder() { return null; }
}
