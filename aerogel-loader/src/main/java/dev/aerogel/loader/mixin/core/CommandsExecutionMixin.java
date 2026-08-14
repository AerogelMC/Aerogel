package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.command.CommandExecuteEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.commands.Commands")
abstract class CommandsExecutionMixin {
    @Unique private boolean aerogel$commandOverride;

    @Inject(
        method = "performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeCommand(@Coerce Object source, String command, CallbackInfo callbackInfo) {
        if (aerogel$commandOverride) return;
        CommandExecuteEvent event = new CommandExecuteEvent(EventHooks.cast(source), command);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (!event.command().equals(command)) {
            aerogel$commandOverride = true;
            try {
                EventHooks.call(this, "performPrefixedCommand", source, event.command());
            } finally {
                aerogel$commandOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
