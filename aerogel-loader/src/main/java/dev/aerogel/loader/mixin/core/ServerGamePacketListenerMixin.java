package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.inventory.InventoryClickEvent;
import dev.aerogel.api.event.inventory.CreativeInventorySlotEvent;
import dev.aerogel.api.event.inventory.AnvilRenameEvent;
import dev.aerogel.api.event.inventory.InventoryButtonClickEvent;
import dev.aerogel.api.event.inventory.RecipePlaceEvent;
import dev.aerogel.api.event.inventory.TradeSelectEvent;
import dev.aerogel.api.event.player.PlayerAbilitiesChangeEvent;
import dev.aerogel.api.event.player.PlayerFlightChangeEvent;
import dev.aerogel.api.event.player.PlayerAdvancementsScreenEvent;
import dev.aerogel.api.event.player.PlayerActionEvent;
import dev.aerogel.api.event.player.PlayerAttackEntityEvent;
import dev.aerogel.api.event.player.ChatRender;
import dev.aerogel.api.event.player.PlayerChatEvent;
import dev.aerogel.api.event.player.PlayerClientCommandEvent;
import dev.aerogel.api.event.player.PlayerClientInformationEvent;
import dev.aerogel.api.event.player.PlayerCommandActionEvent;
import dev.aerogel.api.event.player.PlayerCommandSuggestionEvent;
import dev.aerogel.api.event.player.PlayerEditBookEvent;
import dev.aerogel.api.event.player.PlayerHotbarSlotChangeEvent;
import dev.aerogel.api.event.player.PlayerInputEvent;
import dev.aerogel.api.event.player.PlayerQuitEvent;
import dev.aerogel.api.event.player.PlayerBundleSelectionEvent;
import dev.aerogel.api.event.player.PlayerInteractEvent;
import dev.aerogel.api.event.player.PlayerPaddleBoatEvent;
import dev.aerogel.api.event.player.PlayerRecipeBookSettingsEvent;
import dev.aerogel.api.event.player.PlayerRecipeSeenEvent;
import dev.aerogel.api.event.player.PlayerSpectatorActionEvent;
import dev.aerogel.api.event.player.PlayerInteractEntityEvent;
import dev.aerogel.api.event.player.PlayerMoveEvent;
import dev.aerogel.api.event.player.PlayerPacketEvent;
import dev.aerogel.api.event.player.PlayerSignUpdateEvent;
import dev.aerogel.api.event.player.PlayerSwapHandItemsEvent;
import dev.aerogel.api.event.player.PlayerSwingEvent;
import dev.aerogel.api.event.player.PlayerUseItemEvent;
import dev.aerogel.api.event.player.PlayerUseItemOnBlockEvent;
import dev.aerogel.api.event.player.PlayerVehicleMoveEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.plugin.PluginFailures;
import dev.aerogel.loader.restart.RestartCoordinator;
import dev.aerogel.loader.internal.RestartGameListenerBridge;
import dev.aerogel.loader.internal.RespawnGameListenerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl")
abstract class ServerGamePacketListenerMixin
    implements RestartGameListenerBridge, RespawnGameListenerBridge {
    @Shadow public ServerPlayer player;

    @Override
    @Invoker("removePlayerFromWorld")
    public abstract void aerogel$removePlayerFromWorld();

    @Override
    @Invoker("restartClientLoadTimerAfterRespawn")
    public abstract void aerogel$restartClientLoadTimerAfterRespawn();
    @Unique private boolean aerogel$hasSuppressedSwing;
    @Unique private InteractionHand aerogel$suppressedSwingHand;

    @Redirect(
        method = "removePlayerFromWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void aerogel$handleQuitAnnouncement(PlayerList players, Component message, boolean overlay) {
        if (RestartCoordinator.requested()) return;

        if (EventHooks.hasListeners(PlayerQuitEvent.class)) {
            int previousPlayerCount = players.getPlayers().size();
            PlayerQuitEvent event = new PlayerQuitEvent(
                player,
                message,
                previousPlayerCount,
                Math.max(0, previousPlayerCount - 1)
            );
            EventHooks.post(event);
            if (event.isCancelled()) return;
            message = event.message();
        }

        players.broadcastSystemMessage(message, overlay);
    }

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$move(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerMoveEvent.class))
            post(new PlayerMoveEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$clientCommand(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerClientCommandEvent.class))
            post(new PlayerClientCommandEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSpectatorAction(Lnet/minecraft/network/protocol/game/ServerboundSpectatorActionPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$spectatorAction(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerSpectatorActionEvent.class))
            post(new PlayerSpectatorActionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePaddleBoat(Lnet/minecraft/network/protocol/game/ServerboundPaddleBoatPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$paddleBoat(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerPaddleBoatEvent.class))
            post(new PlayerPaddleBoatEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleRecipeBookSeenRecipePacket", at = @At("HEAD"), cancellable = true)
    private void aerogel$recipeSeen(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerRecipeSeenEvent.class))
            post(new PlayerRecipeSeenEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleRecipeBookChangeSettingsPacket", at = @At("HEAD"), cancellable = true)
    private void aerogel$recipeSettings(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerRecipeBookSettingsEvent.class))
            post(new PlayerRecipeBookSettingsEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSeenAdvancements", at = @At("HEAD"), cancellable = true)
    private void aerogel$advancements(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerAdvancementsScreenEvent.class))
            post(new PlayerAdvancementsScreenEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleBundleItemSelectedPacket", at = @At("HEAD"), cancellable = true)
    private void aerogel$bundleSelection(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerBundleSelectionEvent.class))
            post(new PlayerBundleSelectionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleCustomCommandSuggestions", at = @At("HEAD"), cancellable = true)
    private void aerogel$commandSuggestions(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerCommandSuggestionEvent.class))
            post(new PlayerCommandSuggestionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlayerInput(Lnet/minecraft/network/protocol/game/ServerboundPlayerInputPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$input(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerInputEvent.class))
            post(new PlayerInputEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$moveVehicle(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerVehicleMoveEvent.class))
            post(new PlayerVehicleMoveEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$interactEntity(ServerboundInteractPacket packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
        boolean listenPacket = EventHooks.hasListeners(PlayerInteractEntityEvent.class);
        if (!listenInteraction && !listenPacket) return;
        InteractionHand hand = packet.hand();
        aerogel$suppressNextSwing(hand);

        boolean cancelled = false;
        Entity entity = aerogel$entity(packet.entityId());
        if (listenInteraction && entity != null) {
            PlayerInteractEvent interaction = PlayerInteractEvent.entity(
                player, PlayerInteractEvent.Action.RIGHT_CLICK, hand, entity,
                packet.location(), packet.usingSecondaryAction());
            EventHooks.post(interaction);
            cancelled = interaction.isCancelled();
        }

        if (listenPacket) {
            PlayerInteractEntityEvent packetEvent = new PlayerInteractEntityEvent(
                player, packet);
            EventHooks.post(packetEvent);
            cancelled |= packetEvent.isCancelled();
        }
        if (cancelled) callbackInfo.cancel();
    }

    @Inject(method = "handleAttack(Lnet/minecraft/network/protocol/game/ServerboundAttackPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$attackEntity(ServerboundAttackPacket packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
        boolean listenPacket = EventHooks.hasListeners(PlayerAttackEntityEvent.class);
        if (!listenInteraction && !listenPacket) return;
        aerogel$suppressNextSwing(InteractionHand.MAIN_HAND);

        boolean cancelled = false;
        Entity entity = aerogel$entity(packet.entityId());
        if (listenInteraction && entity != null) {
            PlayerInteractEvent interaction = PlayerInteractEvent.entity(
                player, PlayerInteractEvent.Action.LEFT_CLICK,
                InteractionHand.MAIN_HAND, entity, null, false);
            EventHooks.post(interaction);
            cancelled = interaction.isCancelled();
        }

        if (listenPacket) {
            PlayerAttackEntityEvent packetEvent = new PlayerAttackEntityEvent(
                player, packet);
            EventHooks.post(packetEvent);
            cancelled |= packetEvent.isCancelled();
        }
        if (cancelled) callbackInfo.cancel();
    }

    @Inject(method = "handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$swing(ServerboundSwingPacket packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        boolean listenSwing = EventHooks.hasListeners(PlayerSwingEvent.class);
        boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
        if (!listenSwing && !listenInteraction) return;
        InteractionHand hand = packet.getHand();
        boolean cancelled = false;
        if (listenSwing) {
            PlayerSwingEvent swing = new PlayerSwingEvent(
                player, packet);
            EventHooks.post(swing);
            cancelled = swing.isCancelled();
        }
        boolean suppressed = aerogel$consumeSuppressedSwing(hand)
            || aerogel$isBlockTargeted();
        if (listenInteraction && !suppressed) {
            PlayerInteractEvent interaction = PlayerInteractEvent.air(
                player, PlayerInteractEvent.Action.LEFT_CLICK, hand);
            EventHooks.post(interaction);
            cancelled |= interaction.isCancelled();
        }
        if (cancelled) callbackInfo.cancel();
    }

    @Inject(method = "handlePlayerCommand(Lnet/minecraft/network/protocol/game/ServerboundPlayerCommandPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$playerCommandAction(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerCommandActionEvent.class))
            post(new PlayerCommandActionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlayerAbilities(Lnet/minecraft/network/protocol/game/ServerboundPlayerAbilitiesPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$abilities(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerAbilitiesChangeEvent.class))
            post(new PlayerAbilitiesChangeEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(
        method = "handlePlayerAbilities("
            + "Lnet/minecraft/network/protocol/game/ServerboundPlayerAbilitiesPacket;)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z",
            opcode = Opcodes.PUTFIELD
        ),
        cancellable = true
    )
    private void aerogel$flightChanged(ServerboundPlayerAbilitiesPacket packet, CallbackInfo callbackInfo) {
        if (!EventHooks.hasListeners(PlayerFlightChangeEvent.class)) return;
        Abilities abilities = player.getAbilities();
        boolean previous = abilities.flying;
        boolean requested = packet.isFlying() && abilities.mayfly;
        if (previous == requested) return;

        PlayerFlightChangeEvent event = new PlayerFlightChangeEvent(
            player, previous, requested);
        EventHooks.post(event);
        if (event.isCancelled()) {
            player.onUpdateAbilities();
            callbackInfo.cancel();
        } else if (event.flying() != requested) {
            abilities.flying = event.flying();
            player.onUpdateAbilities();
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$hotbarSlot(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerHotbarSlotChangeEvent.class))
            post(new PlayerHotbarSlotChangeEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleEditBook(Lnet/minecraft/network/protocol/game/ServerboundEditBookPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$editBook(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerEditBookEvent.class))
            post(new PlayerEditBookEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSignUpdate(Lnet/minecraft/network/protocol/game/ServerboundSignUpdatePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$signUpdate(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerSignUpdateEvent.class))
            post(new PlayerSignUpdateEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleClientInformation(Lnet/minecraft/network/protocol/common/ServerboundClientInformationPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$clientInformation(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(PlayerClientInformationEvent.class))
            post(new PlayerClientInformationEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSetCreativeModeSlot(Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$creativeSlot(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(CreativeInventorySlotEvent.class))
            post(new CreativeInventorySlotEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Redirect(
        method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"
        )
    )
    private void aerogel$chat(
        PlayerList playerList, PlayerChatMessage signedMessage,
        ServerPlayer player, ChatType.Bound chatType
    ) {
        if (!EventHooks.hasListeners(PlayerChatEvent.class)) {
            playerList.broadcastChatMessage(signedMessage, player, chatType);
            return;
        }
        PlayerChatEvent event = new PlayerChatEvent(player, signedMessage);
        EventHooks.post(event);
        if (event.isCancelled()) return;
        Component content = event.message();
        ChatType.Bound outgoingType = chatType;
        if (event.renderer().isPresent()) {
            try {
                ChatRender render = event.renderer().orElseThrow().render(player, content);
                content = render.message();
                List<ChatTypeDecoration.Parameter> parameters = List.of(
                    ChatTypeDecoration.Parameter.SENDER,
                    ChatTypeDecoration.Parameter.CONTENT,
                    ChatTypeDecoration.Parameter.TARGET
                );
                ChatTypeDecoration display = new ChatTypeDecoration(
                    "%1$s%2$s%3$s", parameters, net.minecraft.network.chat.Style.EMPTY);
                ChatTypeDecoration narration = new ChatTypeDecoration(
                    "%1$s%2$s%3$s", parameters, net.minecraft.network.chat.Style.EMPTY);
                outgoingType = new ChatType.Bound(
                    Holder.direct(new ChatType(display, narration)),
                    aerogel$join(render.prefix()), Optional.of(aerogel$join(render.suffix())));
            } catch (Throwable failure) {
                PluginFailures.rethrowFatal(failure);
                Logger.getLogger("Aerogel").log(Level.SEVERE,
                    "Plugin chat renderer failed; using the vanilla chat format", failure);
            }
        }
        PlayerChatMessage outgoing = event.isModified() || content != signedMessage.decoratedContent()
            ? signedMessage.withUnsignedContent(content)
            : signedMessage;
        playerList.broadcastChatMessage(outgoing, player, outgoingType);
    }

    private static Component aerogel$join(List<Component> components) {
        MutableComponent result = Component.empty();
        for (Component component : components) {
            result.append(component);
        }
        return result;
    }

    @Inject(method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;ackBlockChangesUpTo(I)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void aerogel$useItem(ServerboundUseItemPacket packet, CallbackInfo callbackInfo) {
        boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
        boolean listenPacket = EventHooks.hasListeners(PlayerUseItemEvent.class);
        if (!listenInteraction && !listenPacket) return;
        InteractionHand hand = packet.getHand();
        aerogel$suppressNextSwing(hand);

        boolean cancelled = false;
        if (listenInteraction) {
            PlayerInteractEvent interaction = PlayerInteractEvent.air(
                player, PlayerInteractEvent.Action.RIGHT_CLICK, hand);
            EventHooks.post(interaction);
            cancelled = interaction.isCancelled();
        }
        if (listenPacket) {
            PlayerUseItemEvent packetEvent = new PlayerUseItemEvent(
                player, packet);
            EventHooks.post(packetEvent);
            cancelled |= packetEvent.isCancelled();
        }
        if (cancelled) {
            callbackInfo.cancel();
            player.containerMenu.sendAllDataToRemote();
        }
    }

    @Inject(method = "handleUseItemOn(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;ackBlockChangesUpTo(I)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void aerogel$useItemOn(ServerboundUseItemOnPacket packet, CallbackInfo callbackInfo) {
        boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
        boolean listenPacket = EventHooks.hasListeners(PlayerUseItemOnBlockEvent.class);
        if (!listenInteraction && !listenPacket) return;
        InteractionHand hand = packet.getHand();
        BlockHitResult hitResult = packet.getHitResult();
        BlockPos position = hitResult.getBlockPos();
        net.minecraft.core.Direction direction = hitResult.getDirection();
        aerogel$suppressNextSwing(hand);

        boolean cancelled = false;
        if (listenInteraction) {
            PlayerInteractEvent interaction = PlayerInteractEvent.block(
                player, PlayerInteractEvent.Action.RIGHT_CLICK,
                hand, position, direction, hitResult.getLocation());
            EventHooks.post(interaction);
            cancelled = interaction.isCancelled();
        }
        if (listenPacket) {
            PlayerUseItemOnBlockEvent packetEvent = new PlayerUseItemOnBlockEvent(
                player, packet);
            EventHooks.post(packetEvent);
            cancelled |= packetEvent.isCancelled();
        }
        if (cancelled) {
            callbackInfo.cancel();
            EventHooks.resyncBlock(player, player.level(), position);
            EventHooks.resyncBlock(player, player.level(), position.relative(direction));
            player.containerMenu.sendAllDataToRemote();
        }
    }

    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$action(ServerboundPlayerActionPacket packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        boolean listenInteraction = EventHooks.hasListeners(PlayerInteractEvent.class);
        boolean listenAction = EventHooks.hasListeners(PlayerActionEvent.class);
        boolean listenSwap = EventHooks.hasListeners(PlayerSwapHandItemsEvent.class);
        ServerboundPlayerActionPacket.Action action = packet.getAction();

        // DROP_ITEM and DROP_ALL_ITEMS are followed by a hand-animation packet. That animation
        // remains observable through PlayerSwingEvent, but must not become a semantic left-click.
        // Block breaking has its own semantic interaction event in ServerPlayerGameModeMixin.
        if (listenInteraction && (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
            || action == ServerboundPlayerActionPacket.Action.DROP_ITEM
            || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
            aerogel$suppressNextSwing(InteractionHand.MAIN_HAND);
        }

        if (!listenAction && !listenSwap) return;
        boolean cancelled = false;

        if (listenSwap && action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            PlayerSwapHandItemsEvent swapEvent = new PlayerSwapHandItemsEvent(
                player, player.getMainHandItem(), player.getOffhandItem());
            EventHooks.post(swapEvent);
            cancelled |= swapEvent.isCancelled();
        }

        if (listenAction) {
            PlayerActionEvent packetEvent = new PlayerActionEvent(
                player, packet);
            EventHooks.post(packetEvent);
            cancelled |= packetEvent.isCancelled();
        }
        if (cancelled) {
            callbackInfo.cancel();
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                EventHooks.resyncBlock(player, player.level(), packet.getPos());
            }
        }
    }

    @Inject(method = "handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$inventoryClick(ServerboundContainerClickPacket packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (!EventHooks.hasListeners(InventoryClickEvent.class)) return;
        InventoryClickEvent event = new InventoryClickEvent(
            player, packet, packet.containerId(), packet.stateId(),
            packet.slotNum(), packet.buttonNum(), packet.containerInput());
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
            player.containerMenu.sendAllDataToRemote();
        }
    }

    @Inject(method = "handleContainerButtonClick(Lnet/minecraft/network/protocol/game/ServerboundContainerButtonClickPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$inventoryButton(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(InventoryButtonClickEvent.class))
            post(new InventoryButtonClickEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlaceRecipe(Lnet/minecraft/network/protocol/game/ServerboundPlaceRecipePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$placeRecipe(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(RecipePlaceEvent.class))
            post(new RecipePlaceEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSelectTrade(Lnet/minecraft/network/protocol/game/ServerboundSelectTradePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$selectTrade(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(TradeSelectEvent.class))
            post(new TradeSelectEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleRenameItem(Lnet/minecraft/network/protocol/game/ServerboundRenameItemPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$renameItem(@Coerce Object packet, CallbackInfo callbackInfo) {
        if (!aerogel$serverThread()) return;
        if (EventHooks.hasListeners(AnvilRenameEvent.class))
            post(new AnvilRenameEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    private static void post(PlayerPacketEvent event, CallbackInfo callbackInfo) {
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Unique
    private Entity aerogel$entity(int entityId) {
        return player.level().getEntity(entityId);
    }

    @Unique
    private void aerogel$suppressNextSwing(InteractionHand hand) {
        aerogel$hasSuppressedSwing = true;
        aerogel$suppressedSwingHand = hand;
    }

    @Unique
    private boolean aerogel$consumeSuppressedSwing(InteractionHand hand) {
        if (!aerogel$hasSuppressedSwing
            || !java.util.Objects.equals(aerogel$suppressedSwingHand, hand)) {
            return false;
        }
        aerogel$hasSuppressedSwing = false;
        aerogel$suppressedSwingHand = null;
        return true;
    }

    @Unique
    private boolean aerogel$isBlockTargeted() {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        return hit.getType() == HitResult.Type.BLOCK;
    }

    @Unique
    private boolean aerogel$serverThread() {
        return player.level().getServer().isSameThread();
    }

    private ServerPlayer player() {
        return player;
    }
}
