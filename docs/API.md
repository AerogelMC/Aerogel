# Aerogel API

Aerogel extends the official Minecraft 26.2 server classes where vanilla development is repetitive. Once you have a `MinecraftServer`, `ServerLevel`, `ServerPlayer`, or `Entity`, normal work continues directly on that object instead of passing through a second wrapper API.

`PluginContext` only holds plugin-owned resources whose lifetime Aerogel must manage:

```java
context.commands();
context.scheduler();
context.inventories();
context.scoreboards();
context.bossBars();
context.dialogs();
context.translations();
context.storage();
```

Get the live vanilla server directly:

```java
MinecraftServer server = context.minecraft();
ServerLevel level = server.overworld();
SimpleContainer container = inventory.vanilla();
```

Calls which need a running server throw a clear `IllegalStateException` before `ServerStartedEvent`. Commands and scheduled tasks may be declared during `onLoad`; Aerogel activates them when the server is ready.

## Commands

Commands use the vanilla Brigadier tree directly. Nested literals, typed arguments,
requirements, tooltips, asynchronous suggestions, redirects, and Minecraft suggestion
providers therefore work without an Aerogel command model in between:

```java
context.commands().register(Commands.literal("game")
    .then(Commands.literal("start")
        .then(Commands.literal("confirmed")
            .executes(command -> {
                command.getSource().sendSuccess(
                    () -> Component.literal("Game started."), false);
                return 1;
            }))));
```

The returned registration is owned by the plugin and removed automatically on reload.

## Scheduler

Synchronous tasks run on server ticks. Async tasks run on Aerogel daemon workers and must return to a synchronous task before changing world state.

```java
context.scheduler().later(20, () -> context.logger().info("One second later"));
context.scheduler().repeat(0, 20, this::updateDisplay);
context.scheduler().async(this::loadExternalData);
```

## Managed file storage

Managed storage keeps a typed value in memory and persists it without performing file I/O on the
server thread. Writes to the same file are serialized, rapid changes are coalesced, and completed
files replace the previous version atomically.

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
    context.logger().info("Loaded round " + loaded.round())
));

data.update(previous -> new PluginData(previous.round() + 1, previous.scores()));
```

`set`, `update`, and `edit` mark the in-memory value dirty. Automatic saving waits 250 ms by
default, so a burst of updates normally becomes one write. `save()` is the user-facing alias of
`flush()`; both return a
`CompletableFuture` that completes once all changes visible at the call have reached disk. Dirty
files are flushed with a bounded wait when the plugin unloads.

Generic types use `TypeRef`:

```java
DataFile<Map<UUID, PlayerData>> players = context.storage().json(
    Path.of("players.json"),
    new TypeRef<Map<UUID, PlayerData>>() { },
    HashMap::new
);
```

Paths are resolved under `context.dataDirectory()` and may contain subdirectories, but cannot
escape that directory. JSON is UTF-8 and human-readable. Missing files use the supplied default;
malformed files fail the load and are not silently overwritten. A custom `DataCodec<T>` can be
used through `storage.open(...)` for binary or domain-specific formats.

Minecraft values use their vanilla 26.2 codecs instead of reflective Gson serialization. This
preserves an `ItemStack`'s item, count, complete data-component patch, custom data, names,
enchantments, container contents, profiles, and any other component accepted by the active
registry set:

```java
DataFile<ItemStack> reward = context.storage().itemStack(
    "reward.json",
    () -> ItemStack.EMPTY
);

DataFile<List<ItemStack>> inventory = context.storage().itemStacks(
    "inventory.json",
    List::of
);
```

`itemStacks` uses `ItemStack.OPTIONAL_CODEC`, including for each list element, so empty entries and
therefore inventory slot indices survive a round trip. Built-ins also exist for `Component`,
`CompoundTag`, `BlockState`, `DataComponentPatch`, `GlobalPos`, `BlockPos`, and `Identifier`.

Use `minecraftJson` when those values are fields inside a plugin record:

```java
record Kit(String name, Component title, List<ItemStack> slots, CompoundTag extra) { }

