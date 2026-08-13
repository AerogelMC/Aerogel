package dev.aerogel.api;

/** Main entry point implemented by an Aerogel server plugin. */
@FunctionalInterface
public interface AerogelPlugin {
    /** Called after Minecraft bootstrap and before the dedicated server finishes starting. */
    void onLoad(PluginContext context) throws Exception;

    /** Called before this plugin's lifecycle is reloaded or the loader releases it. */
    default void onUnload(PluginContext context) throws Exception {
    }

    /**
     * Convenience lifecycle hook for plugin-managed state. Aerogel's reload command creates a new
     * plugin class loader instead of calling this method on the old instance. Mixin hot swap is
     * best-effort and some bytecode changes can still require a server restart.
     */
    default void onReload(PluginContext context) throws Exception {
        onUnload(context);
        onLoad(context);
    }
}
