package dev.aerogel.api.menu;

import net.minecraft.network.chat.Component;

public interface MenuService {
    Menu create(int rows, Component title);
}
