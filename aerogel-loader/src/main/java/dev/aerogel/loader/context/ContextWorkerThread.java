package dev.aerogel.loader.context;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/** A context worker with hot scheduler state stored directly on the thread. */
final class ContextWorkerThread extends ForkJoinWorkerThread {
    ContextThreadState.AccessScope accessScope;
    Object[] localValues = new Object[8];

    ContextWorkerThread(ForkJoinPool pool) {
        super(pool);
    }
}
