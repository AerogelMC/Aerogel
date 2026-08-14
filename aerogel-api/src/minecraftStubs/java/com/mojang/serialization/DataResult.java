package com.mojang.serialization;

import java.util.function.Function;

public interface DataResult<R> {
    <E extends Throwable> R getOrThrow(Function<String, E> exceptionFactory) throws E;
}
