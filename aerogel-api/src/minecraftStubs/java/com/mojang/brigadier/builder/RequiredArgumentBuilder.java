package com.mojang.brigadier.builder;

import com.mojang.brigadier.suggestion.SuggestionProvider;

public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
    public SuggestionProvider<S> getSuggestionsProvider() { return null; }
    public RequiredArgumentBuilder<S, T> suggests(SuggestionProvider<S> provider) { return this; }
    @Override public com.mojang.brigadier.tree.ArgumentCommandNode<S, T> build() { return null; }
}
