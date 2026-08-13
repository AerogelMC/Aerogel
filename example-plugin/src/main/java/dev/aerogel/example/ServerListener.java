package dev.aerogel.example;

import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.EventHandler;
import dev.aerogel.api.event.server.ServerStoppingEvent;

public final class ServerListener {
    private final PluginContext context;

    public ServerListener(PluginContext context) {
        this.context = context;
    }

    @EventHandler
    private void onServerStopping(ServerStoppingEvent event) {
        context.logger().info("The server is stopping.");
    }
}
