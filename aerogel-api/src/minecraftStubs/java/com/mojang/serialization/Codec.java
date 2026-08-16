package com.mojang.serialization;

import java.util.List;
import java.util.function.Function;

public interface Codec<A> {
    <T> DataResult<T> encodeStart(DynamicOps<T> operations, A input);

    <T> DataResult<A> parse(DynamicOps<T> operations, T input);

    Codec<List<A>> listOf();

    default <S> Codec<S> xmap(
        Function<? super A, ? extends S> to,
        Function<? super S, ? extends A> from
    ) { return null; }
}
