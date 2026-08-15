package dev.aerogel.api.mixin

import kotlin.jvm.JvmName
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction0
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction4
import kotlin.reflect.KFunction5
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import org.spongepowered.asm.mixin.injection.invoke.arg.Args

@AerogelMixinDsl
public class MixinScope<T : Any> @PublishedApi internal constructor(
    private val identity: String = "anonymous"
) {
    @PublishedApi internal val operations: MutableList<MixinOperationDefinition> = mutableListOf()
    @PublishedApi internal val members: MutableList<MixinMemberDefinition> = mutableListOf()
    @PublishedApi internal var activeGroup: InjectorGroup? = null

    public fun group(
        name: String, min: Int = -1, max: Int = -1,
        block: MixinScope<T>.() -> Unit
    ) {
        val previous = activeGroup
        activeGroup = InjectorGroup(name, min, max)
        try {
            block()
        } finally {
            activeGroup = previous
        }
    }

    @JvmName("readAccessor")
    public fun <V> accessor(field: KProperty1<T, V>, remap: Boolean = true): FieldAccessor<T, V> {
        val getter = bridgeName("get", members.size)
        members += AccessorDefinition(field, getter, null, remap)
        return FieldAccessor(getter)
    }

    @JvmName("mutableAccessor")
    public fun <V> accessor(
        field: KMutableProperty1<T, V>, remap: Boolean = true
    ): MutableFieldAccessor<T, V> {
        val getter = bridgeName("get", members.size)
        val setter = bridgeName("set", members.size)
        members += AccessorDefinition(field, getter, setter, remap)
        return MutableFieldAccessor(getter, setter)
    }

    @JvmName("readShadow")
    public fun <V> shadow(
        field: KProperty1<T, V>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): FieldAccessor<T, V> {
        val getter = bridgeName("shadowGet", members.size)
        members += ShadowFieldDefinition(field, getter, null, aliases, remap, false)
        return FieldAccessor(getter)
    }

    @JvmName("mutableShadow")
    public fun <V> shadow(
        field: KMutableProperty1<T, V>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): MutableFieldAccessor<T, V> {
        val getter = bridgeName("shadowGet", members.size)
        val setter = bridgeName("shadowSet", members.size)
        members += ShadowFieldDefinition(field, getter, setter, aliases, remap, false)
        return MutableFieldAccessor(getter, setter)
    }

    public fun <V> mutableFinalShadow(
        field: KProperty1<T, V>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): MutableFieldAccessor<T, V> {
        val getter = bridgeName("shadowGet", members.size)
        val setter = bridgeName("shadowSet", members.size)
        members += ShadowFieldDefinition(field, getter, setter, aliases, remap, true)
        return MutableFieldAccessor(getter, setter)
    }

    @JvmName("invoker0")
    public fun <R> invoker(method: KFunction1<T, R>, remap: Boolean = true): Invoker0<T, R> {
        val name = bridgeName("invoke", members.size)
        members += InvokerDefinition(method, name, remap)
        return Invoker0(name)
    }

    @JvmName("invoker1")
    public fun <A, R> invoker(method: KFunction2<T, A, R>, remap: Boolean = true): Invoker1<T, A, R> {
        val name = bridgeName("invoke", members.size)
        members += InvokerDefinition(method, name, remap)
        return Invoker1(name)
    }

    @JvmName("invoker2")
    public fun <A, B, R> invoker(method: KFunction3<T, A, B, R>, remap: Boolean = true): Invoker2<T, A, B, R> {
        val name = bridgeName("invoke", members.size)
        members += InvokerDefinition(method, name, remap)
        return Invoker2(name)
    }

    @JvmName("invoker3")
    public fun <A, B, C, R> invoker(
        method: KFunction4<T, A, B, C, R>, remap: Boolean = true
    ): Invoker3<T, A, B, C, R> {
        val name = bridgeName("invoke", members.size)
        members += InvokerDefinition(method, name, remap)
        return Invoker3(name)
    }

    @JvmName("shadowMethod0")
    public fun <R> shadow(
        method: KFunction1<T, R>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): Invoker0<T, R> {
        val name = bridgeName("shadowCall", members.size)
        members += ShadowMethodDefinition(method, name, aliases, remap)
        return Invoker0(name)
    }

    @JvmName("shadowMethod1")
    public fun <A, R> shadow(
        method: KFunction2<T, A, R>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): Invoker1<T, A, R> {
        val name = bridgeName("shadowCall", members.size)
        members += ShadowMethodDefinition(method, name, aliases, remap)
        return Invoker1(name)
    }

    @JvmName("shadowMethod2")
    public fun <A, B, R> shadow(
        method: KFunction3<T, A, B, R>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): Invoker2<T, A, B, R> {
        val name = bridgeName("shadowCall", members.size)
        members += ShadowMethodDefinition(method, name, aliases, remap)
        return Invoker2(name)
    }

    @JvmName("shadowMethod3")
    public fun <A, B, C, R> shadow(
        method: KFunction4<T, A, B, C, R>, aliases: List<String> = emptyList(), remap: Boolean = true
    ): Invoker3<T, A, B, C, R> {
        val name = bridgeName("shadowCall", members.size)
        members += ShadowMethodDefinition(method, name, aliases, remap)
        return Invoker3(name)
    }

    public inline fun <reified V : Any> uniqueField(
        silent: Boolean = false
    ): MutableFieldAccessor<T, V> = uniqueField(
        V::class.javaPrimitiveType ?: V::class.java,
        silent
    )

    public fun <V : Any> uniqueField(
        type: Class<V>, silent: Boolean = false
    ): MutableFieldAccessor<T, V> {
        val index = members.size
        val fieldName = bridgeName("field", index)
        val getter = bridgeName("get", index)
        val setter = bridgeName("set", index)
        members += UniqueFieldDefinition(
            fieldName,
            type,
            getter,
            setter,
            silent
        )
        return MutableFieldAccessor(getter, setter)
    }

    @JvmName("injectVoid0")
    public fun inject(
        method: KFunction1<T, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectReturnable0")
    public fun <R> inject(
        method: KFunction1<T, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectVoid1")
    public fun <A> inject(
        method: KFunction2<T, A, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectReturnable1")
    public fun <A, R> inject(
        method: KFunction2<T, A, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectVoid2")
    public fun <A, B> inject(
        method: KFunction3<T, A, B, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, B, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectReturnable2")
    public fun <A, B, R> inject(
        method: KFunction3<T, A, B, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, B, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectVoid3")
    public fun <A, B, C> inject(
        method: KFunction4<T, A, B, C, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, B, C, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectReturnable3")
    public fun <A, B, C, R> inject(
        method: KFunction4<T, A, B, C, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, B, C, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectVoid4")
    public fun <A, B, C, D> inject(
        method: KFunction5<T, A, B, C, D, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, B, C, D, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectReturnable4")
    public fun <A, B, C, D, R> inject(
        method: KFunction5<T, A, B, C, D, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), locals: LocalCapture = LocalCapture.NONE,
        handler: T.(A, B, C, D, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, false, false, handler)

    @JvmName("injectLocalsVoid0L1")
    public fun <L : Any> injectLocals(
        method: KFunction1<T, Unit>, at: AtSelection, capture: CapturedLocal<L>,
        cancellable: Boolean = false,
        locals: LocalCapture = LocalCapture.CAPTURE_FAILHARD,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(),
        handler: T.(CallbackInfo, L) -> Unit
    ): Unit = addInjectLocals(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, listOf(capture.type), handler)

    @JvmName("injectLocalsReturnable0L1")
    public fun <R, L : Any> injectLocals(
        method: KFunction1<T, R>, at: AtSelection, capture: CapturedLocal<L>,
        cancellable: Boolean = false,
        locals: LocalCapture = LocalCapture.CAPTURE_FAILHARD,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(),
        handler: T.(CallbackInfoReturnable<R>, L) -> Unit
    ): Unit = addInjectLocals(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, listOf(capture.type), handler)

    @JvmName("injectLocalsVoid0L2")
    public fun <L1 : Any, L2 : Any> injectLocals(
        method: KFunction1<T, Unit>, at: AtSelection, capture: CapturedLocals2<L1, L2>,
        cancellable: Boolean = false,
        locals: LocalCapture = LocalCapture.CAPTURE_FAILHARD,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(),
        handler: T.(CallbackInfo, L1, L2) -> Unit
    ): Unit = addInjectLocals(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, listOf(capture.firstType, capture.secondType), handler)

    @JvmName("injectLocalsReturnable0L2")
    public fun <R, L1 : Any, L2 : Any> injectLocals(
        method: KFunction1<T, R>, at: AtSelection, capture: CapturedLocals2<L1, L2>,
        cancellable: Boolean = false,
        locals: LocalCapture = LocalCapture.CAPTURE_FAILHARD,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(),
        handler: T.(CallbackInfoReturnable<R>, L1, L2) -> Unit
    ): Unit = addInjectLocals(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, listOf(capture.firstType, capture.secondType), handler)

    @JvmName("injectLocalsVoid1L1")
    public fun <A, L : Any> injectLocals(
        method: KFunction2<T, A, Unit>, at: AtSelection, capture: CapturedLocal<L>,
        cancellable: Boolean = false,
        locals: LocalCapture = LocalCapture.CAPTURE_FAILHARD,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(),
        handler: T.(A, CallbackInfo, L) -> Unit
    ): Unit = addInjectLocals(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, listOf(capture.type), handler)

    @JvmName("injectLocalsReturnable1L1")
    public fun <A, R, L : Any> injectLocals(
        method: KFunction2<T, A, R>, at: AtSelection, capture: CapturedLocal<L>,
        cancellable: Boolean = false,
        locals: LocalCapture = LocalCapture.CAPTURE_FAILHARD,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(),
        handler: T.(A, CallbackInfoReturnable<R>, L) -> Unit
    ): Unit = addInjectLocals(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, locals, listOf(capture.type), handler)

    public fun classInitializer(
        at: AtSelection, cancellable: Boolean = false, require: Int = 1,
        expect: Int = 1, allow: Int = -1, order: Int = 1000, remap: Boolean = true,
        constraints: String = "", id: String = "", slices: List<MixinSlice> = emptyList(),
        handler: (CallbackInfo) -> Unit
    ) {
        addInject(::aerogelClassInitializerMarker, at, cancellable, require, expect, allow,
            order, remap, constraints, id, slices, LocalCapture.NONE, true, true, handler)
    }

    @JvmName("injectConstructor0")
    public fun injectConstructor(
        constructor: KFunction0<T>, at: AtSelection = At.CTOR_HEAD,
        cancellable: Boolean = false, require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 1000, remap: Boolean = true,
        constraints: String = "", id: String = "", slices: List<MixinSlice> = emptyList(),
        handler: T.(CallbackInfo) -> Unit
    ): Unit = addInject(constructor, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, false, handler)

    @JvmName("injectConstructor1")
    public fun <A> injectConstructor(
        constructor: KFunction1<A, T>, at: AtSelection = At.CTOR_HEAD,
        cancellable: Boolean = false, require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 1000, remap: Boolean = true,
        constraints: String = "", id: String = "", slices: List<MixinSlice> = emptyList(),
        handler: T.(A, CallbackInfo) -> Unit
    ): Unit = addInject(constructor, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, false, handler)

    @JvmName("injectConstructor2")
    public fun <A, B> injectConstructor(
        constructor: KFunction2<A, B, T>, at: AtSelection = At.CTOR_HEAD,
        cancellable: Boolean = false, require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 1000, remap: Boolean = true,
        constraints: String = "", id: String = "", slices: List<MixinSlice> = emptyList(),
        handler: T.(A, B, CallbackInfo) -> Unit
    ): Unit = addInject(constructor, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, false, handler)

    @JvmName("injectConstructor3")
    public fun <A, B, C> injectConstructor(
        constructor: KFunction3<A, B, C, T>, at: AtSelection = At.CTOR_HEAD,
        cancellable: Boolean = false, require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 1000, remap: Boolean = true,
        constraints: String = "", id: String = "", slices: List<MixinSlice> = emptyList(),
        handler: T.(A, B, C, CallbackInfo) -> Unit
    ): Unit = addInject(constructor, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, false, handler)

    @JvmName("injectStaticVoid0")
    public fun injectStatic(
        method: KFunction0<Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticReturnable0")
    public fun <R> injectStatic(
        method: KFunction0<R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticVoid1")
    public fun <A> injectStatic(
        method: KFunction1<A, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticReturnable1")
    public fun <A, R> injectStatic(
        method: KFunction1<A, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticVoid2")
    public fun <A, B> injectStatic(
        method: KFunction2<A, B, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, B, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticReturnable2")
    public fun <A, B, R> injectStatic(
        method: KFunction2<A, B, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, B, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticVoid3")
    public fun <A, B, C> injectStatic(
        method: KFunction3<A, B, C, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, B, C, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticReturnable3")
    public fun <A, B, C, R> injectStatic(
        method: KFunction3<A, B, C, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, B, C, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticVoid4")
    public fun <A, B, C, D> injectStatic(
        method: KFunction4<A, B, C, D, Unit>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, B, C, D, CallbackInfo) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    @JvmName("injectStaticReturnable4")
    public fun <A, B, C, D, R> injectStatic(
        method: KFunction4<A, B, C, D, R>, at: AtSelection, cancellable: Boolean = false,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", id: String = "",
        slices: List<MixinSlice> = emptyList(), handler: (A, B, C, D, CallbackInfoReturnable<R>) -> Unit
    ): Unit = addInject(method, at, cancellable, require, expect, allow, order, remap,
        constraints, id, slices, LocalCapture.NONE, false, true, handler)

    public inline fun <reified V : Any> modifyArg(
        method: KFunction<*>, at: At, index: Int = -1, slice: MixinSlice? = null,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "",
        noinline handler: T.(V) -> V
    ) {
        operations += ModifyArgDefinition(method, at, slice, index,
            V::class.javaPrimitiveType ?: V::class.java,
            false,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyArgStatic(
        method: KFunction<*>, at: At, index: Int = -1, slice: MixinSlice? = null,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", noinline handler: (V) -> V
    ) {
        operations += ModifyArgDefinition(method, at, slice, index,
            V::class.javaPrimitiveType ?: V::class.java, true,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public fun modifyArgs(
        method: KFunction<*>, at: At, slice: MixinSlice? = null,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "",
        handler: T.(Args) -> Unit
    ) {
        operations += ModifyArgsDefinition(method, at, slice,
            false,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public fun modifyArgsStatic(
        method: KFunction<*>, at: At, slice: MixinSlice? = null,
        require: Int = 1, expect: Int = 1, allow: Int = -1, order: Int = 1000,
        remap: Boolean = true, constraints: String = "", handler: (Args) -> Unit
    ) {
        operations += ModifyArgsDefinition(method, at, slice, true,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyVariable(
        method: KFunction<*>, at: At, ordinal: Int = -1, index: Int = -1,
        names: List<String> = emptyList(), argsOnly: Boolean = false, print: Boolean = false,
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 1000, remap: Boolean = true, constraints: String = "",
        noinline handler: T.(V) -> V
    ) {
        operations += ModifyVariableDefinition(method, at, slice,
            V::class.javaPrimitiveType ?: V::class.java, print,
            ordinal, index, names, argsOnly, false,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyVariableStatic(
        method: KFunction<*>, at: At, ordinal: Int = -1, index: Int = -1,
        names: List<String> = emptyList(), argsOnly: Boolean = false, print: Boolean = false,
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 1000, remap: Boolean = true, constraints: String = "",
        noinline handler: (V) -> V
    ) {
        operations += ModifyVariableDefinition(method, at, slice,
            V::class.javaPrimitiveType ?: V::class.java, print,
            ordinal, index, names, argsOnly, true,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyConstant(
        method: KFunction<*>, constant: ConstantSelector = ConstantSelector.any(),
        slices: List<MixinSlice> = emptyList(), require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 10000, remap: Boolean = true,
        constraints: String = "", noinline handler: T.(V) -> V
    ) {
        operations += ModifyConstantDefinition(method, listOf(constant), slices,
            V::class.javaPrimitiveType ?: V::class.java,
            false,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyConstantStatic(
        method: KFunction<*>, constant: ConstantSelector = ConstantSelector.any(),
        slices: List<MixinSlice> = emptyList(), require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 10000, remap: Boolean = true,
        constraints: String = "", noinline handler: (V) -> V
    ) {
        operations += ModifyConstantDefinition(method, listOf(constant), slices,
            V::class.javaPrimitiveType ?: V::class.java, true,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyConstantsStatic(
        method: KFunction<*>, constants: List<ConstantSelector>,
        slices: List<MixinSlice> = emptyList(), require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 10000, remap: Boolean = true,
        constraints: String = "", noinline handler: (V) -> V
    ) {
        require(constants.isNotEmpty()) { "At least one constant selector is required" }
        operations += ModifyConstantDefinition(method, constants, slices,
            V::class.javaPrimitiveType ?: V::class.java, true,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    public inline fun <reified V : Any> modifyConstant(
        method: KFunction<*>, constants: List<ConstantSelector>,
        slices: List<MixinSlice> = emptyList(), require: Int = 1, expect: Int = 1,
        allow: Int = -1, order: Int = 10000, remap: Boolean = true,
        constraints: String = "", noinline handler: T.(V) -> V
    ) {
        require(constants.isNotEmpty()) { "At least one constant selector is required" }
        operations += ModifyConstantDefinition(method, constants, slices,
            V::class.javaPrimitiveType ?: V::class.java,
            false,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    @JvmName("redirect0")
    public fun <A, R> redirect(
        method: KFunction<*>, target: KFunction1<A, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: T.(A) -> R
    ): Unit = addRedirect(method, target, at, slice, require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirect1")
    public fun <A, B, R> redirect(
        method: KFunction<*>, target: KFunction2<A, B, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: T.(A, B) -> R
    ): Unit = addRedirect(method, target, at, slice, require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirect2")
    public fun <A, B, C, R> redirect(
        method: KFunction<*>, target: KFunction3<A, B, C, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: T.(A, B, C) -> R
    ): Unit = addRedirect(method, target, at, slice, require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirect3")
    public fun <A, B, C, D, R> redirect(
        method: KFunction<*>, target: KFunction4<A, B, C, D, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: T.(A, B, C, D) -> R
    ): Unit = addRedirect(method, target, at, slice, require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirectStatic0")
    public fun <R> redirectStatic(
        method: KFunction<*>, target: KFunction0<R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: () -> R
    ): Unit = addRedirect(method, MethodTarget(target), RedirectFieldAccess.NONE, at, slice, true,
        require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirectStatic1")
    public fun <A, R> redirectStatic(
        method: KFunction<*>, target: KFunction1<A, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: (A) -> R
    ): Unit = addRedirect(method, MethodTarget(target), RedirectFieldAccess.NONE, at, slice, true,
        require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirectStatic2")
    public fun <A, B, R> redirectStatic(
        method: KFunction<*>, target: KFunction2<A, B, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: (A, B) -> R
    ): Unit = addRedirect(method, MethodTarget(target), RedirectFieldAccess.NONE, at, slice, true,
        require, expect, allow, order, remap, constraints, handler)

    @JvmName("redirectStatic3")
    public fun <A, B, C, R> redirectStatic(
        method: KFunction<*>, target: KFunction3<A, B, C, R>, at: At = At.invoke(target),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: (A, B, C) -> R
    ): Unit = addRedirect(method, MethodTarget(target), RedirectFieldAccess.NONE, at, slice, true,
        require, expect, allow, order, remap, constraints, handler)

    public fun <O, V> redirectFieldGet(
        method: KFunction<*>, field: KProperty1<O, V>, at: At = At.field(field),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: T.(O) -> V
    ) {
        addRedirect(method, FieldTarget(field), RedirectFieldAccess.GET, at, slice, false,
            require, expect, allow, order, remap, constraints, handler)
    }

    public fun <O, V> redirectFieldSet(
        method: KFunction<*>, field: KMutableProperty1<O, V>, at: At = At.field(field),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: T.(O, V) -> Unit
    ) {
        addRedirect(method, FieldTarget(field), RedirectFieldAccess.SET, at, slice, false,
            require, expect, allow, order, remap, constraints, handler)
    }

    public fun <O, V> redirectFieldGetStatic(
        method: KFunction<*>, field: KProperty1<O, V>, at: At = At.field(field),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: (O) -> V
    ) {
        addRedirect(method, FieldTarget(field), RedirectFieldAccess.GET, at, slice, true,
            require, expect, allow, order, remap, constraints, handler)
    }

    public fun <O, V> redirectFieldSetStatic(
        method: KFunction<*>, field: KMutableProperty1<O, V>, at: At = At.field(field),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: (O, V) -> Unit
    ) {
        addRedirect(method, FieldTarget(field), RedirectFieldAccess.SET, at, slice, true,
            require, expect, allow, order, remap, constraints, handler)
    }

    public fun <V> redirectStaticFieldGet(
        method: KFunction<*>, field: KProperty0<V>, at: At = At.field(field),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: () -> V
    ) {
        addRedirect(method, FieldTarget(field), RedirectFieldAccess.GET, at, slice, true,
            require, expect, allow, order, remap, constraints, handler)
    }

    public fun <V> redirectStaticFieldSet(
        method: KFunction<*>, field: KMutableProperty0<V>, at: At = At.field(field),
        slice: MixinSlice? = null, require: Int = 1, expect: Int = 1, allow: Int = -1,
        order: Int = 10000, remap: Boolean = true, constraints: String = "",
        handler: (V) -> Unit
    ) {
        addRedirect(method, FieldTarget(field), RedirectFieldAccess.SET, at, slice, true,
            require, expect, allow, order, remap, constraints, handler)
    }

    @JvmName("overwrite0")
    public fun <R> overwrite(method: KFunction1<T, R>, handler: T.() -> R): Unit =
        addOverwrite(method, false, handler)

    @JvmName("overwrite1")
    public fun <A, R> overwrite(method: KFunction2<T, A, R>, handler: T.(A) -> R): Unit =
        addOverwrite(method, false, handler)

    @JvmName("overwrite2")
    public fun <A, B, R> overwrite(method: KFunction3<T, A, B, R>, handler: T.(A, B) -> R): Unit =
        addOverwrite(method, false, handler)

    @JvmName("overwrite3")
    public fun <A, B, C, R> overwrite(method: KFunction4<T, A, B, C, R>, handler: T.(A, B, C) -> R): Unit =
        addOverwrite(method, false, handler)

    @JvmName("overwriteStatic0")
    public fun <R> overwriteStatic(method: KFunction0<R>, handler: () -> R): Unit =
        addOverwrite(method, true, handler)

    @JvmName("overwriteStatic1")
    public fun <A, R> overwriteStatic(method: KFunction1<A, R>, handler: (A) -> R): Unit =
        addOverwrite(method, true, handler)

    @JvmName("overwriteStatic2")
    public fun <A, B, R> overwriteStatic(method: KFunction2<A, B, R>, handler: (A, B) -> R): Unit =
        addOverwrite(method, true, handler)

    @JvmName("overwriteStatic3")
    public fun <A, B, C, R> overwriteStatic(
        method: KFunction3<A, B, C, R>, handler: (A, B, C) -> R
    ): Unit = addOverwrite(method, true, handler)

    @PublishedApi
    internal fun build(target: Class<T>, priority: Int): MixinDefinition<T> =
        MixinDefinition(target, priority, operations, members)

    private fun bridgeName(kind: String, index: Int): String =
        "aerogel\$bridge\$${bridgePrefix(identity)}\$$kind\$$index"

    private fun addInject(
        method: KFunction<*>, at: AtSelection, cancellable: Boolean, require: Int,
        expect: Int, allow: Int, order: Int, remap: Boolean, constraints: String,
        id: String, slices: List<MixinSlice>, locals: LocalCapture,
        classInitializer: Boolean, staticHandler: Boolean, handler: Any
    ) {
        operations += InjectionDefinition(method, at.points, slices, id, cancellable, locals, emptyList(),
            classInitializer, staticHandler,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    @PublishedApi
    internal fun addInjectLocals(
        method: KFunction<*>, at: AtSelection, cancellable: Boolean, require: Int,
        expect: Int, allow: Int, order: Int, remap: Boolean, constraints: String,
        id: String, slices: List<MixinSlice>, locals: LocalCapture,
        localTypes: List<Class<*>>, handler: Any
    ) {
        require(locals != LocalCapture.NONE && locals != LocalCapture.PRINT) {
            "A local-capturing handler requires a CAPTURE_* LocalCapture mode"
        }
        operations += InjectionDefinition(method, at.points, slices, id, cancellable, locals,
            localTypes, false, false,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    private fun addRedirect(
        method: KFunction<*>, target: KFunction<*>, at: At, slice: MixinSlice?,
        require: Int, expect: Int, allow: Int, order: Int, remap: Boolean,
        constraints: String, handler: Any
    ) {
        addRedirect(method, MethodTarget(target), RedirectFieldAccess.NONE, at, slice, false,
            require, expect, allow, order, remap, constraints, handler)
    }

    private fun addRedirect(
        method: KFunction<*>, target: MemberTarget, fieldAccess: RedirectFieldAccess,
        at: At, slice: MixinSlice?, staticHandler: Boolean, require: Int, expect: Int, allow: Int,
        order: Int, remap: Boolean, constraints: String, handler: Any
    ) {
        operations += RedirectDefinition(method, target, fieldAccess, at, slice, staticHandler,
            options(require, expect, allow, order, remap, constraints), handler)
    }

    private fun addOverwrite(method: KFunction<*>, staticHandler: Boolean, handler: Any) {
        operations += OverwriteDefinition(method, staticHandler, handler)
    }

    @PublishedApi
    internal fun options(
        require: Int, expect: Int, allow: Int, order: Int,
        remap: Boolean, constraints: String
    ): InjectorOptions = InjectorOptions(
        require, expect, allow, order, remap, constraints, activeGroup
    )
}

@PublishedApi
internal fun aerogelClassInitializerMarker(): Unit = Unit

/** Declares a target class without textual class, method, or descriptor names. */
public inline fun <reified T : Any> mixin(
    priority: Int = 1000,
    block: MixinScope<T>.() -> Unit
): MixinDefinition<T> {
    val scope = MixinScope<T>(currentMixinIdentity())
    scope.block()
    return scope.build(T::class.java, priority)
}

private val mixinIdentity: ThreadLocal<String?> = ThreadLocal()

public fun <R> withMixinIdentity(identity: String, block: () -> R): R {
    val previous = mixinIdentity.get()
    mixinIdentity.set(identity)
    return try {
        block()
    } finally {
        if (previous == null) mixinIdentity.remove() else mixinIdentity.set(previous)
    }
}

@PublishedApi
internal fun currentMixinIdentity(): String = mixinIdentity.get() ?: "anonymous"
