# Aerogel Agent Guide

This is the single-file operational guide for an AI coding agent building an Aerogel plugin. It describes the platform's intent, project setup, API boundaries, common implementation patterns, Mixin DSL, validation, reload behavior, and failure rules. Follow it before inventing abstractions or importing APIs from Bukkit, Paper, Fabric, or another server platform.

## Platform identity

Aerogel is a Minecraft Java Edition **server plugin loader**, beginning with Minecraft `26.2`. An Aerogel extension is called a **plugin**, even when it uses Mixins or reaches vanilla internals. Do not label an Aerogel plugin as a mod.

Aerogel combines two layers:

1. High-level, plugin-owned services for repetitive server work: events, Brigadier command registration, scheduling, inventories, scoreboards, boss bars, dialogs, translations, managed storage, and world lifecycle.
2. Direct access to official Minecraft server classes for maximum control. Events expose live `ServerPlayer`, `ServerLevel`, `Entity`, `ItemStack`, `Component`, packet, registry, and server objects rather than Bukkit-style wrappers.

Use this decision order:

1. Use an Aerogel high-level API when it models the operation and manages plugin ownership or reload cleanup.
2. Continue directly with a vanilla Minecraft API when Aerogel does not need to own the object.
3. Use a typed Aerogel event when a supported lifecycle point exists.
4. Use a Kotlin Mixin only when no event or callable vanilla API exposes the required behavior.
5. Use a standard Java Mixin only for declaration-oriented or unusual Mixin features that cannot be represented cleanly by the Kotlin DSL.

Never create a second wrapper hierarchy around vanilla players, worlds, entities, or items merely to imitate another platform.

## Preferred language

**Kotlin is the recommended language for Aerogel plugins.**

Use Kotlin for entrypoints, listeners, services, commands, and typed `.mixin.kts` files unless an existing Java codebase provides a concrete reason not to. Kotlin is preferred because Aerogel's Mixin DSL uses typed member references, Kotlin lambdas make event and command registration compact, nullable results are explicit, and the Gradle plugin configures Kotlin automatically.

Java remains supported. Do not translate a mature Java plugin solely to satisfy this recommendation.

## Compatibility and legal boundaries

- Current baseline: Aerogel `26.2-1`, Minecraft server `26.2`, Java `25`, Kotlin `2.4.10`.
- Aerogel release versions follow `<minecraft-version>-<revision>`, for example `26.2-1`.
- Compile against the official Minecraft server artifacts prepared by the Aerogel Gradle plugin.
- Minecraft, Mojang, Brigadier, Sponge Mixin, Kotlin DSL runtime, and Aerogel API classes are compile-time or server-provided dependencies. Do not package their classes inside a plugin JAR.
- Do not redistribute Mojang's server JAR or extracted Minecraft classes with a plugin.
- The Gradle build runs `validateAerogelPluginJar` and rejects protected runtime packages.
- Shade and relocate only third-party libraries that the plugin genuinely needs, and preserve their licenses and notices.
- Aerogel uses Sponge Mixin as a component under its license; a plugin should not bundle its own duplicate Mixin runtime.

## Complete new-project setup

### Requirements

- JDK 25 selected for Gradle and the IDE.
- Gradle wrapper recommended. Aerogel itself uses Gradle `9.3.0`.
- IntelliJ IDEA with the Kotlin and Gradle integrations is the recommended IDE.

### Project layout

```text
my-plugin/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradlew / gradlew.bat
├─ gradle/wrapper/...
└─ src/main/
   ├─ kotlin/com/example/myplugin/MyPlugin.kt
   ├─ mixins/ServerBrand.mixin.kts
   └─ resources/assets/my_plugin/lang/en_us.json
```

Do not create `aerogel.plugin.json` manually when using the Aerogel Gradle plugin. Metadata is generated from `build.gradle.kts`. Do not create a JSON file for generated Kotlin Mixins either.

### Resolve the Gradle plugin

