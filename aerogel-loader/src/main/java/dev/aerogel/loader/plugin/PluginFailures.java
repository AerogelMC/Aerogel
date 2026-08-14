package dev.aerogel.loader.plugin;

public final class PluginFailures {
    private PluginFailures() {
    }

    /** JVM integrity failures are not safe to reinterpret as ordinary plugin callback errors. */
    public static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError error) throw error;
    }
}
