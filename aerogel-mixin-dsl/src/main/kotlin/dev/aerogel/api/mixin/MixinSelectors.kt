package dev.aerogel.api.mixin

import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

/** Marks the declarative part of an Aerogel Mixin script. */
@DslMarker
public annotation class AerogelMixinDsl

/** JVM instruction movement applied after an injection point has matched. */
public enum class AtShift {
    NONE,
    BEFORE,
    AFTER,
    BY
}

/** A type-safe member target used by an `@At` selector. */
public sealed interface MemberTarget

public class MethodTarget internal constructor(public val method: KFunction<*>) : MemberTarget

public class FieldTarget internal constructor(public val field: KProperty<*>) : MemberTarget

public class TypeTarget internal constructor(public val type: Class<*>) : MemberTarget

/**
 * Explicit JVM selector for members which cannot be referenced by Kotlin, such as a private
 * member from an un-widened third-party development JAR.
 */
public class JvmMemberTarget internal constructor(public val selector: String) : MemberTarget

/** One standard Sponge `@At` declaration. */
public class At private constructor(
    public val value: String,
    public val target: MemberTarget?,
    public val id: String,
    public val slice: String,
    public val shift: AtShift,
    public val by: Int,
    public val ordinal: Int,
    public val opcode: Int,
    args: List<String>,
    public val remap: Boolean,
    public val unsafe: Boolean
) : AtSelection {
    public val args: List<String> = args.toList()
    override val points: List<At> get() = listOf(this)

    public fun configured(
        id: String = this.id,
        slice: String = this.slice,
        shift: AtShift = this.shift,
        by: Int = this.by,
        ordinal: Int = this.ordinal,
        opcode: Int = this.opcode,
        args: List<String> = this.args,
        remap: Boolean = this.remap,
        unsafe: Boolean = this.unsafe
    ): At = At(value, target, id, slice, shift, by, ordinal, opcode, args, remap, unsafe)

    public companion object {
        @JvmField public val HEAD: At = simple("HEAD")
        @JvmField public val RETURN: At = simple("RETURN")
        @JvmField public val TAIL: At = simple("TAIL")
        @JvmField public val INVOKE_ASSIGN: At = simple("INVOKE_ASSIGN")
        @JvmField public val JUMP: At = simple("JUMP")
        @JvmField public val CONSTANT: At = simple("CONSTANT")
        @JvmField public val LOAD: At = simple("LOAD")
        @JvmField public val STORE: At = simple("STORE")
        @JvmField public val CTOR_HEAD: At = simple("CTOR_HEAD")

        @JvmStatic
        public fun invoke(
            method: KFunction<*>,
            ordinal: Int = -1,
            shift: AtShift = AtShift.NONE,
            by: Int = 0,
            remap: Boolean = true
        ): At = if (method.name == "<init>") {
            construct(method, ordinal, shift, by, remap)
        } else {
            targeted("INVOKE", MethodTarget(method), ordinal, shift, by, remap = remap)
        }

        @JvmStatic
        public fun invokeAssign(
            method: KFunction<*>,
            ordinal: Int = -1,
            shift: AtShift = AtShift.NONE,
            by: Int = 0,
            remap: Boolean = true
        ): At = targeted("INVOKE_ASSIGN", MethodTarget(method), ordinal, shift, by, remap = remap)

        @JvmStatic
        public fun invokeString(
            method: KFunction<*>, literal: String, ordinal: Int = -1,
            shift: AtShift = AtShift.NONE, by: Int = 0, remap: Boolean = true
        ): At = At(
            "INVOKE_STRING", MethodTarget(method), "", "", shift, by, ordinal, -1,
            listOf("ldc=$literal"), remap, true
        )

        @JvmStatic
        public fun field(
            field: KProperty<*>,
            opcode: Int = -1,
            ordinal: Int = -1,
            shift: AtShift = AtShift.NONE,
            by: Int = 0,
            remap: Boolean = true
        ): At = targeted("FIELD", FieldTarget(field), ordinal, shift, by, opcode, remap)

        @JvmStatic
        public fun construct(
            type: Class<*>,
            ordinal: Int = -1,
            shift: AtShift = AtShift.NONE,
            by: Int = 0,
            remap: Boolean = true
        ): At = targeted("NEW", TypeTarget(type), ordinal, shift, by, remap = remap)

        @JvmStatic
        public fun construct(
            constructor: KFunction<*>,
            ordinal: Int = -1,
            shift: AtShift = AtShift.NONE,
            by: Int = 0,
            remap: Boolean = true
        ): At = targeted("NEW", MethodTarget(constructor), ordinal, shift, by, remap = remap)

        @JvmStatic
        public fun jump(opcode: Int = -1, ordinal: Int = -1): At =
            simple("JUMP", ordinal = ordinal, opcode = opcode)

        @JvmStatic
        public fun load(ordinal: Int = -1, opcode: Int = -1): At =
            simple("LOAD", ordinal = ordinal, opcode = opcode)

        @JvmStatic
        public fun store(ordinal: Int = -1, opcode: Int = -1): At =
            simple("STORE", ordinal = ordinal, opcode = opcode)

        @JvmStatic
        public fun explicit(
            value: String,
            target: String = "",
            id: String = "",
            slice: String = "",
            shift: AtShift = AtShift.NONE,
            by: Int = 0,
            ordinal: Int = -1,
            opcode: Int = -1,
            args: List<String> = emptyList(),
            remap: Boolean = true,
            unsafe: Boolean = true
        ): At = At(
            value,
            target.takeIf(String::isNotEmpty)?.let(::JvmMemberTarget),
            id,
            slice,
            shift,
            by,
            ordinal,
            opcode,
            args,
            remap,
            unsafe
        )

        private fun simple(
            value: String,
            ordinal: Int = -1,
            opcode: Int = -1
        ): At = At(
            value, null, "", "", AtShift.NONE, 0, ordinal, opcode, emptyList(), true, true
        )

        private fun targeted(
            value: String,
            target: MemberTarget,
            ordinal: Int,
            shift: AtShift,
            by: Int,
            opcode: Int = -1,
            remap: Boolean
        ): At = At(
            value, target, "", "", shift, by, ordinal, opcode, emptyList(), remap, true
        )
    }
}

