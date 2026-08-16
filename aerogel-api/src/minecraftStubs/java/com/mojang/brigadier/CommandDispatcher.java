package com.mojang.brigadier;

import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;

public class CommandDispatcher<S> {
    public RootCommandNode<S> getRoot() { return null; }
    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> command) { return null; }
    public ParseResults<S> parse(String command, S source) { return null; }
    public CompletableFuture<Suggestions> getCompletionSuggestions(ParseResults<S> results, int cursor) {
        return null;
    }
}
