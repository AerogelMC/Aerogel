# Aerogel Events

Aerogel supports lambda listeners and automatically discovered annotated listeners. Both use the same synchronous event bus and are removed automatically before a plugin reload.

## Lambda listeners

Register a typed listener through the plugin context:

```java
context.events().listen(PlayerJoinEvent.class, event -> {
    ServerPlayer player = event.player();
    context.logger().info(player.getName().getString());
});
```

An optional priority and cancelled-event flag can be supplied:

```java
context.events().listen(
    SomeCancellableEvent.class,
    EventPriority.EARLY,
    true,
    event -> event.cancel()
);
```

## Annotated listeners

No registration call is required. Aerogel scans class metadata without initializing every class and loads only classes containing `@EventHandler` methods.

```java
public final class PlayerListener {
    private final PluginContext context;

    public PlayerListener(PluginContext context) {
        this.context = context;
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        ServerPlayer player = event.player();
        context.logger().info(player.getName().getString());
    }
}
```

Listener classes may use a constructor accepting `PluginContext` or a no-argument constructor. Static handler methods do not require an instance. A handler must return `void` and accept exactly one `AerogelEvent` subtype.

## Priority

Listeners run in this order:

1. `EARLY`
2. `NORMAL`
3. `LATE`
4. `MONITOR`

Registration order is retained inside the same priority. A `MONITOR` listener observes final state and cannot change cancellation state.

## Built-in events

Minecraft 26.2 currently provides these concrete events:

- Server: `ServerStartingEvent`, `ServerStartedEvent`, `ServerTickStartEvent`, `ServerTickEndEvent`, `ServerSaveStartEvent`, `ServerSaveEndEvent`, `ServerStoppingEvent`, `ServerStoppedEvent`
- Commands: `CommandRegistrationEvent`, `CommandExecuteEvent`
- Players: `PlayerJoinEvent`, `PlayerQuitEvent`, `PlayerRespawnEvent`, `PlayerDeathEvent`, `PlayerTeleportEvent`, `PlayerGameModeChangeEvent`, `PlayerChatEvent`, `PlayerActionEvent`, `PlayerUseItemEvent`, `PlayerUseItemOnBlockEvent`
- Blocks: `BlockBreakEvent`, `BlockPlaceEvent`
- Entities: `EntitySpawnEvent`, `EntityRemoveEvent`, `EntityDamageEvent`, `EntityHealEvent`, `EntityEffectAddEvent`, `EntityDeathEvent`
- Items and inventories: `PlayerDropItemEvent`, `PlayerPickupItemEvent`, `InventoryOpenEvent`, `InventoryCloseEvent`, `InventoryClickEvent`
- Worlds: `WorldLoadEvent`, `WorldUnloadEvent`, `ExplosionEvent`

Events expose the live vanilla object rather than an Aerogel wrapper. Assign the generic accessor to the corresponding Minecraft type:

```java
MinecraftServer server = event.server();
ServerPlayer player = event.player();
```

Events run on the thread of the underlying vanilla operation. Tick, server lifecycle, command registration, join, and quit events therefore run synchronously and must not perform blocking work.

`PlayerChatEvent`, `PlayerActionEvent`, `PlayerUseItemEvent`, `PlayerUseItemOnBlockEvent`, and `InventoryClickEvent` run before their serverbound packet is handled. Cancelling one skips vanilla packet handling. The packet remains available through `event.packet()` so a plugin can inspect every field without waiting for an Aerogel wrapper API.
