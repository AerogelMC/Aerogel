package dev.aerogel.example;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.server.ServerStartedEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ExamplePlugin implements AerogelPlugin {
    @Override
    public void onLoad(PluginContext context) {
        context.logger().info("Example plugin loaded; data directory = " + context.dataDirectory());
        context.events().listen(ServerStartedEvent.class, event ->
            context.logger().info("The server has started."));
        context.commands().register(Commands.literal("aerogel-example")
            .executes(command -> {
                command.getSource().sendSuccess(
                    () -> Component.literal("Hello from Aerogel."), false);
                return 1;
            }));
    }

    @Override
    public void onUnload(PluginContext context) {
        context.logger().info("Example plugin unloaded.");
    }
}
