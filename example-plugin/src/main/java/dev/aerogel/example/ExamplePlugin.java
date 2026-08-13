package dev.aerogel.example;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;

public final class ExamplePlugin implements AerogelPlugin {
    @Override
    public void onLoad(PluginContext context) {
        context.logger().info("Example plugin loaded; data directory = " + context.dataDirectory());
    }
}
