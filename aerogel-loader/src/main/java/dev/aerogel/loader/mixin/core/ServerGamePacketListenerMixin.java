package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.inventory.InventoryClickEvent;
import dev.aerogel.api.event.inventory.CreativeInventorySlotEvent;
import dev.aerogel.api.event.inventory.AnvilRenameEvent;
import dev.aerogel.api.event.inventory.InventoryButtonClickEvent;
import dev.aerogel.api.event.inventory.RecipePlaceEvent;
import dev.aerogel.api.event.inventory.TradeSelectEvent;
import dev.aerogel.api.event.player.PlayerAbilitiesChangeEvent;
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
import dev.aerogel.api.event.player.PlayerBundleSelectionEvent;
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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl")
abstract class ServerGamePacketListenerMixin {
    @Redirect(
        method = "removePlayerFromWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void aerogel$suppressRestartQuitMessage(PlayerList players, Component message, boolean overlay) {
        if (!RestartCoordinator.requested()) {
            players.broadcastSystemMessage(message, overlay);
        }
    }

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$move(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerMoveEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$clientCommand(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerClientCommandEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSpectatorAction(Lnet/minecraft/network/protocol/game/ServerboundSpectatorActionPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$spectatorAction(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerSpectatorActionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePaddleBoat(Lnet/minecraft/network/protocol/game/ServerboundPaddleBoatPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$paddleBoat(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerPaddleBoatEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleRecipeBookSeenRecipePacket", at = @At("HEAD"), cancellable = true)
    private void aerogel$recipeSeen(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerRecipeSeenEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleRecipeBookChangeSettingsPacket", at = @At("HEAD"), cancellable = true)
    private void aerogel$recipeSettings(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerRecipeBookSettingsEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSeenAdvancements", at = @At("HEAD"), cancellable = true)
    private void aerogel$advancements(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerAdvancementsScreenEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleBundleItemSelectedPacket", at = @At("HEAD"), cancellable = true)
    private void aerogel$bundleSelection(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerBundleSelectionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleCustomCommandSuggestions", at = @At("HEAD"), cancellable = true)
    private void aerogel$commandSuggestions(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerCommandSuggestionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlayerInput(Lnet/minecraft/network/protocol/game/ServerboundPlayerInputPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$input(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerInputEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$moveVehicle(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerVehicleMoveEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$interactEntity(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerInteractEntityEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleAttack(Lnet/minecraft/network/protocol/game/ServerboundAttackPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$attackEntity(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerAttackEntityEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$swing(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerSwingEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlayerCommand(Lnet/minecraft/network/protocol/game/ServerboundPlayerCommandPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$playerCommandAction(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerCommandActionEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlayerAbilities(Lnet/minecraft/network/protocol/game/ServerboundPlayerAbilitiesPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$abilities(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerAbilitiesChangeEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$hotbarSlot(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerHotbarSlotChangeEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleEditBook(Lnet/minecraft/network/protocol/game/ServerboundEditBookPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$editBook(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerEditBookEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSignUpdate(Lnet/minecraft/network/protocol/game/ServerboundSignUpdatePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$signUpdate(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerSignUpdateEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleClientInformation(Lnet/minecraft/network/protocol/common/ServerboundClientInformationPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$clientInformation(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerClientInformationEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSetCreativeModeSlot(Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$creativeSlot(@Coerce Object packet, CallbackInfo callbackInfo) {
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
        at = @At("HEAD"), cancellable = true)
    private void aerogel$useItem(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerUseItemEvent(
            EventHooks.cast(EventHooks.field(this, "player")), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleUseItemOn(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$useItemOn(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerUseItemOnBlockEvent(
            EventHooks.cast(EventHooks.field(this, "player")), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$action(@Coerce Object packet, CallbackInfo callbackInfo) {
        Object player = EventHooks.field(this, "player");
        if ("SWAP_ITEM_WITH_OFFHAND".equals(String.valueOf(EventHooks.call(packet, "getAction")))) {
            PlayerSwapHandItemsEvent swapEvent = new PlayerSwapHandItemsEvent(
                EventHooks.cast(player), EventHooks.cast(EventHooks.call(player, "getMainHandItem")),
                EventHooks.cast(EventHooks.call(player, "getOffhandItem")));
            EventHooks.post(swapEvent);
            if (swapEvent.isCancelled()) {
                callbackInfo.cancel();
                return;
            }
        }
        post(new PlayerActionEvent(EventHooks.cast(player), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$inventoryClick(@Coerce Object packet, CallbackInfo callbackInfo) {
        Object player = EventHooks.field(this, "player");
        InventoryClickEvent event = new InventoryClickEvent(
            EventHooks.cast(player), EventHooks.cast(packet),
            ((Number) EventHooks.call(packet, "containerId")).intValue(),
            ((Number) EventHooks.call(packet, "stateId")).intValue(),
            ((Number) EventHooks.call(packet, "slotNum")).intValue(),
            ((Number) EventHooks.call(packet, "buttonNum")).intValue(),
            EventHooks.cast(EventHooks.call(packet, "containerInput")));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
            EventHooks.call(EventHooks.field(player, "containerMenu"), "sendAllDataToRemote");
        }
    }

    @Inject(method = "handleContainerButtonClick(Lnet/minecraft/network/protocol/game/ServerboundContainerButtonClickPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$inventoryButton(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new InventoryButtonClickEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handlePlaceRecipe(Lnet/minecraft/network/protocol/game/ServerboundPlaceRecipePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$placeRecipe(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new RecipePlaceEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleSelectTrade(Lnet/minecraft/network/protocol/game/ServerboundSelectTradePacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$selectTrade(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new TradeSelectEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    @Inject(method = "handleRenameItem(Lnet/minecraft/network/protocol/game/ServerboundRenameItemPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$renameItem(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new AnvilRenameEvent(player(), EventHooks.cast(packet)), callbackInfo);
    }

    private static void post(PlayerPacketEvent event, CallbackInfo callbackInfo) {
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    private ServerPlayer player() {
        return EventHooks.cast(EventHooks.field(this, "player"));
    }
}
