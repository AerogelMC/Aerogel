# Developing Aerogel plugins

> **Target:** Aerogel `26.2-5`, Minecraft Java Edition `26.2+`, Java `25`
> **Language:** English · [한국어](DEVELOPMENT.ko.md)

This guide covers the complete plugin-development workflow: project setup, metadata, lifecycle, vanilla access, events, commands, user interfaces, translations, reload behavior, Mixins, packaging, and common failure modes.

Aerogel is intentionally built around two layers:

- **Aerogel APIs** handle repetitive work and plugin-owned lifetimes: events, commands, tasks, inventories, scoreboards, boss bars, dialogs, translations, and managed files.
- **Minecraft server classes** remain directly available for everything else. Events expose live `ServerPlayer`, `ServerLevel`, `Entity`, `ItemStack`, packet, and component objects rather than copies or generic wrappers.

The practical rule is simple: use the highest-level Aerogel API that expresses the operation, continue with vanilla APIs when you need more control, and use a Mixin only when neither exposes the required hook.

---

## Contents

- [Choose the right layer](#choose-the-right-layer)
- [Requirements](#requirements)
- [Create a project](#create-a-project)
- [Plugin metadata](#plugin-metadata)
- [Entrypoints and lifecycle](#entrypoints-and-lifecycle)
- [Plugin context and ownership](#plugin-context-and-ownership)
- [Events](#events)
- [Commands and suggestions](#commands-and-suggestions)
- [Scheduling and threading](#scheduling-and-threading)
- [Players, worlds, entities, and packets](#players-worlds-entities-and-packets)
- [Inventories and GUI](#inventories-and-gui)
- [Scoreboards, boss bars, and dialogs](#scoreboards-boss-bars-and-dialogs)
- [Components, chat, and translations](#components-chat-and-translations)
- [Plugin data and dependencies](#plugin-data-and-dependencies)
- [Mixins](#mixins)
- [Build, install, and reload](#build-install-and-reload)
- [Failure isolation](#failure-isolation)
- [Troubleshooting](#troubleshooting)
- [Release checklist](#release-checklist)

---

## Choose the right layer

| Need | Preferred tool | Why |
|---|---|---|
| Observe or cancel a supported action | Aerogel event | Stable intent and a defined cancellation point |
| Register a command | Vanilla Brigadier through `context.commands()` | Full command trees, arguments, requirements, redirects, and suggestions |
| Send a message or manipulate a player | `ServerPlayer` | No duplicate player abstraction |
| Read or change a loaded world | `ServerLevel` and vanilla APIs | Full access to Minecraft state |
| Create a chest GUI, boss bar, dialog, or scoreboard entry | Aerogel service | Ownership and reload cleanup are handled automatically |
| Run code later | Aerogel scheduler | Tasks are tied to the plugin lifecycle |
| Persist plugin state | `context.storage()` | Coalesced asynchronous I/O and atomic file replacement |
| Intercept behavior with no suitable API or event | Mixin | Maximum control, with a higher compatibility cost |

Avoid a Mixin when an event already represents the action. An event documents when cancellation is safe; an injection point ties the plugin to a particular implementation detail.

## Requirements

- JDK 25
- Gradle 8-compatible project
- Aerogel Gradle plugin `26.2-5`
- Minecraft Java Edition server `26.2` or a later version supported by the installed Aerogel build
- IntelliJ IDEA or another Java IDE with Gradle support

Aerogel's Gradle plugin downloads the official Minecraft server artifacts, verifies Mojang's published hashes, and adds the extracted server class path as `compileOnly`. Minecraft code is not copied into the plugin JAR.

## Create a project

### 1. Make the project layout

```text
my-plugin/
├─ settings.gradle.kts
├─ build.gradle.kts
└─ src/
   └─ main/
      ├─ java/
      │  └─ com/example/myplugin/MyPlugin.java
      └─ resources/
         └─ assets/my_plugin/lang/
            ├─ en_us.json
            └─ ko_kr.json
```

Do not manually add `aerogel.plugin.json` when using the Gradle plugin. It is generated from the `aerogel` block.

### 2. Configure the Aerogel plugin repository

Until the Gradle plugin is published to a public plugin repository, extract `aerogel-gradle-plugin-26.2-5.zip` and point `pluginManagement` at the extracted Maven repository:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("path/to/extracted/aerogel-gradle-plugin-26.2-5") }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-plugin"
```

Use forward slashes or a properly escaped path on Windows.

### 3. Configure the plugin

```kotlin
// build.gradle.kts
plugins {
    id("dev.aerogel.plugin") version "26.2-5"
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
```

The plugin configures Java toolchains, `--release 25`, UTF-8 compilation, Aerogel API, the official Minecraft class path, Mixin, and JetBrains annotations.

### 4. Prepare IDE symbols

```powershell
.\gradlew.bat setupAerogelDevelopment
```

Then refresh the Gradle project in the IDE. Imports such as `ServerPlayer`, `Component`, `Commands`, and `Blocks` should resolve and autocomplete.

The setup task also runs automatically before Java compilation, but running it explicitly makes IDE setup failures easier to diagnose.

In IntelliJ IDEA, the setup task also registers `@EventHandler` methods and the
entrypoint classes declared in the Aerogel Gradle DSL as reflective entry
points. This prevents valid plugin callbacks and plugin classes from being
reported as unused. Use `./gradlew configureAerogelIdea` when only this IDE
metadata needs to be refreshed.

### 5. Create the entrypoint

```java
package com.example.myplugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.server.ServerStartedEvent;

public final class MyPlugin implements AerogelPlugin {
    @Override
    public void onLoad(PluginContext context) {
        context.logger().info("Loading " + context.pluginId());

        context.events().listen(ServerStartedEvent.class, event ->
            context.logger().info("The Minecraft server is ready."));
    }

    @Override
    public void onUnload(PluginContext context) {
        context.logger().info("Unloading " + context.pluginId());
    }
}
```

## Plugin metadata

The generated `aerogel.plugin.json` uses schema version `1`.

| Field | Required | Meaning |
|---|---:|---|
| `schemaVersion` | Yes | Metadata format. Currently `1`. |
| `id` | Yes | Stable lowercase identifier. Must match `[a-z][a-z0-9_-]{1,63}`. |
| `version` | Yes | Plugin version shown by Aerogel and checked by dependencies. |
| `name` | No | Human-readable display name. Defaults to the ID. |
| `minecraft` | No | Minecraft version requirement. Defaults to `>=26.2`. |
| `entrypoints` | No | Classes implementing `AerogelPlugin`. |
| `mixins` | No | Mixin configuration resources inside the JAR. |
| `depends` | No | Required plugin IDs mapped to version constraints. |

Example generated metadata:

```json
{
  "schemaVersion": 1,
  "id": "my_plugin",
  "version": "1.0.0",
  "name": "My Plugin",
  "minecraft": ">=26.2",
  "entrypoints": [
    "com.example.myplugin.MyPlugin"
  ],
  "mixins": [],
  "depends": {
    "shared_api": ">=2.0.0"
  }
}
```

Supported dependency constraints are `*`, an exact version, `=`, `>=`, `>`, `<=`, and `<`. Compound ranges and caret syntax are not currently supported.

Dependencies determine load order and class-loader visibility. They are not optional dependencies and do not provide a service registry by themselves.

An entrypoint is optional when the JAR only contains automatically discovered `@EventHandler` methods. A plugin may also declare multiple entrypoints; they load in metadata order and unload in reverse order.

## Entrypoints and lifecycle

### `onLoad`

Use `onLoad` to declare plugin-owned resources:

- register commands and event listeners;
- schedule tasks;
- load configuration files;
- construct services owned by the plugin.

Commands can be registered before the live server is ready; Aerogel installs them when the server becomes available.

Do **not** call `context.minecraft()` unconditionally during early `onLoad`. The Minecraft server handle may not exist yet. Check `context.server().ready()` or wait for `ServerStartedEvent` before performing work that needs a running server.

```java
context.events().listen(ServerStartedEvent.class, event -> {
    var server = event.server();
    server.broadcast(Component.literal("Plugin ready."));
});
```

### `onUnload`

Use `onUnload` for state Aerogel does not own:

- flush pending plugin data;
- stop executors or threads created by the plugin;
- close files, sockets, database pools, and watchers;
- unregister integrations managed outside `PluginContext`;
- clear static references.

Aerogel closes its owned registrations before releasing the plugin class loader. Cleanup code should be fast, idempotent, and tolerant of a partially initialized plugin.

### Reload lifecycle

Reload uses a fresh plugin class loader and a fresh plugin instance. Treat it as:

```text
old onUnload → old class loader released → new onLoad
```

Do not rely on instance fields or static fields surviving reload. `onReload` exists as a convenience lifecycle method, but normal loader reload creates a new instance rather than invoking it on the old one.

## Plugin context and ownership

`PluginContext` contains identity, directories, logging, events, and plugin-scoped services.

| Method | Use |
|---|---|
| `pluginId()` | Stable metadata ID |
| `pluginVersion()` | Loaded plugin version |
| `serverDirectory()` | Dedicated server root |
| `dataDirectory()` | Writable `plugins/<id>` directory |
| `logger()` | Plugin-prefixed server logger |
| `events()` | Typed synchronous event bus |
| `server()` | Aerogel services and readiness |
| `minecraft()` | Live `MinecraftServer`; only when ready |
| `commands()` | Brigadier command registration |
| `scheduler()` | Synchronous and asynchronous tasks |
| `inventories()` | Chest inventory creation/wrapping |
| `scoreboards()` | Main scoreboard access |
| `bossBars()` | Boss-bar creation |
| `dialogs()` | Notice, confirmation, and native dialogs |
| `translations()` | Plugin language resources |
| `storage()` | Typed, asynchronously persisted plugin data |

### Owned resources

Aerogel automatically releases these when the plugin unloads:

- event and command registrations;
- scheduled tasks;
- Aerogel inventory views and inventories;
- score objectives and teams created through the plugin service;
- boss bars and their viewers;
- dialogs and their callbacks.
- managed data files, including a bounded final flush.

Every resource implements `Registration`. Call `close()` only when it must end before plugin unload; repeated calls are safe.

```java
var bar = context.bossBars().create(Component.literal("Round"));

// Later, before plugin unload:
bar.close();
```

Live Minecraft objects are **not** plugin-owned resources. Never close, retain indefinitely, or attempt to replace `MinecraftServer`, `ServerLevel`, or `ServerPlayer` instances.

## Events

Aerogel has one typed synchronous event bus with two registration styles. Both styles have the same priority and cancellation behavior and are removed automatically on reload.

See [EVENTS.md](EVENTS.md) for the full event catalog and exact lifecycle contracts.

### Lambda listeners

Use lambdas for small handlers and handlers that naturally belong to the entrypoint or a service instance.

```java
context.events().listen(PlayerJoinEvent.class, event -> {
    ServerPlayer player = event.player();
    player.sendSystemMessage(Component.literal("Welcome!"));
});
```

Priority and cancelled-event delivery are optional:

```java
context.events().listen(
    BlockBreakEvent.class,
    EventPriority.EARLY,
    true,
    event -> {
        if (isProtected(event.position())) {
            event.cancel();
        }
    }
);
```

### Automatically discovered handlers

Use annotations when you want listener classes organized by feature. No explicit listener-class registration is required.

```java
public final class ProtectionListener {
    private final PluginContext context;

    public ProtectionListener(PluginContext context) {
        this.context = context;
    }

    @EventHandler(priority = EventPriority.EARLY)
    private void onBreak(BlockBreakEvent event) {
        if (isProtected(event.position())) {
            event.cancel();
            event.player().sendSystemMessage(Component.literal("Protected area."));
        }
    }
}
```

Aerogel scans class metadata without initializing every class. A listener class may have a `PluginContext` constructor or a no-argument constructor. Static handlers need no instance. Handler methods may be private, but they must return `void` and accept exactly one `AerogelEvent` subtype.

### Priority and cancellation

Listeners run in this order:

1. `EARLY`
2. `NORMAL`
3. `LATE`
4. `MONITOR`

Within one priority, registration order is retained. A cancelled event skips listeners that did not request `receiveCancelled`, except `MONITOR`, which always observes the final state. A `MONITOR` handler must not change cancellation; Aerogel restores the previous state and logs an error if it does.

Only events implementing `CancellableEvent` can prevent their operation. Observation events occur after a result exists and cannot safely be cancelled.

### Mutable event results

When vanilla has not committed an operation yet, Aerogel exposes setters for meaningful inputs and applies the edited values to the real operation. Examples include damage and healing amounts, effects, equipment, teleport destinations, targets, dropped items, experience, explosions, block-state changes, and command text. An after-the-fact notification remains read-only when changing it could no longer produce a coherent vanilla result.

`EntityDeathEvent` is not cancellable, but its loot and experience are mutable. Vanilla calculates both first; Aerogel postpones spawning them until every listener has returned.

```java
@EventHandler
private void onDeath(EntityDeathEvent event) {
    event.clearDrops();
    event.addDrop(reward.copy());
    event.setDroppedExperience(25);
}
```

`drops()` is a live list. `setDrops(...)`, `addDrop(...)`, and `clearDrops()` are provided for the common cases. Every final non-empty stack is copied before it is spawned, and the normal entity-spawn event path still applies.

### Choose the correct block event

```text
raw client request
  └─ BlockBreakAttemptEvent
      └─ vanilla accepts mining
          ├─ BlockMiningStartEvent
          ├─ BlockMiningProgressEvent
          ├─ BlockMiningStopEvent / BlockMiningAbortEvent
          └─ vanilla approves destruction
              ├─ BlockBreakEvent      (last cancellable point)
              └─ BlockBrokenEvent     (removal succeeded)
```

Use `BlockBreakEvent` for protection and drop replacement. Use `BlockBreakAttemptEvent` only when raw input matters. For example, a creative player hitting a block with a sword can produce an attempt but cannot produce a confirmed break event when vanilla rejects destruction.

### Packet events

`PlayerPacketEvent` subtypes run before vanilla handles the serverbound packet. Cancelling the event skips that packet's vanilla handler. The typed packet remains available through `event.packet()`.

Packet events are appropriate for protocol-level rules and details not yet normalized into a higher-level event. Prefer a semantic event when both exist.

Movement, input, inventory, and suggestion packets can be frequent. Keep these handlers small and never perform blocking I/O in them.

### Chunk pre-load caution

`ChunkPreLoadEvent` is a hard load denial. Cancelling it returns vanilla's unloaded result, and ticket-driven callers may retry. Do not cancel spawn or infrastructure chunk loads unless the plugin also controls the requesting operation. Synchronous vanilla callers that require a chunk may surface a load failure rather than accept a fake chunk.

### Event callback failures

A normal exception thrown from an Aerogel event listener is logged against that plugin; the listener remains registered and the plugin remains enabled. Fix repeated errors quickly because a failing high-frequency listener can flood logs and consume tick time.

## Commands and suggestions

Aerogel registers vanilla Brigadier trees directly. There is no second command model, so nested literals, typed arguments, redirects, requirements, tooltips, and asynchronous suggestion providers work as they do in Minecraft.

### Nested command

```java
context.commands().register(
    Commands.literal("game")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(Commands.literal("start")
            .then(Commands.literal("confirmed")
                .executes(command -> {
                    command.getSource().sendSuccess(
                        () -> Component.literal("Game started."),
                        true
                    );
                    return 1;
                })))
);
```

This creates `/game start confirmed`. Each additional `.then(...)` is another tree level; do not parse the complete command from one greedy string unless the syntax is intentionally free-form.

### Typed argument and completion

```java
context.commands().register(
    Commands.literal("game")
        .then(Commands.literal("join")
            .then(Commands.argument("arena", StringArgumentType.word())
                .suggests((command, builder) -> {
                    for (String arena : arenaNames()) {
                        builder.suggest(arena);
                    }
                    return builder.buildFuture();
                })
                .executes(command -> {
                    String arena = StringArgumentType.getString(command, "arena");
                    joinArena(command.getSource(), arena);
                    return 1;
                })))
);
```

Use Minecraft argument types and suggestion providers when they already express the value: players, entities, coordinates, resources, dimensions, items, and more. This gives the client correct validation and richer completion.

Aerogel guards command execution and suggestion callbacks. Normal plugin exceptions are logged and converted to a failed command or empty suggestions instead of stopping the server.

Command registrations are plugin-owned and removed on reload. Store the returned registration only when you need to unregister the command earlier.

## Scheduling and threading

Minecraft world state is primarily single-threaded. Use synchronous tasks for all world, entity, inventory, and packet mutations.

```java
context.scheduler().later(20, () -> {
    context.logger().info("Approximately one second later at 20 TPS.");
});

context.scheduler().repeat(0, 20, this::updateBossBar);
```

Use asynchronous work only for operations that do not touch live Minecraft state:

```java
context.scheduler().async(() -> {
    Result result = loadFromDatabase();

    context.scheduler().run(() -> {
        applyResultToWorld(result);
    });
});
```

Important rules:

- A tick is not guaranteed to be 50 ms when the server is overloaded.
- `asyncLater` converts its delay to wall-clock time in 50 ms units; it is not synchronized to server TPS.
- Do not read mutable vanilla collections from an async worker unless the vanilla API explicitly documents it as safe.
- Do not call `.join()`, wait on a database, sleep, or perform network/file I/O on the server thread.
- Aerogel cancels scheduler tasks on unload. Executors and threads created directly by the plugin remain the plugin's responsibility.

## Players, worlds, entities, and packets

Aerogel convenience methods live on the vanilla objects that own the behavior.

### Players and broadcasts

```java
MinecraftServer server = context.minecraft();
ServerPlayer player = server.findPlayer("Steve").orElseThrow();

player.sendSystemMessage(Component.literal("Hello"));
player.sendOverlayMessage(Component.literal("Ready"));
player.sendTitle(
    Component.literal("Round start"),
    Component.literal("Good luck"),
    10, 60, 20
);
player.setDisplayName(Component.literal("Host"));
player.setTabListName(Component.literal("[Admin] Host"));
player.setTabListHidden(true);
player.setNameTagHidden(true);
player.setTabListHeaderFooter(
    Component.literal("Aerogel"),
    Component.literal("Players: 10")
);
player.giveItem(new ItemStack(Items.DIAMOND));

server.broadcast(Component.literal("Round complete"));
```

`setDisplayName` overrides the component returned by vanilla `getDisplayName()` and synchronizes
the overhead player name seen by vanilla clients. Vanilla chat, death messages, advancement
announcements, and command output which resolve that display name use the override automatically.
The TAB list follows it by default; `setTabListName` creates a TAB-only override. Clear either layer
with `clearDisplayName` or `clearTabListName`.

`setTabListHidden(true)` removes only the player's TAB-list row. The player remains connected and
visible in the world. Pass `false` to restore the row; `isTabListHidden()` returns the current state.
`setNameTagHidden(true)` independently hides the overhead name tag. Pass `false` to show it again;
`isNameTagHidden()` returns the current state.

TAB headers and footers belong to the receiving player. `setTabListHeader` and
`setTabListFooter` update one side without erasing the other; `setTabListHeaderFooter` changes both.
Call these again from the join event because a new `ServerPlayer` is created for a new connection.
For the overhead name, Aerogel sends viewer-local player-info and scoreboard-team packets. This
does not create a `TextDisplay`, mutate the authenticated server profile, or alter the server's
scoreboard. Reapplying the value is automatic when another client starts tracking the player.

Other conveniences include `kick`, `clearTitle`, predicate-based `removeItems`, `clearInventory`, `sendPacket`, online-player lookup, and UUID lookup. Existing vanilla methods remain available.

`ServerPlayer replacement = player.respawn()` invokes vanilla's complete respawn path. Always use
the returned player afterward; the original instance is stale. The boolean overload selects
vanilla's `keepEverything` path and, like all lifecycle changes, must run on the server thread.

Viewer-local rendering also lives directly on the receiving `ServerPlayer`. Use `setBlock` and
`resetBlock` for fake blocks; `setVisible` for per-viewer entity tracking; and `setGlowing`,
`setInvisible`, `setOnFire`, or `setEquipment` for persistent client overrides. Each state-changing
method has an explicit reset where `false` would otherwise mean “force the false value.”
`setGlowColorOverride` accepts vanilla `TeamColor`; arbitrary RGB outlines are not supported by a
stock Minecraft client. `clearViewOverrides` restores everything Aerogel tracks for that viewer.
Movement, animation, particles, sounds, HUD values, weather, and border methods send snapshots and
can be superseded by subsequent vanilla synchronization.

### Worlds and entities

```java
ServerLevel level = context.minecraft().overworld();
Collection<ServerLevel> loaded = context.worlds().loaded();
ServerLevel arena = context.worlds().createFlat("arena");
ServerLevel seededArena = context.worlds().createFlat("practice", 12345L);
ServerLevel empty = context.worlds().createVoid("empty");
ServerLevel nether = context.worlds().createVanilla(
    "nether_arena", 12345L, VanillaDimension.NETHER
);
ServerLevel islands = context.worlds().create(
    "islands", 12345L, new IslandChunkGenerator(biomeSource)
);

level.setDayTime(6000);
level.clearWeather(20 * 60);
level.block(0, 64, 0, Blocks.STONE.defaultBlockState(), 3);

Collection<Entity> nearby = level.nearbyEntities(
    0, 64, 0, 16,
    entity -> entity instanceof LivingEntity
);

level.findEntity(uniqueId).ifPresent(Entity::discard);
level.teleport(player, 0.5, 65, 0.5);
```

Use Aerogel conveniences for common operations and continue directly with vanilla registries, chunks, recipes, particles, sounds, data components, and entity APIs when needed.

`worlds().loaded()` returns an immutable snapshot of every currently loaded level. An unqualified world id is automatically namespaced to the plugin, so `arena` becomes `<plugin-id>:arena`. A fully qualified id such as `shared:arena` is useful when multiple plugins intentionally share one level. `createVoid` creates a completely empty overworld-type level and does not add a spawn platform. The `createFlat` overload accepting `FlatLevelGeneratorSettings` exposes Minecraft's complete superflat layer, biome, structure, lake, and decoration rules. `createVanilla` creates a built-in overworld, Nether, or End generator with its matching dimension type. `create(id, generator)` and its seed/dimension overloads accept a vanilla-compatible `ChunkGenerator` implemented by the plugin, retaining the full 26.2 generation pipeline rather than imposing an Aerogel terrain callback. The returned `ServerLevel` remains server-owned and must not be closed by the plugin. World creation must run on the server thread after the server is attached; use `ServerStartedEvent`, not the initial `onLoad` callback. The world remains server-owned across plugin reloads, its chunks are saved normally, and the plugin must recreate its generator and call `create` again on every full server start to restore the runtime dimension registration. Chunk generation is asynchronous: keep the generator thread-safe and deterministic, and do not read mutable live-world state from it.

`context.worlds().unload(id)` safely saves and unloads a dynamic level, moving any remaining players to the primary overworld spawn first. `delete(id)` safely unloads it and permanently removes its dimension directory. Both reject Minecraft's built-in overworld, Nether, and End and must run on the server thread. Never use `delete` when the saved world may still be needed.

### Packets

```java
player.sendPacket(new ClientboundClearTitlesPacket(true));
context.minecraft().broadcastPacket(packet);
```

Packets are version-sensitive and can disconnect clients if constructed with invalid state. Prefer `Component`, player, inventory, boss-bar, and dialog APIs when they cover the same result.

Never retain a `ServerPlayer` after quit or across a full server restart. Store the UUID and resolve the live player again.

## Persistent data and gameplay object APIs

Use `context.persistentData()` for small plugin-namespaced values on server, player, entity,
block entity, world, block, or item identities. When the vanilla object is already available, use
`server.data()`, `level.data()`, `level.data(pos)`, `entity.data()`, `blockEntity.data()`, or
`stack.data()` and select the namespace with `.namespace(context)`. Use `new ItemStack(item).edit()`
or `stack.edit()` to edit real vanilla `ItemStack` components,
`context.recipes()` and `context.loot()` for plugin-owned vanilla registrations,
`context.menus()` for routed read-only GUIs, `context.virtualEntities()` for selected-client
unspawned entities, and `context.blockBatches()` for chunk-coalesced bulk block changes.

Persistent data is owned by vanilla save objects: players, entities, and block entities carry it in
their normal NBT, item stacks carry it in `CUSTOM_DATA`, and server, world, and coordinate containers use
that world's `SavedData` file. It is not copied into `plugins/<id>`. Use it on the server thread.
The player and entity overloads deliberately require live objects; a UUID-only API would create a
second storage database instead of following vanilla object lifetime.

Other direct forms follow the same rule: `player.openMenu(menu)`, `entity.virtual(context, viewers)`,
and `level.batch()`. A `RecipeHolder` can call `register(context)`, and a `LootTable` can call
`register(context, path)`. The context remains explicit for registrations because plugin ownership
controls unload cleanup; Aerogel does not guess it from threads, stack traces, or global state.

These APIs and their lifecycle rules are documented with examples in [API.md](API.md).

## Inventories and GUI

Aerogel creates one-to-six-row chest inventories and can wrap a compatible live vanilla `Container`.

```java
Inventory menu = context.inventories().create(
    3,
    Component.literal("Choose a game")
);

menu.item(13, new ItemStack(Items.DIAMOND));
InventoryView view = menu.open(player);
```

To make a display-only GUI, cancel clicks targeting that open menu and handle the selected slot yourself:

```java
context.events().listen(InventoryClickEvent.class, event -> {
    if (event.player().containerMenu != view.menu()) {
        return;
    }

    event.cancel();

    if (event.slot() == 13) {
        startGame(event.player());
        view.close();
    }
});
```

Keep these details in mind:

- Validate every slot index; custom inventories range from `0` to `size() - 1`.
- A packet click can refer to the player's own inventory as well as the upper container. Check the menu and slot meaning before applying an action.
- Cancelling `InventoryClickEvent` prevents vanilla packet processing. Keep the server's inventory state authoritative.
- The inventory and all open views close automatically on plugin unload.
- `Inventory#vanilla()` exposes the live `Container` when advanced behavior is necessary.

## Scoreboards, boss bars, and dialogs

### Scoreboard

```java
Scoreboard board = context.scoreboards().main();

Objective coins = board.objective("coins", Component.literal("Coins"))
    .display(DisplaySlot.SIDEBAR)
    .score(player.getScoreboardName(), 10);

Team builders = board.team("builders")
    .prefix(Component.literal("[Build] "))
    .friendlyFire(false)
    .add(player.getScoreboardName());
```

Objectives and teams created through the plugin service are removed on unload. Objects found through `findObjective` or `findTeam` wrap existing vanilla state without taking ownership.

Use unique names, usually prefixed with the plugin ID, to avoid collisions on the main scoreboard.

### Boss bar

```java
BossBar bar = context.bossBars().create(
    Component.literal("Raid"),
    BossBarColor.RED,
    BossBarOverlay.NOTCHED_10
).progress(0.5f).add(player);
```

Progress must be between `0.0` and `1.0`. Viewer membership, visibility, color, overlay, music, fog, and screen darkening are supported.

### Dialog

```java
Dialog dialog = context.dialogs().confirmation(
    Component.literal("Continue?"),
    List.of(Component.literal("This action changes the world.")),
    Component.literal("Yes"),
    Component.literal("No"),
    result -> confirm(result.player()),
    result -> cancel(result.player())
);

dialog.show(player);
```

Use `notice` and `confirmation` for common cases. Use `nativeDialog` with a vanilla `net.minecraft.server.dialog.Dialog` when you need a 26.2 feature not represented by the high-level builders.

Dialog callbacks are guarded like other Aerogel callbacks. Payloads are exposed as optional vanilla NBT `Tag` values.

## Components, chat, and translations

### Use vanilla components

Aerogel uses `net.minecraft.network.chat.Component` everywhere. This preserves styling, click events, hover events, and translatable content.

```java
Component message = Component.literal("Open website")
    .withStyle(style -> style
        .withColor(ChatFormatting.AQUA)
        .withUnderlined(true));

player.sendSystemMessage(message);
```

Component colors are preserved in supported player output and Aerogel's console rendering.

### Translate plugin messages

Place language files under `assets/<plugin-id>/lang/` inside the plugin JAR:

```text
src/main/resources/
└─ assets/my_plugin/lang/
   ├─ en_us.json
   ├─ ko_kr.json
   └─ ja_jp.json
```

```json
// en_us.json
{
  "my_plugin.game.started": "The game has started.",
  "my_plugin.player.welcome": "Welcome, %s!"
}
```

```json
// ko_kr.json
{
  "my_plugin.game.started": "게임이 시작되었습니다.",
  "my_plugin.player.welcome": "%s님, 환영합니다!"
}
```

```java
Component welcome = context.translations().componentFor(
    player,
    "my_plugin.player.welcome",
    player.getDisplayName()
);

player.sendSystemMessage(welcome);
```

`componentFor` chooses a fallback from the recipient's client language. `component` uses `en_us`; `componentForLocale` uses an explicit locale; and `text` resolves plain text for logs or non-component output. Locale codes are normalized to lowercase with underscores.

Always provide `en_us`. If a locale or key is missing, Aerogel falls back to `en_us`, then to the key itself.

Use plugin-prefixed keys to prevent collisions. Player-visible plugin messages should use translation resources; operational logs should remain concise English unless there is a strong reason to localize them.

### Change complete chat presentation

`PlayerChatEvent#setMessage` changes the displayed message body. Use a renderer when the prefix, player name, separators, or suffix must also change:

```java
@EventHandler
private void onChat(PlayerChatEvent event) {
    event.setRenderer((player, message) -> ChatRender.builder(message)
        .prefix(
            Component.literal("[").withStyle(ChatFormatting.DARK_GRAY),
            player.getDisplayName().copy().withStyle(ChatFormatting.AQUA),
            Component.literal("] ").withStyle(ChatFormatting.GRAY)
        )
        .suffix(Component.literal(" ✓").withStyle(ChatFormatting.GREEN))
        .build());
}
```

The prefix and suffix are independent components, so every delimiter can have its own style. Keeping the provided `message` as the render body preserves the signed body while changing presentation.

The event runs after signed-message validation and immediately before player broadcast and console logging. Do not fabricate or reinterpret signature state.

## Plugin data and dependencies

### Data directory

Write mutable plugin data only under `context.dataDirectory()`:

```java
Path configFile = context.dataDirectory().resolve("config.json");
```

For structured state, prefer managed storage over direct `Files.read*` and `Files.write*` calls:

```java
record PluginData(int round, Map<UUID, Integer> scores) {
    static PluginData empty() {
        return new PluginData(0, Map.of());
    }
}

DataFile<PluginData> data = context.storage().json(
    "state.json",
    PluginData.class,
    PluginData::empty
);

data.load().thenAccept(loaded -> context.scheduler().run(() ->
    applyLoadedState(loaded)
));
```

Opening a file starts its load on Aerogel's shared storage workers. Never block the server thread
with `load().join()` or `flush().join()`. A continuation attached directly to `load()` also runs on
an I/O worker, so enqueue Minecraft work through `scheduler().run(...)` as shown above.

Use immutable replacement when practical:

```java
data.update(previous -> new PluginData(
    previous.round() + 1,
    previous.scores()
));
```

For mutable collections, use `edit` so Aerogel knows the value changed:

```java
DataFile<Map<UUID, Integer>> coins = context.storage().json(
    Path.of("coins.json"),
    new TypeRef<Map<UUID, Integer>>() { },
    HashMap::new
);

coins.load().thenRun(() -> coins.edit(values -> values.put(playerId, 10)));
```

`set`, `update`, and `edit` mark the value dirty. Automatic saving waits 250 ms by default and
coalesces a burst of changes into one ordered write. `save()` is the user-facing alias of
`flush()`; both force all changes visible at the call to disk and return a `CompletableFuture`.
Plugin unload performs a bounded final flush.

Storage writes use a temporary file in the same directory, force its contents to disk, and replace
the destination atomically when the filesystem supports it. A malformed existing file fails the
load and is not silently replaced with a default value. `lastFailure()` exposes the most recent
load or save error.

Paths may contain subdirectories but must stay inside `context.dataDirectory()`. The default size
limit is 64 MiB per file. `StorageOptions` can change autosave delay, close timeout, automatic-save
behavior, and the size limit. `StorageOptions.manual()` disables background autosaves; the final
unload flush still applies.

### Minecraft values in JSON

Do not send `ItemStack` through ordinary reflective Gson serialization. Aerogel's Minecraft-aware
storage uses the exact vanilla 26.2 `Codec` with the live frozen registry access, so the item ID,
count, complete data-component patch, custom data, names, enchantments, nested container contents,
profiles, and every other registered component round-trip together.

```java
DataFile<ItemStack> reward = context.storage().itemStack(
    "reward.json",
    () -> ItemStack.EMPTY
);

DataFile<List<ItemStack>> slots = context.storage().itemStacks(
    "slots.json",
    List::of
);
```

`itemStacks` uses the optional stack codec for each entry. Empty stacks are encoded too, so list
indices can safely represent inventory slots. There are equivalent built-ins for `Component`,
`CompoundTag`, `BlockState`, `DataComponentPatch`, `GlobalPos`, `BlockPos`, and `Identifier`.

For a plugin record containing Minecraft values, use `minecraftJson`:

```java
record Kit(String id, Component title, List<ItemStack> slots, CompoundTag metadata) { }

DataFile<List<Kit>> kits = context.storage().minecraftJson(
    "kits.json",
    new TypeRef<List<Kit>>() { },
    List::of
);
```

The Minecraft-aware Gson layer only adapts known value types; the surrounding records, lists, and
maps remain normal plugin data. For every other vanilla or plugin-defined Mojang codec, use the
generic bridge:

```java
DataFile<MyRule> rule = context.storage().codecJson(
    "rule.json",
    MyRule.CODEC,
    MyRule::defaults
);
```

These files may be opened during `onLoad`, but their asynchronous load waits for the live server's
registry access. Always continue from `load()` instead of assuming they have loaded during
`onLoad`. Aerogel first encodes through `NbtOps`, then projects the tag tree into structured JSON.
Normal strings, ints, compounds, and lists remain ordinary JSON. Byte, short, long, float, double,
and typed arrays receive a small `$nbt` marker. This prevents JSON text parsing from
collapsing distinct NBT types while keeping the file readable.

JSON works best for records and explicitly declared POJO types. Generic maps and lists require
`TypeRef`; polymorphic runtime subtypes require a custom `DataCodec<T>`. Do not persist live
`ServerPlayer`, `ServerLevel`, `Entity`, menu, registry, packet, or other Minecraft runtime objects.
Persist UUIDs and resource keys, then resolve fresh objects from the current server.

`value()` returns the live in-memory object. Mutating it directly cannot trigger automatic saving;
always make changes through `set`, `update`, or `edit`.

Do not write into the plugin JAR, the staged plugin cache, or Minecraft's own files unless the plugin explicitly owns that integration.

Recommended practices:

- use UTF-8;
- validate configuration before replacing live state;
- write to a temporary file and atomically replace the destination when possible;
- keep a schema version in persistent data;
- flush important state during `onUnload` and server stopping events;
- avoid long synchronous disk writes during ticks.

### Third-party libraries

Aerogel API, Minecraft, Mixin, and annotations are compile-only. Never bundle their classes in the plugin JAR. `validateAerogelPluginJar`, which runs during `check`, rejects accidental copies.

A normal Gradle `implementation` dependency is not automatically copied into a plain JAR. If a plugin needs a third-party library, shade it into the plugin artifact and relocate packages when collision risk exists. Do not shade:

- `dev.aerogel.api.*`;
- `net.minecraft.*`;
- `com.mojang.*` server/Brigadier classes supplied by Minecraft;
- `org.spongepowered.asm.mixin.*`.

Comply with every bundled library's license and include required notices.

### Plugin dependencies

```kotlin
aerogel {
    plugin {
        id.set("game")
        dependsOn("shared_api", ">=2.0.0")
    }
}
```

Aerogel rejects missing, incompatible, duplicate, or cyclic required dependencies before loading the affected graph. A dependent plugin can see classes from its declared dependency.

Do not cache objects from another plugin across reload. Reload all related plugins after changing a shared API or its implementation so consumers receive compatible class-loader types.

## Mixins

Mixins are the escape hatch for behavior that cannot be expressed through a supported event or API. They are not required for normal plugin development.

Use a Mixin when you must:

- intercept an internal vanilla branch before no public hook exists;
- expose a private field or method through an accessor;
- modify a return value or argument at a precise call site;
- implement a feature whose semantics cannot be represented by the current event catalog.

Do not use a Mixin only to send messages, create commands, manipulate inventories, or observe an existing Aerogel event.

### Configuration

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.myplugin.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [
    "MinecraftServerMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

Declare the resource in the Gradle metadata:

```kotlin
aerogel {
    plugin {
        id.set("my_plugin")
        mixin("my-plugin.mixins.json")
    }
}
```

```java
@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void my_plugin$beforeServerLoop(CallbackInfo callbackInfo) {
        // Keep injected work small.
    }
}
```

### Mixin rules

- Prefix injected method names with the plugin ID.
- Prefer narrow `@Inject`, `@ModifyArg`, or `@ModifyVariable` hooks over `@Overwrite`.
- Keep `required: true` and `defaultRequire: 1` for hooks the plugin cannot function without; silent mismatch is harder to diagnose than a startup failure.
- Verify descriptors and targets against the exact Minecraft version.
- Never perform blocking work in an injected server-thread method.
- Treat Mixin code as trusted code with no sandbox.
- A Mixin preparation or application failure can prevent the server from starting before normal plugin failure isolation is available.

Mixin reload is best-effort. Method-body changes may hot-swap, but structural changes, new targets, fields, interfaces, hierarchy changes, and already transformed classes can require a full `/restart`. Design the plugin so a failed hot swap produces a clear warning and the old behavior remains understandable.

See [MIXINS.md](MIXINS.md) for injection patterns and lower-level guidance.

## Build, install, and reload

### Build

```powershell
.\gradlew.bat clean build
```

The plugin JAR is written to `build/libs`. The build also validates that forbidden server and API classes were not bundled.

### Install

Copy only the plugin JAR into the server's `plugins` directory:

```text
server/
├─ Aerogel-26.2-29.jar
└─ plugins/
   └─ my-plugin-1.0.0.jar
```

Mutable data belongs in the automatically created `plugins/<plugin-id>/` directory, not beside compiled classes in the JAR.

### Inspect and reload

Aerogel provides:

```text
/plugins list
/plugins reload
/plugins reload <plugin-id>
/tps
/networkstats
/networkstats reset
/networkstats mode vanilla
/networkstats mode aerogel
/restart
```

`/plugins` by itself is intentionally incomplete. `/plugins list` shows the display name and gray `<id>`; initialization-disabled plugins are marked as disabled.

`/networkstats` reports inbound packet queue delay as average, p50, p95, p99, and maximum latency. It also separates packets handled by the idle pump from packets handled at the normal tick boundary. `mode vanilla` and `mode aerogel` switch between the two paths and reset the measurement window, allowing a controlled A/B comparison on the same running server. Resetting or changing the mode requires game-master permission.

Reload behavior:

- a replaced JAR is loaded from an immutable staged copy;
- a newly added JAR is discovered by `/plugins reload`;
- a removed JAR is unloaded by a full plugin reload;
- commands, events, tasks, GUI resources, and other owned registrations are released;
- normal class changes use a fresh plugin class loader;
- Mixin changes may require `/restart`.

Use `/restart` for Minecraft-version changes, loader updates, Mixin structural changes, native libraries, or any situation where old JVM-global state may remain.

## Failure isolation

Aerogel distinguishes plugin failures by phase:

| Failure | Result |
|---|---|
| Metadata, dependency, or discovery error | Loading/reload is rejected with a diagnostic |
| Entrypoint constructor or `onLoad` error | That plugin is disabled; server startup continues |
| Missing loaded dependency | Dependent plugin is disabled |
| Event, command, suggestion, scheduled-task, or dialog callback error | Error is logged; plugin remains enabled |
| `onUnload` cleanup error | Warning is logged; remaining cleanup continues |
| Mixin prepare/apply failure | Can stop startup because it occurs before normal plugin callbacks |
| `VirtualMachineError` indicating JVM-level failure | Rethrown because continuing may be unsafe |

Failure isolation is not a security sandbox. Plugins run with the server process's filesystem, network, reflection, and JVM permissions. Install only trusted plugins.

## Troubleshooting

### Minecraft imports are red in the IDE

1. Confirm the project uses JDK 25.
2. Run `setupAerogelDevelopment`.
3. Refresh or re-import the Gradle project.
4. Check that `dev.aerogel.plugin` is applied.
5. Inspect the Gradle task output for download or hash-verification errors.

Do not solve this by adding an arbitrary Minecraft server JAR as `implementation`.

### `Minecraft server is not ready yet`

The plugin called `context.minecraft()` before Aerogel had a live server. Register commands and listeners in `onLoad`, then move live-server work to `ServerStartedEvent`, a command callback, or a later synchronous task.

### `NoClassDefFoundError` for an Aerogel API class

Common causes are:

- plugin and server were built against different Aerogel API revisions;
- the plugin JAR was incompletely copied or externally corrupted;
- a third-party dependency was not shaded;
- stale files remained after a failed build or reload.

Rebuild the plugin, verify the JAR, update Aerogel and the Gradle plugin together, and perform a full restart after API shape changes.

### Plugin appears as disabled

Check the first plugin-specific error in the console. Later linkage errors are often consequences of the original constructor, `onLoad`, listener scanning, metadata, or dependency failure.

### Reload does not apply a change

Normal Java class changes should load through a fresh class loader. If they do not:

- confirm the correct JAR was rebuilt and copied;
- avoid static references from global threads or registries;
- stop plugin-created executors in `onUnload`;
- reload dependents together after shared-API changes;
- use `/restart` for Mixin structural changes.

### Console text is corrupted

Compile and store resources as UTF-8. Aerogel configures plugin Java compilation as UTF-8. Avoid platform-default `FileReader`, `FileWriter`, or `new String(bytes)` calls; specify `StandardCharsets.UTF_8`.

### Server ticks stall

Look for blocking work in event listeners, command callbacks, synchronous scheduled tasks, and Mixin injections. Move external I/O to `scheduler().async(...)`, then enqueue only the final Minecraft mutation back to `scheduler().run(...)`.

## Release checklist

Before publishing a plugin:

- [ ] Build and test with JDK 25 and the target Minecraft version.
- [ ] Run `clean build` and confirm `validateAerogelPluginJar` passes.
- [ ] Confirm the plugin ID and translation resource path match exactly.
- [ ] Keep Aerogel, Minecraft, Mixin, and Brigadier classes out of the plugin JAR.
- [ ] Include licenses and notices for shaded libraries.
- [ ] Test a clean first load, `/plugins reload <id>`, `/plugins reload`, and `/restart` when Mixins are present.
- [ ] Verify commands and client suggestions after reload.
- [ ] Test cancellation at the correct event stage.
- [ ] Test with missing and malformed configuration.
- [ ] Confirm `onUnload` stops plugin-owned threads and releases external resources.
- [ ] Avoid retaining live player, world, entity, menu, or registry objects across unload or restart.
- [ ] Await or explicitly handle managed-storage load failures before using stored state.
- [ ] Document supported Aerogel and Minecraft versions for users.

## Related documentation

- [API overview](API.md)
- [Event catalog and contracts](EVENTS.md)
- [Gradle plugin reference](GRADLE_PLUGIN.md)
- [Mixin guide](MIXINS.md)
- [Example plugin](../example-plugin)
