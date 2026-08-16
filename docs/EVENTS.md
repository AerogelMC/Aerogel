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

## Cancellation contract

Aerogel marks an event cancellable only when its hook can prevent the corresponding vanilla operation. Calling `cancel()` on these events stops that operation rather than merely marking the event:

- All `PlayerPacketEvent` subtypes, including movement, input, attacks, interactions, item use, inventory input, book/sign edits, client settings, resource-pack responses, custom payloads, recipe-book actions, spectator actions, and creative-slot changes
- Block intent and processing: `BlockBreakAttemptEvent`, `BlockMiningStartEvent`, `BlockMiningProgressEvent`, `BlockMiningStopEvent`, `BlockMiningAbortEvent`, `BlockBreakEvent`, `BlockPlaceEvent`, `BlockStateChangeEvent`, and `PistonMoveEvent`
- Entity changes: `EntitySpawnEvent`, `EntityDamageEvent`, `EntityHealEvent`, `EntityEffectAddEvent`, `EntityEffectRemoveEvent`, `EntityEquipmentChangeEvent`, `EntityMountEvent`, `EntityDismountEvent`, `EntityCombustEvent`, `EntityTargetEvent`, `EntityAirSupplyChangeEvent`, `EntityFreezeTicksChangeEvent`, `EntityPoseChangeEvent`, `EntityCustomNameChangeEvent`, `EntityVisibilityChangeEvent`, `EntityGravityChangeEvent`, `EntitySilentChangeEvent`, `EntityHealthChangeEvent`, `EntityAbsorptionChangeEvent`, `EntityKnockbackEvent`, `EntityJumpEvent`, `EntityRandomTeleportEvent`, `ProjectileLaunchEvent`, `ProjectileHitEvent`, `EntityTeleportEvent`, `EntityTameEvent`, and `EntityBreedEvent`
- Player and inventory operations: `PlayerLoginEvent`, `PlayerJoinEvent`, `PlayerQuitEvent`, `PlayerChatEvent`, `PlayerInteractEvent`, `PlayerGameModeChangeEvent`, `PlayerTeleportEvent`, `PlayerSneakChangeEvent`, `PlayerSprintChangeEvent`, `PlayerSwimChangeEvent`, `PlayerFlightChangeEvent`, `PlayerItemUseStartEvent`, `PlayerItemUseEndEvent`, `PlayerDropItemEvent`, `PlayerPickupItemEvent`, `PlayerBedEnterEvent`, `PlayerBedLeaveEvent`, `PlayerExperienceChangeEvent`, `PlayerFoodExhaustionEvent`, `PlayerItemConsumeEvent`, `InventoryOpenEvent`, `InventoryButtonClickEvent`, `RecipePlaceEvent`, `TradeSelectEvent`, and `AnvilRenameEvent`
- Server/world operations: `CommandExecuteEvent`, `ServerSaveStartEvent`, `ExplosionEvent`, `ChunkPreLoadEvent`, `RainChangeEvent`, and `ThunderChangeEvent`

Result and lifecycle notifications remain observation-only. Examples include `BlockBrokenEvent`, respawn, entity removal, inventory close, save completion, ticks, and world load/unload. At those hook points the vanilla result already exists, or cancelling it would leave server state inconsistent. Cancelling `PlayerJoinEvent` or `PlayerQuitEvent` is deliberately narrower: it suppresses the corresponding announcement without rejecting the connection or preventing disconnection. `EntityDeathEvent` is a deliberate exception: it is not cancellable, but Aerogel holds the calculated loot and experience until listeners have edited the result.

## Mutable outcomes

Pre-operation events expose setters when changing an argument still has clear vanilla semantics. Aerogel applies those values to the underlying operation rather than changing only the event object. This includes damage and healing amounts, effects, equipment, targets, mounts, teleports, projectile hits, breeding/taming participants, dropped items, experience changes, food exhaustion, block-state changes, explosion properties, and command text.