For a normal external plugin, use Aerogel's public, anonymous Maven repository:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://raw.githubusercontent.com/AerogelMC/Aerogel/maven/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-plugin"
```

When developing against an Aerogel source checkout, use a composite build:

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("../Aerogel")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-plugin"
```

For a downloaded Aerogel Gradle repository package, extract the release ZIP and use its root as a Maven repository instead:

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("C:/path/to/aerogel-gradle-plugin-26.2-1") }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-plugin"
```

Use a portable repository path in a published template. Never commit a path to one developer's server directory.

### Configure the plugin

```kotlin
// build.gradle.kts
plugins {
    id("dev.aerogel.plugin") version "26.2-1"
}

group = "com.example"
version = "1.0.0"

aerogel {
    minecraft.set("26.2")

    plugin {
        id.set("my_plugin")
        name.set("My Plugin")
        entrypoint("com.example.myplugin.MyPlugin")
    }
}

tasks.jar {
    archiveBaseName.set("my-plugin")
}
```

The Gradle plugin configures Java 25, Kotlin, UTF-8, Aerogel API, Sponge Mixin, JetBrains annotations, and the official Minecraft development classpath. It downloads and verifies official artifacts. `minecraftServerJar` is optional and should only select an official local server-bundler JAR; it is not a normal project dependency.

### Prepare IDE symbols

Run:

```powershell
.\gradlew.bat setupAerogelDevelopment
```

Then refresh the Gradle project. Imports such as `ServerPlayer`, `ServerLevel`, `Component`, `Commands`, `Blocks`, and Minecraft registries must resolve and autocomplete. If they remain red, check JDK 25, task output, the Gradle JVM, and the IDE Gradle refresh before editing dependencies.

The task also marks plugin entrypoints and `@EventHandler` methods as reflectively used in IntelliJ. `configureAerogelIdea` refreshes only that IDE metadata.

### Minimal Kotlin entrypoint

```kotlin
package com.example.myplugin

import dev.aerogel.api.AerogelPlugin
import dev.aerogel.api.PluginContext
import dev.aerogel.api.event.player.PlayerJoinEvent
import net.minecraft.network.chat.Component