DataFile<List<Kit>> kits = context.storage().minecraftJson(
    "kits.json",
    new TypeRef<List<Kit>>() { },
    List::of
);
```

Use `codecJson` for any other vanilla or plugin-provided Mojang `Codec<T>`:

```java
DataFile<MyRule> rule = context.storage().codecJson(
    "rule.json",
    MyRule.CODEC,
    MyRule::defaults
);
```

Registry-aware files wait until the live server registry access exists before loading. Their
`load()` future therefore completes after server startup even if the file was opened in `onLoad`.
Aerogel encodes through `NbtOps` and projects the result into structured JSON. Normal strings,
integers, compounds, and lists stay ordinary JSON; values such as byte, short, long, float, and
double, plus typed NBT arrays, carry a small `$nbt` marker so their exact tag type survives text
parsing.

JSON restores the declared type rather than arbitrary runtime subtypes. Prefer records, immutable
state, UUIDs, resource keys, strings, numbers, lists, and maps. Only codec-backed Minecraft value
objects are persistable; do not persist live players, worlds, entities, menus, registries, packets,
or servers. Changes made directly to the object returned by `value()` cannot be detected; use
`set`, `update`, or `edit`.

## Inventories

```java
Inventory inventory = context.inventories().create(3, Component.literal("Tools"));
inventory.item(0, new ItemStack(Items.DIAMOND_PICKAXE));
inventory.open(player);
```

Inventories expose their live `Container`, track viewers, close open views on plugin reload, and work with the typed inventory events in `docs/EVENTS.md`.

## Players

Player lookup and broadcasts live on `MinecraftServer`; player operations live on `ServerPlayer`:

```java
MinecraftServer server = context.minecraft();
ServerPlayer player = server.findPlayer("Steve").orElseThrow();

player.sendSystemMessage(Component.literal("Hello"));
player.sendOverlayMessage(Component.literal("Ready"));
player.sendTitle(Component.literal("Game start"), Component.literal("Good luck"), 10, 60, 20);
player.setDisplayName(Component.literal("Host"));
player.setTabListName(Component.literal("[Admin] Host"));
player.setTabListHidden(true);
player.setNameTagHidden(true);
player.setTabListHeaderFooter(
    Component.literal("Aerogel"),
    Component.literal("Players: 10")
);
player.giveItem(new ItemStack(Items.DIAMOND));
player.sendPacket(packet);

server.broadcast(Component.literal("Round complete"));
server.broadcastPacket(packet);
```

`setDisplayName` changes the component returned by the player's vanilla `getDisplayName()` and
also synchronizes the overhead player name seen by vanilla clients. Chat, death, advancement, and
command messages which ask vanilla for the display name therefore follow the same value. The TAB
list follows it unless `setTabListName` supplies a TAB-only value. Use `clearDisplayName` or
`clearTabListName` to restore the corresponding vanilla behavior.

`setTabListHidden(true)` removes the player from the TAB list without disconnecting them or hiding
their entity. Pass `false` to show them again, and use `isTabListHidden()` to inspect the state.
`setNameTagHidden(true)` independently hides only the overhead name tag; pass `false` to restore it.

TAB headers and footers are viewer-specific. `setTabListHeader` and `setTabListFooter` preserve the
other half, while `clearTabListHeaderFooter` clears both. Aerogel synchronizes an arbitrary overhead
`Component` with viewer-local player-info and scoreboard-team packets. It does not spawn a display
entity or mutate the authenticated server profile or the server scoreboard.

`kick`, `clearTitle`, predicate-based `removeItems`, and `clearInventory` are also available directly. Existing vanilla methods remain available alongside them.

## Entities and items

Levels expose their live entities directly, and an entity can query its own surroundings:

```java
Collection<Entity> nearby = level.nearbyEntities(0, 64, 0, 16,
    entity -> entity instanceof LivingEntity);
Collection<Entity> aroundMob = mob.nearbyEntities(8);

