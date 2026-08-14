package com.mojang.brigadier.suggestion;

import com.mojang.brigadier.context.CommandContext;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface SuggestionProvider<S> {
    CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) throws Exception;
}
