package dev.aerogel.api;

/** Main entry point implemented by an Aerogel server plugin. */
@FunctionalInterface
public interface AerogelPlugin {
    /** Called before Minecraft's dedicated-server main method is invoked. */
    void onLoad(PluginContext context) throws Exception;
}