class MyPlugin : AerogelPlugin {
    override fun onLoad(context: PluginContext) {
        context.events().listen(PlayerJoinEvent::class.java) { event ->
            event.player().sendSystemMessage(Component.literal("Hello!"))
        }
    }
}
```

Build with:

```powershell
.\gradlew.bat clean build
```

The validated plugin JAR is placed in `build/libs`. Copy that JAR, and only that JAR, into the server's `plugins` directory.

## Metadata model

The generated metadata schema is version `1`.

- `id`: stable lowercase identifier matching `[a-z][a-z0-9_-]{1,63}`.
- `name`: human-readable display name.
- `version`: Gradle project version.
- `minecraft`: compatibility expression, normally generated from `minecraft.set(...)`.
- `entrypoints`: classes implementing `AerogelPlugin`.
- `mixins`: standard JSON Mixin configurations explicitly registered by the plugin. Generated Kotlin Mixin configs are added automatically.
- `depends`: required plugin IDs mapped to `*`, exact, `=`, `>=`, `>`, `<=`, or `<` version constraints.

Example dependency declaration:

```kotlin
aerogel {
    plugin {
        id.set("game")
        dependsOn("shared_api", ">=2.0.0")
    }
}
```

Dependencies determine load order and class-loader visibility. Reload a shared dependency and its consumers together after changing shared class shapes.

## Lifecycle and server readiness

`onLoad(context)` is for declarations and plugin-owned setup:

- register events and commands;
- schedule work;
- open managed data files;
- construct plugin services;
- prepare callbacks that will run after the server is ready.

Do not assume `context.minecraft()` is available during early `onLoad`. Calls requiring the live server throw `IllegalStateException` until readiness. Perform live-server work in `ServerStartedEvent`, a command callback, an event raised by a running server, or a synchronous scheduled task after startup.

```kotlin
context.events().listen(ServerStartedEvent::class.java) { event ->
    event.server().broadcast(Component.literal("Plugin ready."))
}
```

`onUnload(context)` must stop resources Aerogel does not own: custom threads, executors, sockets, database pools, file watchers, and global integrations. Cleanup should be fast and idempotent.

Reload creates a fresh plugin class loader and plugin instance. Never expect instance fields or static/object state to survive. Do not retain live `MinecraftServer`, `ServerLevel`, `ServerPlayer`, `Entity`, menu, registry, or packet objects across reload or restart. Persist identifiers such as UUIDs and resource keys, then resolve fresh objects.

## PluginContext and ownership

Important context accessors:

```kotlin
context.pluginId()
context.pluginVersion()
context.serverDirectory()
context.dataDirectory()
context.logger()
context.events()
context.server()
context.minecraft()
context.commands()
context.scheduler()
context.inventories()
context.scoreboards()
context.bossBars()
context.dialogs()
context.translations()
context.storage()
context.worlds()
```

Aerogel owns and automatically releases registrations, scheduled tasks, Aerogel inventories, plugin-created scoreboard entries, boss bars, dialogs, and managed files. Close a registration manually only when it must end before plugin unload.

Minecraft objects remain owned by Minecraft. Never call `close()` on a `MinecraftServer` or `ServerLevel` obtained from Aerogel, and do not treat an IDE AutoCloseable inspection as authority over server ownership.

## Events

### Lambda registration

```kotlin
context.events().listen(BlockBreakEvent::class.java) { event ->
    if (isProtected(event.position())) {
        event.cancel()
        event.player().sendSystemMessage(Component.literal("Protected area."))
    }
}
```

Use lambdas for compact listeners tied to an entrypoint or service.

### Annotation registration

```kotlin
class ProtectionListener(private val context: PluginContext) {
    @EventHandler(priority = EventPriority.EARLY)
    private fun onBreak(event: BlockBreakEvent) {
        if (isProtected(event.position())) event.cancel()
    }
}
```

Aerogel discovers annotated listener classes automatically; no explicit listener-class registration is required. A listener class may have a `PluginContext` constructor or no-argument constructor. A handler may be private, must return `Unit`/void, and must accept exactly one `AerogelEvent` subtype.

### Priority and cancellation

Order is `EARLY`, `NORMAL`, `LATE`, `MONITOR`, then registration order within one priority. Cancelled events skip listeners that did not request cancelled delivery. `MONITOR` always observes final state and must not change cancellation.

Only `CancellableEvent` can prevent an operation. Mutable event values change the real pending vanilla operation only when the event fires before commitment. Read-only after-events do not gain fake setters.

Callback exceptions are logged against the owning plugin; the plugin normally remains enabled. Do not catch and suppress linkage or logic failures merely to keep producing incorrect state.

### Event selection rules

- Use `PlayerInteractEvent` for player clicks. `action()` is left/right and `target()` is `AIR`, `BLOCK`, or `ENTITY`. Block left-clicks follow Minecraft's block-action packets; mining swing animations are not emitted as repeated air clicks.
- Do not infer interaction from `PlayerSwingEvent`; swing packets can also be caused by dropping items and other animations.
- Use `BlockBreakAttemptEvent` for raw client intent.
- Use `BlockMiningStart/Progress/Stop/AbortEvent` for mining phases.
- Use cancellable `BlockBreakEvent` at the confirmed pre-removal point.
- Use `BlockBrokenEvent` only to observe successful removal.
- Use `BlockStateChangeEvent` to observe or replace every pending vanilla state change. Branch on `changeType()` and `reason()`; use `sourceEntity()`, `sourcePosition()`, and `sourceLocation()` instead of inferring an actor from a stack trace.
- Use `PlayerItemUseStartEvent` and `PlayerItemUseEndEvent` for the accepted vanilla item-use lifecycle. End reasons are `COMPLETED`, `RELEASED`, and `INTERRUPTED`; cancelling preserves active use.
- Use `PlayerChatEvent#setMessage` for the message body and a chat renderer for the entire prefix/name/body/suffix presentation.
- `EntityDeathEvent` is not cancellable, but its final drops and experience are mutable.
- Packet events run before vanilla packet handling; cancellation skips handling. Prefer a semantic event when one exists.
- Cancelling `ChunkPreLoadEvent` is a real load denial, not a fake empty chunk. Mandatory synchronous callers may fail and ticket callers may retry.

