# Mixins

Aerogel supports ordinary Sponge Mixin classes and a typed Kotlin DSL. The DSL compiles to normal Mixin bytecode during the plugin build; the server does not interpret scripts and there is no second transformation engine.

Use one `.mixin.kts` file per generated Mixin:

```kotlin
// src/main/mixins/ServerBrand.mixin.kts
import dev.aerogel.api.mixin.At
import dev.aerogel.api.mixin.mixin
import net.minecraft.server.MinecraftServer

mixin<MinecraftServer>(priority = 900) {
    inject(
        MinecraftServer::getServerModName,
        at = At.HEAD,
        cancellable = true
    ) { callback ->
        callback.returnValue = "example"
    }
}
```

The file name becomes the generated Mixin name. Aerogel resolves every Kotlin member reference to its exact JVM owner, name, and descriptor, emits the Mixin class, creates `<plugin-id>.generated.mixins.json`, and adds it to plugin metadata automatically.

## What the DSL supports

The DSL covers all executable injector families in Sponge Mixin 0.8.7:

- `inject` and `injectStatic`
- `modifyArg` and `modifyArgStatic`
- `modifyArgs` and `modifyArgsStatic`
- `modifyVariable` and `modifyVariableStatic`
- `modifyConstant` and `modifyConstantStatic`
- `redirect` and `redirectStatic`, including method, constructor, and field access redirects
- `overwrite` and `overwriteStatic`

It also generates standard `@Accessor`, `@Invoker`, `@Shadow`, `@Final`, `@Mutable`, and `@Unique` members. These are real members merged by Sponge Mixin, not reflective access to private Minecraft fields.

Standard Java Mixin classes remain supported for unusual declaration-oriented features such as `@Pseudo`, soft `@Implements`, custom injection-point classes, and complex surrogate sets. They can coexist with generated Kotlin Mixins in the same plugin.

## Target receiver and arguments

For an instance target, the handler receiver is the target object. Target method parameters precede the callback:

```kotlin
mixin<MinecraftServer> {
    inject(MinecraftServer::tickServer, at = At.HEAD) { shouldKeepTicking, callback ->
        if (!shouldKeepTicking.asBoolean) callback.cancel()
    }
}
```

Void methods receive `CallbackInfo`. Returning methods receive `CallbackInfoReturnable<R>`, with `R` inferred from the method reference. Use the `*Static` form for a static target; the build fails when an instance and static form are mixed.

## Injection points

Built-in typed points include:

- `At.HEAD`, `At.RETURN`, `At.TAIL`, and `At.CTOR_HEAD`
- `At.invoke(method)` and `At.invokeAssign(method)`
- `At.invokeString(method, literal)`
- `At.field(property)`
- `At.construct(Type::class.java)` and `At.construct(::Constructor)`
- `At.jump()`, `At.load()`, `At.store()`, and `At.CONSTANT`

Configure an ordinal, opcode, shift, slice id, or injection-point arguments without replacing the typed target:

```kotlin
val secondReturn = At.RETURN.configured(ordinal = 1)
val afterCall = At.invoke(Target::work).configured(shift = AtShift.AFTER)
```

Use `at(first, second)` when an annotation accepts multiple points, and `MixinSlice` to bound a search. `At.explicit(...)` is an intentional low-level escape hatch for a selector that cannot exist as a Kotlin reference, not the normal API.

## Constructors and class initialization

Constructor references retain argument types and autocomplete:

```kotlin
mixin<Target> {
    injectConstructor(::Target, at = At.RETURN) { callback ->
        // `this` is the newly constructed Target.
    }

    classInitializer(at = At.TAIL) { callback ->
        // Static initialization; there is no target receiver.
    }
}
```

Passing a constructor reference to `redirect` automatically emits a `NEW` selector and a constructor redirect descriptor.

## Modifying and redirecting code

```kotlin
mixin<Target> {
    modifyArg<Int>(
        Target::calculate,
        at = At.invoke(Helper::consume),
        index = 0
    ) { value -> value + 1 }

    modifyConstant<Int>(
        Target::limit,
        constant = ConstantSelector.value(64)
    ) { 128 }

    redirect(Target::render, Renderer::draw) { renderer, value ->
        renderer.draw(value)
    }
}
```

`ModifyArgs` uses Sponge's `Args` object. Field redirects choose the correct GET/SET opcode from the referenced field. Static target methods use the corresponding `*Static` function.

## Accessors, invokers, shadows, and unique state

```kotlin
mixin<Target> {
    val value = accessor(Target::value)
    val calculate = invoker(Target::calculate)
    val shadowed = shadow(Target::existingField)
    val counter = uniqueField<Int>()

    inject(Target::run, at = At.HEAD) { callback ->
        value[this] = calculate(this, value[this])
        counter[this] = counter[this] + 1
    }
}
```

- `accessor`/`invoker` emit public standard Mixin bridges.
- `shadow` emits an actual shadow member and a collision-resistant bridge used by the external Kotlin handler.
- `mutableFinalShadow` emits `@Shadow @Final @Mutable`; use it only when changing a genuinely final target field is intentional.
- `uniqueField<T>()` adds an actual per-target-instance `@Unique` field. It is not backed by a global map. Its initial value is the JVM default for `T`.

The bridge names include the generated Mixin identity, so two plugin Mixins do not share generic accessor names.

## Local capture

First inspect locals with `locals = LocalCapture.PRINT` on a normal injection. Then declare the exact captured types:

```kotlin
injectLocals(
    Target::read,
    at = At.RETURN,
    capture = local<String>(),
    locals = LocalCapture.CAPTURE_FAILHARD
) { callback, localValue ->
    callback.returnValue = localValue
}
```

Use `locals<A, B>()` for two consecutive locals. Local capture depends on the target method's local-variable frame and is less stable across Minecraft updates than argument-only injection.

## Groups, slices, and validation

Every injector exposes Sponge's `require`, `expect`, `allow`, `order`, `remap`, and `constraints` settings. Group alternatives without copying a group name into each operation:

```kotlin
group("compatible-paths", min = 1, max = 1) {
    inject(Target::firstPath, at = At.HEAD, require = 0) { callback -> }
    inject(Target::secondPath, at = At.HEAD, require = 0) { callback -> }
}
```

Aerogel validates direct member references, static/instance form, descriptors, fields, overwrite visibility, and generated handlers during `build`. Failures point to the Mixin file instead of being deferred to server startup whenever possible.

## Standard Mixin classes

For a feature whose shape is inherently a Java class declaration, use an ordinary Mixin:

```java
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void example$beforeRun(CallbackInfo callback) {
    }
}
```

Register its JSON file alongside generated Mixins:

```kotlin
aerogel {
    plugin {
        mixin("example.mixins.json")
    }
}
```

Run `./gradlew build`. Generated source, bytecode, and configuration are build outputs and should not be committed. A reload can replace ordinary plugin callbacks, but changing already-applied Mixin structure can still require a full server restart.
