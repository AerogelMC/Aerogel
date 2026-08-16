package dev.aerogel.api.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;

public record MenuClick(ServerPlayer player, Menu menu, int slot, int button, ContainerInput input) { }
