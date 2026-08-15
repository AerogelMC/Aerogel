package dev.aerogel.api.mixin

import kotlin.reflect.KFunction

/** Options shared by standard Mixin injectors. */
public class InjectorOptions(
    public val require: Int = 1,
    public val expect: Int = 1,
    public val allow: Int = -1,
    public val order: Int = 1000,
    public val remap: Boolean = true,
    public val constraints: String = "",
    public val group: InjectorGroup? = null
) {
    init {
        require(require >= -1) { "require must be -1 or greater" }
        require(expect >= -1) { "expect must be -1 or greater" }
        require(allow >= -1) { "allow must be -1 or greater" }
    }
}

public class InjectorGroup(
    public val name: String,
    public val min: Int = -1,
    public val max: Int = -1
) {
    init {
        require(name.isNotBlank()) { "Injector group name cannot be blank" }
        require(min >= -1) { "group min must be -1 or greater" }
        require(max >= -1) { "group max must be -1 or greater" }
        require(max < 0 || min < 0 || max >= min) { "group max cannot be smaller than min" }
    }
}

public sealed interface MixinOperationDefinition {
    public val method: KFunction<*>
    public val handler: Any
    public val options: InjectorOptions
    public val staticHandler: Boolean get() = false
}

public class InjectionDefinition internal constructor(
    override val method: KFunction<*>,
    public val points: List<At>,
    public val slices: List<MixinSlice>,
    public val id: String,
    public val cancellable: Boolean,
    public val locals: LocalCapture,
    localTypes: List<Class<*>>,
    public val classInitializer: Boolean,
    override val staticHandler: Boolean,
    override val options: InjectorOptions,
    override val handler: Any
) : MixinOperationDefinition
{
    public val localTypes: List<Class<*>> = localTypes.toList()
}

public enum class LocalCapture {
    NONE,
    PRINT,
    CAPTURE_FAILSOFT,
    CAPTURE_FAILHARD,
    CAPTURE_FAILEXCEPTION
}

public class ModifyArgDefinition @PublishedApi internal constructor(
    override val method: KFunction<*>,
    public val at: At,
    public val slice: MixinSlice?,
    public val index: Int,
    public val valueType: Class<*>,
    override val staticHandler: Boolean,
    override val options: InjectorOptions,
    override val handler: Any
) : MixinOperationDefinition

public class ModifyArgsDefinition internal constructor(
    override val method: KFunction<*>,
    public val at: At,
    public val slice: MixinSlice?,
    override val staticHandler: Boolean,
    override val options: InjectorOptions,
    override val handler: Any
) : MixinOperationDefinition

public class ModifyVariableDefinition @PublishedApi internal constructor(
    override val method: KFunction<*>,
    public val at: At,
    public val slice: MixinSlice?,
    public val valueType: Class<*>,
    public val print: Boolean,
    public val ordinal: Int,
    public val index: Int,
    names: List<String>,
    public val argsOnly: Boolean,
    override val staticHandler: Boolean,
    override val options: InjectorOptions,
    override val handler: Any
) : MixinOperationDefinition {
    public val names: List<String> = names.toList()
}

public class ModifyConstantDefinition @PublishedApi internal constructor(
    override val method: KFunction<*>,
    public val constants: List<ConstantSelector>,
    public val slices: List<MixinSlice>,
    public val valueType: Class<*>,
    override val staticHandler: Boolean,
    override val options: InjectorOptions,
    override val handler: Any
) : MixinOperationDefinition

public class RedirectDefinition internal constructor(
    override val method: KFunction<*>,
    public val target: MemberTarget,
    public val fieldAccess: RedirectFieldAccess,
    public val at: At,
    public val slice: MixinSlice?,
    override val staticHandler: Boolean,
    override val options: InjectorOptions,
    override val handler: Any
) : MixinOperationDefinition

public enum class RedirectFieldAccess { NONE, GET, SET }

public class OverwriteDefinition internal constructor(
    override val method: KFunction<*>,
    override val staticHandler: Boolean,
    override val handler: Any
) : MixinOperationDefinition {
    override val options: InjectorOptions = InjectorOptions()
}

/** Build-time description lowered to an ordinary Sponge Mixin class. */
public class MixinDefinition<T : Any> internal constructor(
    public val target: Class<T>,
    public val priority: Int,
    operations: List<MixinOperationDefinition>,
    members: List<MixinMemberDefinition>
) {
    public val operations: List<MixinOperationDefinition> = operations.toList()
    public val injections: List<InjectionDefinition> = operations.filterIsInstance<InjectionDefinition>()
    public val members: List<MixinMemberDefinition> = members.toList()
}
