package net.minecraft.world.level.block;

public class Block {
    public static final int UPDATE_NEIGHBORS = 1;
    public static final int UPDATE_CLIENTS = 2;
    public static final int UPDATE_ALL = UPDATE_NEIGHBORS | UPDATE_CLIENTS;
}
