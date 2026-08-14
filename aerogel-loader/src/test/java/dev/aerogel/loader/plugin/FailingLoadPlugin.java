package dev.aerogel.loader.plugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;

public final class FailingLoadPlugin implements AerogelPlugin {
    @Override
    public void onLoad(PluginContext context) {
        throw new IllegalStateException("Expected initialization failure");
    }
}