Built-in categories include server lifecycle and ticks; command registration and execution; login, join, quit, respawn, death, teleport, game mode, chat, interactions, movement, input, item-use lifecycle, posture, flight, vehicle, inventory and client-action player events; block mining, breaking, placement, global state, and piston events; entity spawn, removal, damage, healing, health, absorption, air, freezing, pose, identity, visibility, gravity, silence, knockback, jumping, effects, equipment, mount, combustion, death, target, projectile, teleport, tame, and breed events; item drop/pickup and inventory/menu events; and world, chunk, weather, and explosion events.

Events expose live vanilla types. Do not search for an Aerogel `Player` or `World` wrapper.

## Commands and suggestions

Aerogel installs native Brigadier trees. Use `context.commands().register(...)`; the registration is plugin-owned and removed on reload. There is no simpler parallel command API.

```kotlin
context.commands().register(
    Commands.literal("game")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(
            Commands.literal("start")
                .then(
                    Commands.literal("confirmed")
                        .executes { command ->
                            command.source.sendSuccess(
                                { Component.literal("Game started.") },
                                false
                            )
                            1
                        }
                )
        )
)
```

Use vanilla argument types and `suggests` providers for completion. Suggestions are attached to the exact argument node that consumes them. Nested subcommands must be nested Brigadier literals, not parsed from one greedy string. Player and console completion use the same dispatcher after command synchronization.

Register commands in `onLoad`; Aerogel installs and synchronizes them when the live server is ready. Do not retain command sources beyond the invocation.

## Scheduler and threads

Minecraft world mutation belongs on the server thread.

```kotlin
context.scheduler().later(20) {
    context.logger().info("One second later")
}

context.scheduler().repeat(0, 20) {
    updateDisplay()
}

context.scheduler().async {
    val result = loadExternalData()
    context.scheduler().run {
        applyToMinecraft(result)
    }
}
```

Do not block event handlers, commands, tick callbacks, or Mixin handlers with file, HTTP, or database I/O. Async workers must return to a synchronous task before changing Minecraft state. Aerogel-owned scheduled tasks are cancelled on unload.

## Vanilla players, worlds, entities, and components

Use `Component` directly for text, styles, hover events, and click events.

Useful Aerogel extensions on vanilla objects include:

```kotlin
player.sendSystemMessage(Component.literal("Hello"))
player.sendOverlayMessage(Component.literal("Ready"))
player.sendTitle(title, subtitle, 10, 60, 20)
player.setDisplayName(Component.literal("Host"))
player.setTabListName(Component.literal("[Admin] Host"))
player.setTabListHidden(true)
player.setNameTagHidden(true)
player.setTabListHeaderFooter(header, footer)
player.giveItem(stack)
player.sendPacket(packet)

server.broadcast(Component.literal("Round complete"))
server.findPlayer("Steve")
server.broadcastPacket(packet)
```

`setDisplayName` affects vanilla display-name consumers and synchronizes the overhead name. The TAB name follows it unless a TAB-only name is set. TAB visibility and overhead-name visibility are independent. These APIs do not spawn TextDisplay entities or rewrite authenticated profiles.

Use vanilla level and entity methods directly, plus Aerogel conveniences such as player teleportation, nearby-entity lookup, UUID lookup, spawn, block changes, time, and weather. Do not invent Bukkit locations or materials; use `BlockPos`, `Vec3`, `BlockState`, `Blocks`, `ItemStack`, and resource keys.

