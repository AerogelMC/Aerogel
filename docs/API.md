# Aerogel API

Aerogel adds convenience where vanilla server development is repetitive, while keeping the official Minecraft 26.2 classes available. API resources belong to the plugin that created them and are closed automatically when that plugin reloads.

Every service is available from `PluginContext`:

```java
context.commands();
context.scheduler();
context.inventories();
context.players();
context.scoreboards();
context.bossBars();
context.dialogs();
context.worlds();
```

The live server and every wrapped object remain accessible:

```java
MinecraftServer server = context.server().vanilla();
ServerLevel level = context.worlds().overworld().vanilla();
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

`context.players()` returns live `ServerPlayer` objects by name or UUID, lists online players, broadcasts components, and sends chat or action-bar messages. Player wrappers are intentionally avoided because vanilla already exposes the complete player surface.

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
World world = context.worlds().overworld();
world.dayTime(6000)
    .weather(Weather.CLEAR, 20 * 60);
world.block(0, 64, 0, Blocks.STONE.defaultBlockState(), 3);
world.teleport(player, new Position(0.5, 65, 0.5));
```

Loaded-world lookup, time, weather, block access, entity spawning, and player teleporting are provided. Advanced dimension creation, chunk generation, registries, recipes, particles, sounds, packets, and data components use the live vanilla server/level handles directly; Aerogel does not hide or duplicate those APIs.

## Components

Aerogel uses Minecraft's `Component` directly. Use `Component.literal`,
`Component.translatable`, styling, click events, and hover events exactly as you would in
vanilla server code.
