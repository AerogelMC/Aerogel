package dev.aerogel.loader.internal;

/** Invokes the vanilla connection bookkeeping that follows a player respawn. */
public interface RespawnGameListenerBridge {
    void aerogel$restartClientLoadTimerAfterRespawn();
}
