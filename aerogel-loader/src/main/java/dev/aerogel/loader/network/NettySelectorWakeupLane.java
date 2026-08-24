package dev.aerogel.loader.network;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Keeps a contended native-selector wakeup off simulation and server threads.
 *
 * <p>The task submitted here only publishes an already ordered connection-lane
 * continuation into Netty. It does not execute packet work. Virtual threads
 * are appropriate for this boundary because the Windows selector wakeup may
 * block on its monitor; a blocked wakeup therefore neither pins a simulation
 * worker nor serialises unrelated connections behind a fixed worker count.</p>
 */
public final class NettySelectorWakeupLane {
    private static final Executor WAKEUPS = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("Aerogel-Netty-Wakeup-", 0L).factory());

    private NettySelectorWakeupLane() {
    }

    public static void execute(Runnable publication) {
        WAKEUPS.execute(Objects.requireNonNull(publication, "publication"));
    }
}
