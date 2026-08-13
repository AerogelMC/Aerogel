package dev.aerogel.api.dialog;

import java.util.Optional;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public record DialogResult(ServerPlayer player, String action, Optional<Tag> payload) {
}
