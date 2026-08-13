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
player.giveItem(new ItemStack(Items.DIAMOND));
player.sendPacket(packet);

server.broadcast(Component.literal("Round complete"));
server.broadcastPacket(packet);
```

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

world.setDayTime(6000);
world.clearWeather(20 * 60);
world.block(0, 64, 0, Blocks.STONE.defaultBlockState(), 3);
world.teleport(player, 0.5, 65, 0.5);
```

`MinecraftServer.loadedLevels`, `ServerLevel.identifier`, entity lookup, radius queries, block access, spawning, `rain`, and `thunder` are provided. Advanced dimension creation, chunk generation, registries, recipes, particles, sounds, and data components continue to use vanilla APIs directly.

`MinecraftServer.restart()` requests Aerogel's full-process restart and returns whether the request was accepted.

## Components

Aerogel uses Minecraft's `Component` directly. Use `Component.literal`,
`Component.translatable`, styling, click events, and hover events exactly as you would in
vanilla server code.
