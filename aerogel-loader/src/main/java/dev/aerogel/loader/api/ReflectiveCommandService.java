package dev.aerogel.loader.api;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.aerogel.api.command.CommandRegistration;
import dev.aerogel.api.command.CommandService;
import net.minecraft.commands.CommandSourceStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class ReflectiveCommandService implements CommandService {
    private final PluginApiScope scope;
    private final Set<RegistrationImpl> registrations = ConcurrentHashMap.newKeySet();

    ReflectiveCommandService(PluginApiScope scope) {
        this.scope = scope;
    }

    @Override public CommandRegistration register(
        LiteralArgumentBuilder<CommandSourceStack> brigadierRoot
    ) {
        return registerRoot(brigadierRoot.getLiteral(), brigadierRoot);
    }

    @Override public CommandRegistration register(
        LiteralCommandNode<CommandSourceStack> brigadierRoot
    ) {
        return registerRoot(brigadierRoot.getLiteral(), brigadierRoot);
    }

    private CommandRegistration registerRoot(String name, Object root) {
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid command root: " + name);
        }
        RegistrationImpl registration = new RegistrationImpl(name, root);
        registrations.add(registration);
        scope.own(registration);
        if (scope.ready()) install(registration);
        return registration;
    }

    void serverReady() {
        for (RegistrationImpl registration : registrations) {
            if (registration.active() && !registration.installed) install(registration);
        }
    }

    private synchronized void install(RegistrationImpl registration) {
        if (!registration.active() || registration.installed) return;
        Object server = scope.serverHandle();
        Object dispatcher = Reflect.invoke(Reflect.invoke(server, "getCommands"), "getDispatcher");
        if (registration.root instanceof LiteralArgumentBuilder<?>) {
            Reflect.invoke(dispatcher, "register", registration.root);
        } else {
            Reflect.invoke(Reflect.invoke(dispatcher, "getRoot"), "addChild", registration.root);
        }
        registration.installed = true;
        syncCommands(server);
    }

    private void syncCommands(Object server) {
        try {
            Object commands = Reflect.invoke(server, "getCommands");
            Object players = Reflect.invoke(Reflect.invoke(server, "getPlayerList"), "getPlayers");
            if (players instanceof Iterable<?> iterable) {
                for (Object player : iterable) Reflect.invoke(commands, "sendCommands", player);
            }
        } catch (RuntimeException exception) {
            scope.logger().log(Level.FINE, "Could not immediately synchronize command trees", exception);
        }
    }

    private final class RegistrationImpl implements CommandRegistration {
        private final String name;
        private final Object root;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private volatile boolean installed;

        private RegistrationImpl(String name, Object root) {
            this.name = name;
            this.root = root;
        }

        @Override public String name() {
            return name;
        }

        @Override public boolean active() {
            return active.get();
        }

        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            registrations.remove(this);
            if (!installed || !scope.ready()) return;
            Object server = scope.serverHandle();
            Object rootNode = Reflect.invoke(
                Reflect.invoke(Reflect.invoke(server, "getCommands"), "getDispatcher"), "getRoot");
            Reflect.removeNamedChild(rootNode, name);
            syncCommands(server);
        }
    }
}