## Inventories, scoreboards, boss bars, and dialogs

Create plugin-owned UI through context services so reload cleanup is automatic.

```kotlin
val inventory = context.inventories().create(3, Component.literal("Tools"))
inventory.item(0, ItemStack(Items.DIAMOND_PICKAXE))
inventory.open(player)

val board = context.scoreboards().main()
val objective = board.objective("coins", Component.literal("Coins"))
    .display(DisplaySlot.SIDEBAR)
objective.score(player.scoreboardName, 10)

val bar = context.bossBars().create(
    Component.literal("Raid"),
    BossBarColor.RED,
    BossBarOverlay.NOTCHED_10
).progress(0.5f).add(player)
```

High-level notice and confirmation dialogs are available, while `nativeDialog` accepts complete vanilla Minecraft 26.2 dialog objects. Use typed inventory events to prevent taking GUI items or to interpret clicks.

## Worlds

World APIs return live `ServerLevel` objects:

```kotlin
val flat = context.worlds().createFlat("arena")
val empty = context.worlds().createVoid("empty")
val nether = context.worlds().createVanilla("nether_arena", seed, VanillaDimension.NETHER)
val custom = context.worlds().create("islands", seed, generator)
```

Plugin world identifiers use `<plugin-id>:<local-id>`. `createVoid` creates a genuinely empty world and does not add a platform. Flat generation accepts vanilla `FlatLevelGeneratorSettings`. Custom generation accepts a vanilla `ChunkGenerator`, preserving full freedom.

Create worlds only after server readiness and on the server thread. Recreate runtime registration on every full startup; saved dimension data remains on disk. A custom generator must be deterministic and thread-safe because generation may execute away from the server thread.

`unload` safely saves and detaches a plugin world. `delete` unloads and permanently deletes only that world's vanilla-resolved dimension directory. Both reject built-in levels; deletion is irreversible.

## Player-visible translations

Use translation resources for player-visible plugin messages that should be localized:

```text
src/main/resources/assets/my_plugin/lang/en_us.json
src/main/resources/assets/my_plugin/lang/ko_kr.json
```

```json
{
  "my_plugin.ready": "Ready!"
}
```

```kotlin
player.sendSystemMessage(
    context.translations().componentFor(player, "my_plugin.ready")
)
```

Always provide `en_us`; missing locales fall back to English and then to the key. Prefix keys with the plugin ID. Keep operational server logs concise English. Vanilla translation keys can be used directly with `Component.translatable(...)` when the message is truly a vanilla concept.

## Managed storage

Write mutable data only under `context.dataDirectory()`. Prefer `context.storage()` over direct synchronous file I/O.

```kotlin
data class PluginData(
    val round: Int = 0,
    val scores: Map<UUID, Int> = emptyMap()
)

val data = context.storage().json(
    "state.json",
    PluginData::class.java,
    ::PluginData
)

data.load().thenAccept { loaded ->
    context.scheduler().run {
        applyLoadedState(loaded)
    }
}

data.update { previous -> previous.copy(round = previous.round + 1) }
```

Never call `load().join()` or `flush().join()` on the server thread. `set`, `update`, and `edit` mark data dirty. Saves are serialized, burst changes are coalesced, and destination replacement is atomic when supported. Malformed files fail instead of being silently overwritten.

Use `TypeRef` for generic collections. Use built-ins for exact Minecraft values:

- `itemStack` and `itemStacks`
- `component`
- `compoundTag`
- `blockState`
- `dataComponentPatch`
- `globalPos`, `blockPos`, and `identifier`
- `minecraftJson` for plugin records containing supported Minecraft values
- `codecJson` for another Mojang `Codec<T>`

Minecraft-aware files use live registry access and may finish loading only after server startup. They preserve complete vanilla codec state, including an `ItemStack`'s data-component patch. Never serialize live server runtime objects.

## Kotlin Mixin DSL

