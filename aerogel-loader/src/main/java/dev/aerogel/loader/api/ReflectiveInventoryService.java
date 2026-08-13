package dev.aerogel.loader.api;

import dev.aerogel.api.inventory.Inventory;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.inventory.InventoryView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class ReflectiveInventoryService implements InventoryService {
    private final PluginApiScope scope;

    ReflectiveInventoryService(PluginApiScope scope) { this.scope = scope; }

    @Override public Inventory create(int rows, Component title) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
        Object container = Reflect.construct(Reflect.type(scope.loader(), "net.minecraft.world.SimpleContainer"), rows * 9);
        return scope.own(new InventoryImpl(container, title, rows));
    }

    @Override public Inventory wrap(Container container, Component title) {
        int size = ((Number) Reflect.invoke(container, "getContainerSize")).intValue();
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Wrapped chest container size must be 9, 18, 27, 36, 45, or 54");
        }
        return scope.own(new InventoryImpl(container, title, size / 9));
    }

    private final class InventoryImpl implements Inventory {
        private final Object container;
        private final Component title;
        private final int rows;
        private final Set<View> views = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean active = new AtomicBoolean(true);

        private InventoryImpl(Object container, Component title, int rows) {
            this.container = java.util.Objects.requireNonNull(container, "container");
            this.title = java.util.Objects.requireNonNull(title, "title");
            this.rows = rows;
        }

        @Override public int size() { return rows * 9; }
        @Override public Container vanilla() { return (Container) container; }
        @Override public ItemStack item(int slot) {
            checkSlot(slot); return (ItemStack) Reflect.invoke(container, "getItem", slot);
        }
        @Override public void item(int slot, ItemStack itemStack) {
            checkActive(); checkSlot(slot); Reflect.invoke(container, "setItem", slot, itemStack);
        }
        @Override public void clear() { checkActive(); Reflect.invoke(container, "clearContent"); }

        @Override public InventoryView open(ServerPlayer player) {
            checkActive();
            Class<?> providerType = Reflect.type(scope.loader(), "net.minecraft.world.MenuProvider");
            Object provider = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[]{providerType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getDisplayName" -> title;
                    case "createMenu" -> createMenu(((Number) arguments[0]).intValue(), arguments[1]);
                    case "toString" -> "Aerogel inventory provider";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
            Object opened = Reflect.invoke(player, "openMenu", provider);
            if (opened instanceof java.util.OptionalInt value && value.isEmpty()) {
                throw new IllegalStateException("Minecraft refused to open the inventory");
            }
            Object menu = Reflect.field(player, "containerMenu");
            View view = new View(player, menu);
            views.add(view);
            return view;
        }

        private Object createMenu(int containerId, Object playerInventory) {
            Class<?> menuType = Reflect.type(scope.loader(), "net.minecraft.world.inventory.MenuType");
            Object type = Reflect.staticField(menuType, "GENERIC_9x" + rows);
            return Reflect.construct(Reflect.type(scope.loader(), "net.minecraft.world.inventory.ChestMenu"),
                type, containerId, playerInventory, container, rows);
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
            private final Object player;
            private final Object menu;
            private final AtomicBoolean active = new AtomicBoolean(true);
            private View(Object player, Object menu) { this.player = player; this.menu = menu; }
            private Object rawPlayer() { return player; }
            @Override public ServerPlayer player() { return (ServerPlayer) player; }
            @Override public AbstractContainerMenu menu() { return (AbstractContainerMenu) menu; }
            @Override public boolean active() { return active.get(); }
            @Override public void close() {
                if (!active.compareAndSet(true, false)) return;
                views.remove(this);
                if (Reflect.field(player, "containerMenu") == menu) Reflect.invoke(player, "closeContainer");
            }
        }
    }
}