Death loot is calculated by vanilla first and spawned only after `EntityDeathEvent` returns. Its drop list is live and mutable:

```java
@EventHandler
private void onDeath(EntityDeathEvent event) {
    event.drops().removeIf(ItemStack::isEmpty);
    event.addDrop(new ItemStack(Blocks.DIAMOND_BLOCK));
    event.setDroppedExperience(25);
}
```

Use `clearDrops()`, `addDrop(ItemStack)`, `setDrops(Collection)`, or edit `drops()` directly. Aerogel snapshots the final list, copies every non-empty stack, and then lets each entity enter the level through the normal spawn event path. A listener may set experience to zero, but not to a negative value.

Mutable fields are not added to after-the-fact notifications merely for API symmetry. For example, changing `BlockBrokenEvent.state()` after removal could not restore the old block safely; use cancellable `BlockBreakEvent` or mutable `BlockStateChangeEvent` instead. Packet events expose the complete vanilla packet and cancellation because most serverbound packet records cannot be safely rewritten in place.

## Built-in events

Minecraft 26.2 currently provides these concrete events:

- Server: `ServerStartingEvent`, `ServerStartedEvent`, `ServerTickStartEvent`, `ServerTickEndEvent`, `ServerSaveStartEvent`, `ServerSaveEndEvent`, `ServerStoppingEvent`, `ServerStoppedEvent`
- Commands: `CommandRegistrationEvent`, `CommandExecuteEvent`
- Players: `PlayerLoginEvent`, `PlayerJoinEvent`, `PlayerQuitEvent`, `PlayerRespawnEvent`, `PlayerDeathEvent`, `PlayerTeleportEvent`, `PlayerGameModeChangeEvent`, `PlayerChatEvent`, `PlayerInteractEvent`, `PlayerActionEvent`, `PlayerMoveEvent`, `PlayerInputEvent`, `PlayerVehicleMoveEvent`, `PlayerInteractEntityEvent`, `PlayerAttackEntityEvent`, `PlayerSwingEvent`, `PlayerCommandActionEvent`, `PlayerClientCommandEvent`, `PlayerAbilitiesChangeEvent`, `PlayerSneakChangeEvent`, `PlayerSprintChangeEvent`, `PlayerSwimChangeEvent`, `PlayerFlightChangeEvent`, `PlayerItemUseStartEvent`, `PlayerItemUseEndEvent`, `PlayerHotbarSlotChangeEvent`, `PlayerEditBookEvent`, `PlayerSignUpdateEvent`, `PlayerClientInformationEvent`, `PlayerUseItemEvent`, `PlayerUseItemOnBlockEvent`, `PlayerSwapHandItemsEvent`, `PlayerSpectatorActionEvent`, `PlayerPaddleBoatEvent`, `PlayerRecipeSeenEvent`, `PlayerRecipeBookSettingsEvent`, `PlayerAdvancementsScreenEvent`, `PlayerBundleSelectionEvent`, `PlayerCommandSuggestionEvent`, `PlayerResourcePackStatusEvent`, `PlayerCustomPayloadEvent`, `PlayerCustomClickActionEvent`, `PlayerBedEnterEvent`, `PlayerBedLeaveEvent`, `PlayerExperienceChangeEvent`, `PlayerFoodExhaustionEvent`, `PlayerItemConsumeEvent`
- Blocks: `BlockBreakAttemptEvent`, `BlockMiningStartEvent`, `BlockMiningProgressEvent`, `BlockMiningStopEvent`, `BlockMiningAbortEvent`, `BlockBreakEvent`, `BlockBrokenEvent`, `BlockPlaceEvent`, `BlockStateChangeEvent`, `PistonMoveEvent`
- Entities: `EntitySpawnEvent`, `EntityRemoveEvent`, `EntityDamageEvent`, `EntityHealEvent`, `EntityEffectAddEvent`, `EntityEffectRemoveEvent`, `EntityEquipmentChangeEvent`, `EntityMountEvent`, `EntityDismountEvent`, `EntityCombustEvent`, `EntityDeathEvent`, `EntityTargetEvent`, `EntityAirSupplyChangeEvent`, `EntityFreezeTicksChangeEvent`, `EntityPoseChangeEvent`, `EntityCustomNameChangeEvent`, `EntityVisibilityChangeEvent`, `EntityGravityChangeEvent`, `EntitySilentChangeEvent`, `EntityHealthChangeEvent`, `EntityAbsorptionChangeEvent`, `EntityKnockbackEvent`, `EntityJumpEvent`, `EntityRandomTeleportEvent`, `ProjectileLaunchEvent`, `ProjectileHitEvent`, `EntityTeleportEvent`, `EntityTameEvent`, `EntityBreedEvent`
- Items and inventories: `PlayerDropItemEvent`, `PlayerPickupItemEvent`, `InventoryOpenEvent`, `InventoryCloseEvent`, `InventoryClickEvent`, `CreativeInventorySlotEvent`, `InventoryButtonClickEvent`, `RecipePlaceEvent`, `TradeSelectEvent`, `AnvilRenameEvent`
- Worlds: `WorldLoadEvent`, `WorldUnloadEvent`, `ChunkPreLoadEvent`, `ChunkLoadEvent`, `ChunkPreUnloadEvent`, `ChunkUnloadEvent`, `RainChangeEvent`, `ThunderChangeEvent`, `ExplosionEvent`

