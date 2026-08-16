package dev.aerogel.loader.api;

import dev.aerogel.api.inventory.Inventory;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.inventory.InventoryView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectInventoryService implements InventoryService {
    private final PluginApiScope scope;

    DirectInventoryService(PluginApiScope scope) { this.scope = scope; }

    @Override public Inventory create(int rows, Component title) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        Container container = new SimpleContainer(rows * 9);
        return scope.own(new InventoryImpl(container, title, rows));
    }

    @Override public Inventory wrap(Container container, Component title) {
        int size = container.getContainerSize();
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Wrapped chest container size must be 9, 18, 27, 36, 45, or 54");
        }
        return scope.own(new InventoryImpl(container, title, size / 9));
    }

    private final class InventoryImpl implements Inventory {
        private final Container container;
        private final Component title;
        private final int rows;
        private final Set<View> views = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean active = new AtomicBoolean(true);

        private InventoryImpl(Container container, Component title, int rows) {
            this.container = java.util.Objects.requireNonNull(container, "container");
            this.title = java.util.Objects.requireNonNull(title, "title");
            this.rows = rows;
        }

        @Override public int size() { return rows * 9; }
        @Override public Container vanilla() { return container; }
        @Override public ItemStack item(int slot) {
            checkSlot(slot); return container.getItem(slot);
        }
        @Override public void item(int slot, ItemStack itemStack) {
            checkActive(); checkSlot(slot); container.setItem(slot, itemStack);
        }
        @Override public void clear() { checkActive(); container.clearContent(); }

        @Override public InventoryView open(ServerPlayer player) {
            checkActive();
            MenuProvider provider = new MenuProvider() {
                @Override public Component getDisplayName() { return title; }
                @Override public AbstractContainerMenu createMenu(
                    int containerId, net.minecraft.world.entity.player.Inventory playerInventory,
                    Player ignored
                ) {
                    return InventoryImpl.this.createMenu(containerId, playerInventory);
                }
            };
            java.util.OptionalInt opened = player.openMenu(provider);
            if (opened.isEmpty()) {
                throw new IllegalStateException("Minecraft refused to open the inventory");
            }
            AbstractContainerMenu menu = player.containerMenu;
            View view = new View(player, menu);
            views.add(view);
            return view;
        }

        private AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory playerInventory
        ) {
            MenuType<?> type = switch (rows) {
                case 1 -> MenuType.GENERIC_9x1;
                case 2 -> MenuType.GENERIC_9x2;
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                case 6 -> MenuType.GENERIC_9x6;
                default -> throw new IllegalStateException("Unsupported row count: " + rows);
            };
            return new ChestMenu(type, containerId, playerInventory, container, rows);
        }

        @Override public Collection<ServerPlayer> viewers() {
            return views.stream().filter(View::active).map(View::player).toList();
        }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            for (View view : views.toArray(View[]::new)) view.close();
        }
        private void checkActive() { if (!active()) throw new IllegalStateException("Inventory is closed"); }
        private void checkSlot(int slot) {
            if (slot < 0 || slot >= size()) throw new IndexOutOfBoundsException("slot " + slot + ", size " + size());
        }

        private final class View implements InventoryView {
            private final ServerPlayer player;
            private final AbstractContainerMenu menu;
            private final AtomicBoolean active = new AtomicBoolean(true);
            private View(ServerPlayer player, AbstractContainerMenu menu) { this.player = player; this.menu = menu; }
            @Override public ServerPlayer player() { return player; }
            @Override public AbstractContainerMenu menu() { return menu; }
            @Override public boolean active() { return active.get(); }
            @Override public void close() {
                if (!active.compareAndSet(true, false)) return;
                views.remove(this);
                if (player.containerMenu == menu) player.closeContainer();
            }
        }
    }
}
