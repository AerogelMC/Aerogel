package dev.aerogel.loader.api;

import dev.aerogel.loader.context.ContextServiceImpl;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AerogelApiRuntime implements AutoCloseable {
    private final Set<PluginApiScope> scopes = ConcurrentHashMap.newKeySet();
    private final ContextServiceImpl contexts = new ContextServiceImpl();
    private volatile net.minecraft.server.MinecraftServer server;

    public PluginApiScope openScope(String pluginId, java.util.logging.Logger logger) {
        return openScope(
            pluginId,
            logger,
            AerogelApiRuntime.class.getClassLoader(),
            Path.of(System.getProperty("user.dir"), "plugins", pluginId)
        );
    }

    public PluginApiScope openScope(
        String pluginId, java.util.logging.Logger logger, ClassLoader resourceLoader
    ) {
        return openScope(
            pluginId,
            logger,
            resourceLoader,
            Path.of(System.getProperty("user.dir"), "plugins", pluginId)
        );
    }

    public PluginApiScope openScope(
        String pluginId,
        java.util.logging.Logger logger,
        ClassLoader resourceLoader,
        Path dataDirectory
    ) {
        PluginApiScope scope = new PluginApiScope(
            this, pluginId, logger, resourceLoader, dataDirectory);
        scopes.add(scope);
        if (server != null) scope.serverReady();
        return scope;
    }

    public synchronized void attach(Object minecraftServer) {
        if (server != null && server != minecraftServer) {
            throw new IllegalStateException("A different Minecraft server is already attached");
        }
        server = (net.minecraft.server.MinecraftServer) minecraftServer;
        for (PluginApiScope scope : scopes) scope.serverReady();
    }

    public void tick(Object minecraftServer) {
        if (server == null) attach(minecraftServer);
        long tick = ((net.minecraft.server.MinecraftServer) minecraftServer).getTickCount();
        for (PluginApiScope scope : scopes) scope.tick(tick);
    }

    net.minecraft.server.MinecraftServer server() { return server; }
    public ContextServiceImpl contexts() { return contexts; }
    boolean ready() { return server != null; }
    void remove(PluginApiScope scope) { scopes.remove(scope); }

    @Override public void close() {
        for (PluginApiScope scope : scopes.toArray(PluginApiScope[]::new)) scope.close();
        contexts.close();
    }
}
