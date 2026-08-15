package dev.aerogel.api.mixin

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KMutableProperty1

public sealed interface MixinMemberDefinition

public class AccessorDefinition internal constructor(
    public val field: KProperty1<*, *>,
    public val getterName: String,
    public val setterName: String?,
    public val remap: Boolean
) : MixinMemberDefinition

public class InvokerDefinition internal constructor(
    public val method: KFunction<*>,
    public val bridgeName: String,
    public val remap: Boolean
) : MixinMemberDefinition

public class UniqueFieldDefinition internal constructor(
    public val fieldName: String,
    public val fieldType: Class<*>,
    public val getterName: String,
    public val setterName: String,
    public val silent: Boolean
) : MixinMemberDefinition

public class ShadowFieldDefinition internal constructor(
    public val field: KProperty1<*, *>,
    public val getterName: String,
    public val setterName: String?,
    aliases: List<String>,
    public val remap: Boolean,
    public val mutableFinal: Boolean
) : MixinMemberDefinition {
    public val aliases: List<String> = aliases.toList()
}

public class ShadowMethodDefinition internal constructor(
    public val method: KFunction<*>,
    public val bridgeName: String,
    aliases: List<String>,
    public val remap: Boolean
) : MixinMemberDefinition {
    public val aliases: List<String> = aliases.toList()
}

public abstract class MixinBridgeHandle internal constructor(private val bridgeName: String) {
    @Volatile private var owner: Class<*>? = null
    @Volatile private var handle: MethodHandle? = null

    protected fun call(instance: Any, vararg arguments: Any?): Any? {
        val type = instance.javaClass
        var current = handle
        if (current == null || owner !== type) {
            synchronized(this) {
                current = handle
                if (current == null || owner !== type) {
                    val bridge = type.methods.singleOrNull { it.name == bridgeName }
                        ?: throw IllegalStateException("Mixin bridge $bridgeName is not present on ${type.name}")
                    current = MethodHandles.publicLookup().unreflect(bridge)
                    owner = type
                    handle = current
                }
            }
        }
        return current!!.invokeWithArguments(listOf(instance) + arguments)
    }
}

public class FieldAccessor<T : Any, out V> internal constructor(
    bridgeName: String
) : MixinBridgeHandle(bridgeName) {
    @Suppress("UNCHECKED_CAST")
    public operator fun get(instance: T): V = call(instance) as V
}

public class MutableFieldAccessor<T : Any, V> internal constructor(
    getterName: String,
    setterName: String
) {
    private val getter: FieldAccessor<T, V> = FieldAccessor(getterName)
    private val setter: SetterBridge<T, V> = SetterBridge(setterName)

    public operator fun get(instance: T): V = getter[instance]
    public operator fun set(instance: T, value: V): Unit = setter.set(instance, value)

    private class SetterBridge<T : Any, V>(name: String) : MixinBridgeHandle(name) {
        fun set(instance: T, value: V) { call(instance, value) }
    }
}

public class Invoker0<T : Any, out R> internal constructor(name: String) : MixinBridgeHandle(name) {
    @Suppress("UNCHECKED_CAST")
    public operator fun invoke(instance: T): R = result(call(instance)) as R
}

public class Invoker1<T : Any, in A, out R> internal constructor(name: String) : MixinBridgeHandle(name) {
    @Suppress("UNCHECKED_CAST")
    public operator fun invoke(instance: T, first: A): R = result(call(instance, first)) as R
}

public class Invoker2<T : Any, in A, in B, out R> internal constructor(name: String) : MixinBridgeHandle(name) {
    @Suppress("UNCHECKED_CAST")
    public operator fun invoke(instance: T, first: A, second: B): R = result(call(instance, first, second)) as R
}

public class Invoker3<T : Any, in A, in B, in C, out R> internal constructor(name: String) : MixinBridgeHandle(name) {
    @Suppress("UNCHECKED_CAST")
    public operator fun invoke(instance: T, first: A, second: B, third: C): R =
        result(call(instance, first, second, third)) as R
}

private fun result(value: Any?): Any = value ?: Unit

@PublishedApi
internal fun bridgePrefix(identity: String): String = identity.hashCode().toUInt().toString(16)
