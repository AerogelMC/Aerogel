package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ListTag implements Tag {
    private final List<Tag> values = new ArrayList<>();
    public int size() { return values.size(); }
    public Tag get(int index) { return values.get(index); }
    public boolean addTag(int index, Tag value) {
        if (!values.isEmpty() && values.getFirst().getId() != value.getId()) return false;
        values.add(index, value);
        return true;
    }
    @Override public byte getId() { return TAG_LIST; }
    @Override public Optional<ListTag> asList() { return Optional.of(this); }
}
