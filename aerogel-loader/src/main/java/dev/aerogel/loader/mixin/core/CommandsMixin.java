package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.command.CommandRegistrationEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.commands.Commands")
abstract class CommandsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$commandsReady(CallbackInfo callbackInfo) {
        EventHooks.post(new CommandRegistrationEvent(this));
    }
}
