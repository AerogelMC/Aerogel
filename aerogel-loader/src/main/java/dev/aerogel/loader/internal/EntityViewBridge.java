package dev.aerogel.loader.internal;

import net.minecraft.network.syncher.SynchedEntityData;

/** Accesses the shared entity flags without reflection. */
public interface EntityViewBridge {
    byte aerogel$sharedFlags();
    SynchedEntityData.DataValue<Byte> aerogel$sharedFlagsValue(byte flags);
}
