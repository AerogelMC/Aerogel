package com.mojang.authlib;

import com.mojang.authlib.properties.PropertyMap;

import java.util.UUID;

public record GameProfile(UUID id, String name, PropertyMap properties) {
    public GameProfile(UUID id, String name) {
        this(id, name, new PropertyMap());
    }
}