Place generated Mixins in `src/main/mixins/*.mixin.kts`. One file produces one ordinary Sponge Mixin class and one generated config entry.

```kotlin
import dev.aerogel.api.mixin.InjectionPoint
import dev.aerogel.api.mixin.mixin
import net.minecraft.server.MinecraftServer

mixin<MinecraftServer> {
    inject(
        method = MinecraftServer::getServerModName,
        at = InjectionPoint.HEAD,
        cancellable = true
    ) { callback ->
        callback.returnValue = "my-plugin"
    }
}
```

The DSL avoids class-name, method-name, and descriptor strings when Kotlin can reference the target. Minecraft source navigation and autocomplete remain available.

Supported executable families:

- `inject` / `injectStatic`
- `modifyArg` / `modifyArgStatic`
- `modifyArgs` / `modifyArgsStatic`
- `modifyVariable` / `modifyVariableStatic`
- `modifyConstant` / `modifyConstantStatic`
- `redirect` / `redirectStatic` for methods, constructors, and field GET/SET
- `overwrite` / `overwriteStatic`

Supported structural bridges:

- `accessor(Target::field)`
- `invoker(Target::method)`
- `shadow(Target::member)`
- `mutableFinalShadow(Target::field)`
- `uniqueField<T>()`, which creates a real per-target-instance `@Unique` field

Supported points and selectors include `HEAD`, `RETURN`, `TAIL`, `CTOR_HEAD`, invoke, invoke-assign, invoke-string, field, constructor/NEW, jump, load, store, constant, ordinal/opcode/shift configuration, multiple points, slices, constant discriminators, injector validation options, and injector groups.

Constructor and class initialization examples:

```kotlin
injectConstructor(::Target, at = At.RETURN) { callback -> }
classInitializer(at = At.TAIL) { callback -> }
```

Local capture is explicit and typed:

```kotlin
injectLocals(
    Target::read,
    at = At.RETURN,
    capture = local<String>(),
    locals = LocalCapture.CAPTURE_FAILHARD
) { callback, value ->
    callback.returnValue = value
}
```

Use a Mixin only for a missing hook. Prefer narrow injections to `overwrite`. Keep handlers small and non-blocking. `require = 1` is appropriate when failure means the plugin cannot function; use `require = 0` only for deliberate compatibility alternatives, preferably in a group.

Mixin method-body reload is best-effort. New targets, fields, interfaces, hierarchy changes, structural changes, and already transformed classes can require a full restart. A Mixin prepare/apply failure occurs before normal plugin callback isolation and may stop startup.

Standard Java Mixin classes and JSON configs remain supported for `@Pseudo`, soft `@Implements`, custom injection-point classes, and complex surrogate declarations. Register those configs with `plugin.mixin("name.mixins.json")`.

## Reload, restart, and failure isolation

Relevant built-in commands:

```text
/plugins list
/plugins reload
/plugins reload <plugin-id>
/tps
/restart
```

`/plugins` alone is intentionally incomplete. Reload discovers new JARs, unloads plugin-owned registrations, stages immutable copies, and loads fresh class loaders. A normal callback exception is logged while the plugin remains active. An entrypoint constructor or `onLoad` failure disables that plugin while server startup continues. Disabled plugins appear in `/plugins list` with localized disabled state.

Use `/restart` for loader updates, Minecraft version changes, Mixin structural changes, native libraries, or leaked JVM-global state. A process restart replaces the standalone JAR and plugin JARs from disk; a plugin reload is not a JVM restart.

Plugins are trusted code, not sandboxed code. They share the server process's filesystem, network, reflection, native-access, and JVM permissions.

## Troubleshooting decision tree

### Minecraft or Aerogel symbols are red

1. Confirm JDK 25 is both the project SDK and Gradle JVM.
2. Run `setupAerogelDevelopment`.
3. Refresh the Gradle project, not only the editor cache.
4. Confirm `dev.aerogel.plugin` is applied.
5. Do not add the runtime server JAR as `implementation`.

