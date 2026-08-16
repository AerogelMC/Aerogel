package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.command.CommandExecuteEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.commands.Commands")
abstract class CommandsExecutionMixin {
    @Shadow public abstract void performPrefixedCommand(CommandSourceStack source, String command);
    @Unique private boolean aerogel$commandOverride;

    @Inject(
        method = "performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeCommand(CommandSourceStack source, String command, CallbackInfo callbackInfo) {
        if (aerogel$commandOverride || !EventHooks.hasListeners(CommandExecuteEvent.class)) return;
        CommandExecuteEvent event = new CommandExecuteEvent(source, command);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (!event.command().equals(command)) {
            aerogel$commandOverride = true;
            try {
                performPrefixedCommand(source, event.command());
            } finally {
                aerogel$commandOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
