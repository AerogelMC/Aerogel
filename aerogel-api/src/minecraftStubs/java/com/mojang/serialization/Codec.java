package com.mojang.serialization;

import java.util.List;

public interface Codec<A> {
    <T> DataResult<T> encodeStart(DynamicOps<T> operations, A input);

    <T> DataResult<A> parse(DynamicOps<T> operations, T input);

    Codec<List<A>> listOf();
}
