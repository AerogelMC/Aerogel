package net.minecraft.world.item.component;

import net.minecraft.network.chat.Component;
import java.util.List;

public record ItemLore(List<Component> lines) { }
