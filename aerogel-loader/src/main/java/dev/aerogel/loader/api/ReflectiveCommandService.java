package dev.aerogel.loader.api;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.aerogel.api.command.CommandRegistration;
import dev.aerogel.api.command.CommandService;
import net.minecraft.commands.CommandSourceStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import dev.aerogel.loader.plugin.PluginFailures;

final class ReflectiveCommandService implements CommandService {
    private final PluginApiScope scope;
    private final Set<RegistrationImpl> registrations = ConcurrentHashMap.newKeySet();
    private int batchDepth;
    private boolean synchronizationPending;
    private Object pendingServer;

    ReflectiveCommandService(PluginApiScope scope) {
        this.scope = scope;
    }

    @Override public CommandRegistration register(
        LiteralArgumentBuilder<CommandSourceStack> brigadierRoot
    ) {
        return registerRoot(brigadierRoot.getLiteral(), guarded(brigadierRoot.build()));
    }

    @Override public CommandRegistration register(
        LiteralCommandNode<CommandSourceStack> brigadierRoot
    ) {
        return registerRoot(brigadierRoot.getLiteral(), guarded(brigadierRoot));
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
        beginBatch();
        try {
            for (RegistrationImpl registration : registrations) {
                if (registration.active() && !registration.installed) install(registration);
            }
        } finally {
            endBatch();
        }
    }

    synchronized void beginBatch() {
        batchDepth++;
    }

    void endBatch() {
        Object server = null;
        synchronized (this) {
            if (batchDepth <= 0) {
                throw new IllegalStateException("Command mutation batch is not active");
            }
            batchDepth--;
            if (batchDepth == 0 && synchronizationPending) {
                synchronizationPending = false;
                server = pendingServer;
                pendingServer = null;
            }
        }
        if (server != null) syncCommands(server);
    }

    private synchronized void install(RegistrationImpl registration) {
        if (!registration.active() || registration.installed) return;
        Object server = scope.serverHandle();
        Object dispatcher = Reflect.invoke(Reflect.invoke(server, "getCommands"), "getDispatcher");
        Reflect.invoke(Reflect.invoke(dispatcher, "getRoot"), "addChild", registration.root);
        registration.installed = true;
        requestSynchronization(server);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CommandNode<CommandSourceStack> guarded(CommandNode<CommandSourceStack> node) {
        ArgumentBuilder<CommandSourceStack, ?> builder = node.createBuilder();
        Command<CommandSourceStack> command = node.getCommand();
        if (command != null) {
            builder.executes(context -> {
                try {
                    return command.run(context);
                } catch (Throwable failure) {
                    PluginFailures.rethrowFatal(failure);
                    scope.logger().log(Level.SEVERE, "Command /" + node.getName() + " failed", failure);
                    return 0;
                }
            });
        }
        if (builder instanceof RequiredArgumentBuilder<?, ?>) {
            RequiredArgumentBuilder<CommandSourceStack, ?> required =
                (RequiredArgumentBuilder<CommandSourceStack, ?>) builder;
            SuggestionProvider<CommandSourceStack> suggestions = required.getSuggestionsProvider();
            if (suggestions != null) {
                required.suggests((context, suggestionsBuilder) -> {
                    try {
                        return suggestions.getSuggestions(context, suggestionsBuilder).handle((result, failure) -> {
                            if (failure == null) return result;
                            PluginFailures.rethrowFatal(failure);
                            scope.logger().log(Level.SEVERE,
                                "Suggestions for /" + node.getName() + " failed", failure);
                            return Suggestions.empty().join();
                        });
                    } catch (Throwable failure) {
                        PluginFailures.rethrowFatal(failure);
                        scope.logger().log(Level.SEVERE,
                            "Suggestions for /" + node.getName() + " failed", failure);
                        return Suggestions.empty();
                    }
                });
            }
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            builder.then(guarded(child));
        }
        return builder.build();
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

    private void requestSynchronization(Object server) {
        synchronized (this) {
            if (batchDepth > 0) {
                synchronizationPending = true;
                pendingServer = server;
                return;
            }
        }
        syncCommands(server);
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
            requestSynchronization(server);
        }
    }
}
