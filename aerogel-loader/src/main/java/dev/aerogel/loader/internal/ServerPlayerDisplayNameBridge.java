package dev.aerogel.loader.internal;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;

/** Internal bridge between the Player and ServerPlayer Mixins. */
public interface ServerPlayerDisplayNameBridge {
    Component aerogel$displayNameOverride();

    GameProfile aerogel$packetProfileOverride();

    void aerogel$packetProfileOverride(GameProfile profile);

    boolean aerogel$tabListHidden();

    boolean aerogel$nameTagHidden();
}
