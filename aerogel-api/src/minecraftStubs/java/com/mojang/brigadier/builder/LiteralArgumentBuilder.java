package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;

public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
    public String getLiteral() { return null; }
    public LiteralArgumentBuilder<S> then(LiteralArgumentBuilder<S> argument) { return this; }
    public LiteralArgumentBuilder<S> executes(Command<S> command) { return this; }
    public com.mojang.brigadier.tree.LiteralCommandNode<S> build() { return null; }
}