/** One or more injection points for annotations whose `at` member is an array. */
public sealed interface AtSelection {
    public val points: List<At>
}

private class MultipleAt(points: List<At>) : AtSelection {
    override val points: List<At> = points.toList()
}

public fun at(vararg points: At): AtSelection {
    require(points.isNotEmpty()) { "At least one injection point is required" }
    return MultipleAt(points.toList())
}

/** Backwards-compatible name used by the first Aerogel DSL release. */
public typealias InjectionPoint = At

/** A standard Mixin slice. */
public class MixinSlice(
    public val id: String = "",
    public val from: At = At.HEAD,
    public val to: At = At.TAIL
)

/** Constant discriminator for `@ModifyConstant`. */
public class ConstantSelector private constructor(
    public val kind: Kind,
    public val value: Any?,
    public val ordinal: Int,
    public val slice: String,
    conditions: List<ZeroCondition>,
    public val log: Boolean
) {
    public enum class Kind { ANY, NULL, INT, FLOAT, LONG, DOUBLE, STRING, CLASS }
    public enum class ZeroCondition { LESS_THAN_ZERO, LESS_THAN_OR_EQUAL_TO_ZERO, GREATER_THAN_OR_EQUAL_TO_ZERO, GREATER_THAN_ZERO }
    public val conditions: List<ZeroCondition> = conditions.toList()

    public companion object {
        @JvmStatic public fun any(ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.ANY, null, ordinal, slice, emptyList(), log)

        @JvmStatic public fun nullValue(ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.NULL, null, ordinal, slice, emptyList(), log)

        @JvmStatic public fun value(value: Int, ordinal: Int = -1, slice: String = "", log: Boolean = false,
            conditions: List<ZeroCondition> = emptyList()): ConstantSelector =
            ConstantSelector(Kind.INT, value, ordinal, slice, conditions, log)

        @JvmStatic public fun value(value: Float, ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.FLOAT, value, ordinal, slice, emptyList(), log)

        @JvmStatic public fun value(value: Long, ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.LONG, value, ordinal, slice, emptyList(), log)

        @JvmStatic public fun value(value: Double, ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.DOUBLE, value, ordinal, slice, emptyList(), log)

        @JvmStatic public fun value(value: String, ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.STRING, value, ordinal, slice, emptyList(), log)

        @JvmStatic public fun value(value: Class<*>, ordinal: Int = -1, slice: String = "", log: Boolean = false): ConstantSelector =
            ConstantSelector(Kind.CLASS, value, ordinal, slice, emptyList(), log)
    }
}