If only a `.mixin.kts` file reports that `java.util.concurrent.Executor` (or another
JDK supertype of a Minecraft class) is inaccessible, rerun `setupAerogelDevelopment` and refresh
Gradle so IntelliJ reloads Aerogel's custom script definition and its JVM classpath. Keep
`src/main/mixins` outside ordinary Kotlin/Java source roots: Kotlin intentionally warns that
standalone scripts inside source roots are ignored during module compilation. Do not add
`java.base` or a duplicate JDK dependency.

### `Minecraft server is not ready yet`

Move `context.minecraft()`, world creation, registry-dependent work, and server mutations out of early `onLoad` into `ServerStartedEvent` or a later server-thread callback.

### `NoClassDefFoundError` or `NoSuchMethodError`

Align the Aerogel server, Gradle plugin, and plugin build revision. Rebuild the plugin completely, ensure the correct JAR was copied, and fully restart after API or Mixin structure changes. Do not hot-reload across incompatible shared API class shapes.

### Gradle/IDE reports Kotlin Gradle internal class errors

Stop stale Gradle daemons, remove the affected project's `.gradle/configuration-cache` if present, rebuild with `--no-configuration-cache`, and refresh the Gradle project. Do not work around it by downgrading random Kotlin libraries inside the plugin.

### Reload did not apply a Mixin

Compare the generated Mixin structure. If targets, descriptors, fields, interfaces, hierarchy, or injector structure changed, use a full restart. Reload warnings should not appear when the Mixin structure is unchanged.

### Tick stalls or delayed commands

Find blocking I/O, large synchronous loops, packet listeners, tick events, or injected handlers. Move external work async. Keep Minecraft mutation synchronous. Do not introduce arbitrary per-tick work queues unless the operation truly needs a time budget.

### Console text is corrupted

Use UTF-8 source and resource files and explicit `StandardCharsets.UTF_8` for direct file code. Do not localize vanilla server logs by replacing arbitrary formatted messages.

## Agent implementation rules

Before completing an Aerogel plugin task, an agent must:

1. Confirm the target Minecraft and Aerogel versions.
2. Prefer Kotlin unless maintaining an existing Java project.
3. Apply the Aerogel Gradle plugin and generate metadata; do not hand-maintain metadata for a normal project.
4. Run `setupAerogelDevelopment` before diagnosing missing vanilla types.
5. Search Aerogel API and event types before adding a Mixin.
6. Use live vanilla types instead of Bukkit/Paper/Fabric abstractions.
7. Use translation resources for localized player-visible plugin messages.
8. Keep blocking work off the server thread.
9. Tie resources to `PluginContext` services or clean them in `onUnload`.
10. Persist stable values, UUIDs, and keys rather than live runtime objects.
11. Keep server-provided classes out of the plugin JAR.
12. Run `clean build` and require `validateAerogelPluginJar` to pass.
13. Test initial load, command suggestions, relevant cancellation paths, plugin reload, and full restart when Mixins exist.
14. Never start a user's server merely to build or inspect a plugin unless the user explicitly asks for a server run.

## Release checklist

- Kotlin-first source layout is coherent and IDE autocomplete works.
- `aerogel` metadata ID, name, version, Minecraft version, entrypoint, dependencies, and standard Mixin configs are correct.
- Every early `context.minecraft()` call has been removed or deferred.
- Event choice matches the real vanilla lifecycle point.
- Commands are native Brigadier trees with working client and console suggestions.
- Player-visible messages use components and translations where localization is expected.
- Async continuations return to the server thread before Minecraft mutation.
- Managed storage load failures are handled without blocking joins.
- No live player/world/entity/menu/registry object is persisted or retained across unload.
- Mixin targets and descriptors match the exact Minecraft version.
- The plugin JAR contains no Minecraft, Aerogel API, or Sponge Mixin classes.
- `clean build` and validation pass.
- Reload behavior and restart requirements are documented for users.
