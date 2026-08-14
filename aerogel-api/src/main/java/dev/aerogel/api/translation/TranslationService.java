package dev.aerogel.api.translation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Builds Minecraft components from a plugin's language resources. */
public interface TranslationService {
    /** Uses the plugin's {@code en_us} value as the client fallback. */
    Component component(String key, Object... arguments);

    /** Uses the recipient's client language as the fallback language. */
    Component componentFor(ServerPlayer recipient, String key, Object... arguments);

    /** Uses an explicit Minecraft language code such as {@code ko_kr}. */
    Component componentForLocale(String language, String key, Object... arguments);

    /** Resolves plain text for logging or other non-component output. */
    String text(String language, String key);
}
