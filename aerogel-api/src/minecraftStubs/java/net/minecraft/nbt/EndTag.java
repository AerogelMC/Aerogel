package net.minecraft.nbt;

public final class EndTag implements Tag {
    public static final EndTag INSTANCE = new EndTag();
    private EndTag() { }
    @Override public byte getId() { return TAG_END; }
}
