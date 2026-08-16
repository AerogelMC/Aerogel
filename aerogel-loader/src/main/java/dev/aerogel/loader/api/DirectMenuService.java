package dev.aerogel.loader.api;

import dev.aerogel.api.event.EventBus;
import dev.aerogel.api.event.inventory.InventoryClickEvent;
import dev.aerogel.api.event.inventory.InventoryCloseEvent;
import dev.aerogel.api.inventory.Inventory;
import dev.aerogel.api.inventory.InventoryView;
import dev.aerogel.api.menu.Menu;
import dev.aerogel.api.menu.MenuClick;
import dev.aerogel.api.menu.MenuService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class DirectMenuService implements MenuService {
    private final PluginApiScope scope;
    private final DirectInventoryService inventories;
    private final Map<ServerPlayer, OpenMenu> open = new ConcurrentHashMap<>();
    private boolean bound;

    DirectMenuService(PluginApiScope scope, DirectInventoryService inventories) {
        this.scope = scope;
        this.inventories = inventories;
    }

    void bind(EventBus events) {
        if (bound) throw new IllegalStateException("Menu service is already bound to an event bus");
        bound = true;
        events.listen(InventoryClickEvent.class, this::click);
        events.listen(InventoryCloseEvent.class, event -> open.remove(event.player()));
    }

    @Override public Menu create(int rows, Component title) {
        return scope.own(new MenuImpl(inventories.create(rows, title)));
    }

    private void click(InventoryClickEvent event) {
        OpenMenu opened = open.get(event.player());
        if (opened == null || event.player().containerMenu != opened.view().menu()) return;
        opened.menu().click(event);
    }

    private final class MenuImpl implements Menu {
        private final Inventory inventory;
        private final Map<Integer, Consumer<MenuClick>> handlers = new ConcurrentHashMap<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private volatile Consumer<MenuClick> anyHandler = ignored -> { };
        private volatile boolean allowPlayerInventory;

        private MenuImpl(Inventory inventory) { this.inventory = inventory; }
        @Override public int size() { return inventory.size(); }
        @Override public Menu item(int slot, ItemStack item) { check(); inventory.item(slot, Objects.requireNonNull(item)); refresh(); return this; }
        @Override public ItemStack item(int slot) { check(); return inventory.item(slot); }
        @Override public Menu onClick(int slot, Consumer<MenuClick> handler) {
            checkSlot(slot); handlers.put(slot, Objects.requireNonNull(handler)); return this;
        }
        @Override public Menu onAnyClick(Consumer<MenuClick> handler) { anyHandler = Objects.requireNonNull(handler); return this; }
        @Override public Menu allowPlayerInventory(boolean allow) { allowPlayerInventory = allow; return this; }
        @Override public InventoryView open(ServerPlayer player) {
            check();
            InventoryView view = inventory.open(Objects.requireNonNull(player));
            OpenMenu previous = open.put(player, new OpenMenu(this, view));
            if (previous != null && previous.view().active()) previous.view().close();
            return view;
        }
        @Override public void refresh() {
            for (OpenMenu value : open.values()) {
                if (value.menu() == this && value.view().active()) value.view().menu().sendAllDataToRemote();
            }
        }
        @Override public Collection<ServerPlayer> viewers() { return inventory.viewers(); }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            open.entrySet().removeIf(entry -> {
                if (entry.getValue().menu() != this) return false;
                entry.getValue().view().close();
                return true;
            });
            inventory.close();
        }

        private void click(InventoryClickEvent event) {
            int slot = event.slot();
            boolean menuSlot = slot >= 0 && slot < size();
            if (menuSlot || !allowPlayerInventory) event.setCancelled(true);
            MenuClick click = new MenuClick(event.player(), this, slot, event.button(), event.input());
            if (menuSlot) {
                Consumer<MenuClick> handler = handlers.get(slot);
                if (handler != null) handler.accept(click);
            }
            anyHandler.accept(click);
            if (event.isCancelled()) event.player().containerMenu.sendAllDataToRemote();
        }
        private void check() { if (!active()) throw new IllegalStateException("Menu is closed"); }
        private void checkSlot(int slot) {
            check();
            if (slot < 0 || slot >= size()) throw new IndexOutOfBoundsException("slot " + slot + ", size " + size());
        }
    }

    private record OpenMenu(MenuImpl menu, InventoryView view) { }
}
