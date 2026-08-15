package dev.aerogel.api.mixin

/** Typed description of one local variable appended after the Mixin callback. */
public class CapturedLocal<L : Any> @PublishedApi internal constructor(
    @PublishedApi internal val type: Class<*>
)

/** Typed description of two consecutive local variables appended after the callback. */
public class CapturedLocals2<L1 : Any, L2 : Any> @PublishedApi internal constructor(
    @PublishedApi internal val firstType: Class<*>,
    @PublishedApi internal val secondType: Class<*>
)

public inline fun <reified L : Any> local(): CapturedLocal<L> =
    CapturedLocal(L::class.javaPrimitiveType ?: L::class.java)

public inline fun <reified L1 : Any, reified L2 : Any> locals(): CapturedLocals2<L1, L2> =
    CapturedLocals2(
        L1::class.javaPrimitiveType ?: L1::class.java,
        L2::class.javaPrimitiveType ?: L2::class.java
    )