Events expose the live vanilla object rather than an Aerogel wrapper. Assign the generic accessor to the corresponding Minecraft type:

```java
MinecraftServer server = event.server();
ServerPlayer player = event.player();
```

Events run on the thread of the underlying vanilla operation. Tick, server lifecycle, command registration, join, and quit events therefore run synchronously and must not perform blocking work.

`PlayerInteractEvent` is the high-level click event. Its `action()` is `LEFT_CLICK` or
`RIGHT_CLICK`, while `target()` is `AIR`, `BLOCK`, or `ENTITY`. Block positions and faces,
entities, exact client interaction positions, and the used hand are exposed without requiring a
plugin to correlate raw packets. Left-clicked blocks follow Minecraft's block-action packets;
per-tick mining swing animations do not produce repeated `AIR` interactions. `PlayerSwingEvent`
intentionally remains a low-level animation packet event and can also occur for actions such as
dropping an item.

`PlayerPacketEvent` subtypes run before their serverbound packet is handled. Cancelling one skips vanilla packet handling. The typed packet remains available through `event.packet()`, so a plugin can inspect every vanilla field without waiting for an Aerogel wrapper API. High-frequency events such as `PlayerMoveEvent` and `PlayerInputEvent` should use small, non-blocking listeners.

`PlayerChatEvent` runs after signed-message validation and immediately before broadcast. Use
`event.setMessage(Component)` to replace the displayed component while retaining the original
`PlayerChatMessage` through `event.signedMessage()`. The same final component is sent to players
and rendered, including its colors, in the server console.

`InventoryClickEvent` also exposes `containerId()`, `stateId()`, `slot()`, `button()`, and the vanilla `ContainerInput` through `input()`.

World and entity events are hooked at the vanilla operation they describe instead of being inferred from a generic packet. For example, `EntityTargetEvent` covers AI target changes, `ProjectileLaunchEvent` covers a projectile entering a level, and weather events cover natural rain and thunder transitions. Packet-backed inventory events retain their exact vanilla packet through `event.packet()` so plugins can use every Minecraft 26.2 field.

### Item-use lifecycle

`PlayerItemUseStartEvent` runs after vanilla has accepted a non-empty item and confirmed that the player is not already using another item. It exposes the accepted hand and item and can cancel entry into active use. `PlayerItemUseEndEvent` reports the exact end path through `reason()`:

- `COMPLETED`: vanilla is about to call `ItemStack.finishUsingItem`.
- `RELEASED`: the player released an item whose behavior supports release.
- `INTERRUPTED`: vanilla stopped the use for another reason, such as an explicit state reset.

