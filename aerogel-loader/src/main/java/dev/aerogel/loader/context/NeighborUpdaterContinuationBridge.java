package dev.aerogel.loader.context;

import java.util.ArrayDeque;
import java.util.List;

/** Internal access to a suspended vanilla neighbor-update stack. */
public interface NeighborUpdaterContinuationBridge {
    void aerogel$resumeNeighborUpdates();
    ArrayDeque<?> aerogel$neighborUpdateStack();
    List<?> aerogel$neighborUpdatesAddedThisLayer();
    void aerogel$neighborUpdateCount(int count);
}
