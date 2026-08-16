package net.minecraft.network.chat;

import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

public interface ClickEvent {
    record Custom(Identifier id, Optional<Tag> payload) implements ClickEvent { }
}
