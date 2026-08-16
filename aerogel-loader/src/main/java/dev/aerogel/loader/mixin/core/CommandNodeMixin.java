package dev.aerogel.loader.mixin.core;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.aerogel.loader.internal.CommandNodeBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(value = CommandNode.class, remap = false)
abstract class CommandNodeMixin<S> implements CommandNodeBridge {
    @Shadow @Final private Map<String, CommandNode<S>> children;
    @Shadow @Final private Map<String, LiteralCommandNode<S>> literals;
    @Shadow @Final private Map<String, ArgumentCommandNode<S, ?>> arguments;

    @Override
    public void aerogel$removeChild(String name) {
        children.remove(name);
        literals.remove(name);
        arguments.remove(name);
    }
}
