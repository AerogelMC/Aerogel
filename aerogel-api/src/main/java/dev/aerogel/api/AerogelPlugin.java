package dev.aerogel.api;

/** Main entry point implemented by an Aerogel server plugin. */
@FunctionalInterface
public interface AerogelPlugin {
    /** Called before Minecraft's dedicated-server main method is invoked. */
    void onLoad(PluginContext context) throws Exception;

    /** Called before this plugin's lifecycle is reloaded or the loader releases it. */
    default void onUnload(PluginContext context) throws Exception {
    }

    /**
     * Reloads runtime state. Mixin bytecode and plugin classes remain loaded until a server restart.
     * Plugins with listeners or other registrations should undo them in {@link #onUnload(PluginContext)}.
     */
    default void onReload(PluginContext context) throws Exception {
        onUnload(context);
        onLoad(context);
    }
}
