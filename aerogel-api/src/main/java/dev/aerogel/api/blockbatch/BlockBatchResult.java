package dev.aerogel.api.blockbatch;

public record BlockBatchResult(int requested, int changed, int chunksSynchronized) { }