level.findEntity(uniqueId).ifPresent(Entity::discard);
level.spawn(entity);
entity.teleport(destination, 0.5, 65, 0.5);
```

Item behavior stays on the owning vanilla object: use `ServerPlayer.giveItem`, `removeItems`, and the normal vanilla inventory and `ItemStack` APIs.

## Packets

Packets use the same direct objects:

```java
player.sendPacket(new ClientboundClearTitlesPacket(true));
context.minecraft().broadcastPacket(packet);
```

## Scoreboards

```java
Scoreboard board = context.scoreboards().main();
Objective objective = board.objective("coins", Component.literal("Coins"))
    .display(DisplaySlot.SIDEBAR)
    .score(player.getScoreboardName(), 10);

board.team("builders")
    .prefix(Component.literal("[Build] "))
    .add(player.getScoreboardName());
```

Objectives and teams created by a plugin are removed automatically on reload. Existing vanilla objectives and teams can be found and wrapped without taking ownership.

## Boss bars

```java
BossBar bar = context.bossBars().create(Component.literal("Raid"),
    BossBarColor.RED, BossBarOverlay.NOTCHED_10)
    .progress(0.5f)
    .add(player);
```

Viewer membership, visibility, progress, color, overlay, music, fog, and screen darkening are supported.

## Dialogs

```java
Dialog dialog = context.dialogs().confirmation(
    Component.literal("Continue?"),
    List.of(Component.literal("This action changes the world.")),
    Component.literal("Yes"), Component.literal("No"),
    result -> confirm(result.player()),
    result -> cancel(result.player())
);
dialog.show(player);
```

Notice and confirmation dialogs have high-level builders. `nativeDialog` accepts any vanilla `Dialog` or `Holder<Dialog>`, so every 26.2 input, body, action, and registry-backed dialog remains usable without waiting for an Aerogel wrapper.

## Worlds

```java
MinecraftServer server = context.minecraft();
ServerLevel world = server.overworld();
ServerLevel arena = context.worlds().createFlat("arena");
ServerLevel empty = context.worlds().createVoid("empty");
ServerLevel nether = context.worlds().createVanilla(
    "nether_arena", seed, VanillaDimension.NETHER
);
ServerLevel islands = context.worlds().create(
    "islands", seed, new IslandChunkGenerator(biomeSource)
);

world.setDayTime(6000);
world.clearWeather(20 * 60);
world.block(0, 64, 0, Blocks.STONE.defaultBlockState(), 3);
world.teleport(player, 0.5, 65, 0.5);
```

`createFlat("arena")` uses the plugin-local id `<plugin-id>:arena` and the server seed. Use `createFlat(id, seed, settings)` with a vanilla `FlatLevelGeneratorSettings` for the same layers, biome, structures, lakes, and decoration controls used by Minecraft superflat generation. `createVoid` creates a completely empty overworld-type level and does not add a spawn platform. `createVanilla` clones the complete built-in overworld, Nether, or End stem, including the correct dimension type. `create(id, generator)` and its seed/dimension overloads accept a plugin-defined vanilla `ChunkGenerator` directly, without reducing it to an Aerogel callback model. Repeating a call returns the loaded world when its generator and dimension types match and fails on a type collision. The returned `ServerLevel` remains server-owned and must not be closed by the plugin. Call world creation from the server thread after the server becomes ready, normally from `ServerStartedEvent` or a synchronous scheduled task. The Minecraft server is not attached yet during the initial `onLoad` callback.

The dimension folder is saved by vanilla, while the runtime dimension registration is recreated by the plugin on every server start. Reloading or unloading the plugin does not unload the world. Recreate the same generator and call `create` again on each full server start. Generation may execute away from the server thread, so a custom generator must be thread-safe and must derive repeatable output from its seed and coordinates instead of reading mutable live-world state. `MinecraftServer.loadedLevels`, `ServerLevel.identifier`, entity lookup, radius queries, block access, spawning, `rain`, and `thunder` are also provided. Registries, recipes, particles, sounds, and data components continue to use vanilla APIs directly.

`MinecraftServer.restart()` requests Aerogel's full-process restart and returns whether the request was accepted.

## Components

Aerogel uses Minecraft's `Component` directly. Use `Component.literal`,
`Component.translatable`, styling, click events, and hover events exactly as you would in
vanilla server code.