Cancelling an end event preserves the active-use state. When delaying `COMPLETED`, set a positive `remainingTicks` value as well; leaving it at zero intentionally lets vanilla attempt completion again on the next tick.

### Global block-state changes

`BlockStateChangeEvent` is the common pre-commit event for every state replacement routed through the vanilla level. It catches placement, removal, state-only replacement, fluid flow, explosions, piston movement, random and scheduled ticks, and entity-driven changes such as endermen taking or placing blocks. Cancelling it makes the underlying `setBlock` operation return `false`; changing `state`, `flags`, or `recursionLeft` changes the actual vanilla call.

Use `changeType()` for `PLACE`, `REMOVE`, or `REPLACE`, and use `reason()` for the initiating operation. Known operations supply all applicable origin data:

```java
@EventHandler
private void onBlockChange(BlockStateChangeEvent event) {
    if (event.reason() == BlockStateChangeEvent.Reason.ENTITY_ACTION) {
        event.sourceEntity().ifPresent(entity ->
            logger.info("{} changed {}", entity.getUUID(), event.position()));
    }
}
```

Reasons distinguish player placement, player breaking, other player interactions, entity actions, explosions, pistons, fluids, random ticks, scheduled ticks, and direct setters. `sourceEntity()` is present for player and entity actions and for explosions with an entity source. `sourcePosition()` identifies an originating block or tick position. `sourceLocation()` retains a precise actor or explosion origin. Direct calls to the vanilla setter have `DIRECT`; Aerogel does not guess a cause from stack traces. Nested changes inherit the current operation until a more specific nested reason overrides it.

The chunk lifecycle distinguishes requests from completed state changes:

- `ChunkPreLoadEvent` runs before vanilla starts the first load or generation task for the holder. It exposes the `ChunkPos` and requested `ChunkStatus` because no `LevelChunk` exists yet. Cancelling it returns vanilla's unloaded-chunk result and prevents that attempt from reading or generating the chunk.
- `ChunkLoadEvent` runs after the resulting full chunk starts ticking.
- `ChunkPreUnloadEvent` runs immediately before the chunk is detached.
- `ChunkUnloadEvent` runs after block entities and tick containers have been detached and is observation-only.

Cancelling `ChunkPreLoadEvent` is a hard load denial. Ordinary asynchronous and ticket-driven callers receive vanilla's normal unloaded result and may retry later, causing the event to fire again. A plugin must not deny mandatory infrastructure chunks such as initial spawn unless it also controls the requesting operation: synchronous vanilla code that explicitly requires a chunk is allowed to surface a load-failure exception rather than receiving a fake or partially initialized chunk.

### Block destruction lifecycle

Block events deliberately separate client intent, accepted mining, and actual destruction:

- `BlockBreakAttemptEvent` observes the raw start request before range, protection, game-mode, tool, or block restrictions are checked. It is cancellable.
- `BlockMiningStartEvent` runs only after vanilla accepts non-creative mining. It is cancellable.
- `BlockMiningProgressEvent` reports the server's accumulated progress and crack-animation stage. Cancelling it suppresses that progress calculation and clears the crack animation.
- `BlockMiningStopEvent` and `BlockMiningAbortEvent` report the corresponding client actions; neither means that the block was destroyed. Cancelling one prevents vanilla from applying that stop or abort action.
- `BlockBreakEvent` runs only after vanilla approves destruction, immediately before `playerWillDestroy` and block removal. Cancelling it leaves the block intact.
- `BlockBrokenEvent` runs only after `removeBlock` succeeds and exposes the state that existed before removal.

All of these events inherit `PlayerBlockEvent`, so one listener can observe the complete player-block lifecycle. A creative-mode sword, for example, can produce an attempt but cannot produce `BlockBreakEvent` or `BlockBrokenEvent` because vanilla rejects it before Aerogel's confirmed-break hook.
