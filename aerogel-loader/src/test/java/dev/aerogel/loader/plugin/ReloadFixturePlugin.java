package dev.aerogel.loader.plugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;

public final class ReloadFixturePlugin implements AerogelPlugin {
    static final String LOADS = "aerogel.test.reload.loads";
    static final String UNLOADS = "aerogel.test.reload.unloads";

    @Override
    public void onLoad(PluginContext context) {
        increment(LOADS);
    }

    @Override
    public void onUnload(PluginContext context) {
        increment(UNLOADS);
    }

    private static void increment(String key) {
        System.setProperty(key, Integer.toString(Integer.parseInt(System.getProperty(key, "0")) + 1));
    }
}
