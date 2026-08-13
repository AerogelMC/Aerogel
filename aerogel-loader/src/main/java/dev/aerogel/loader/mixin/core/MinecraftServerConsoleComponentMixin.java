package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.logging.ComponentAnsiRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerConsoleComponentMixin {
    @Redirect(
        method = {"sendSystemMessage", "logChatMessage"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"
        )
    )
    private String aerogel$renderConsoleComponent(Component component) {
        return ComponentAnsiRenderer.render(component);
    }
}
