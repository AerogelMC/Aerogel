package dev.aerogel.loader.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LosslessNbtJsonTest {
    @Test
    void preservesEveryNbtValueTypeAcrossJsonText() {
        CompoundTag original = new CompoundTag();
        original.put("byte", ByteTag.valueOf((byte) 3));
        original.put("short", ShortTag.valueOf((short) 4));
        original.put("int", IntTag.valueOf(5));
        original.put("long", LongTag.valueOf(9_007_199_254_740_993L));
        original.put("float", FloatTag.valueOf(1.25F));
        original.put("double", DoubleTag.valueOf(-0.0D));
        original.put("infinite", DoubleTag.valueOf(Double.POSITIVE_INFINITY));
        original.put("string", StringTag.valueOf("hello"));
        original.put("bytes", new ByteArrayTag(new byte[]{-1, 0, 1}));
        original.put("ints", new IntArrayTag(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}));
        original.put("longs", new LongArrayTag(new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}));

        ListTag list = new ListTag();
        list.addTag(0, LongTag.valueOf(1));
        list.addTag(1, LongTag.valueOf(2));
        original.put("list", list);

        CompoundTag reservedName = new CompoundTag();
        reservedName.put("$nbt", StringTag.valueOf("plugin-owned value"));
        reservedName.put("value", IntTag.valueOf(8));
        original.put("reserved", reservedName);

        JsonElement tree = LosslessNbtJson.encode(original);
        String text = new Gson().toJson(tree);
        Tag decoded = LosslessNbtJson.decode(JsonParser.parseString(text));

        assertSameTag(original, decoded);
    }

    private static void assertSameTag(Tag expected, Tag actual) {
        assertEquals(expected.getId(), actual.getId());
        switch (expected.getId()) {
            case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG ->
                assertEquals(expected.asNumber().orElseThrow().longValue(),
                    actual.asNumber().orElseThrow().longValue());
            case Tag.TAG_FLOAT -> assertEquals(
                Float.floatToIntBits(expected.asNumber().orElseThrow().floatValue()),
                Float.floatToIntBits(actual.asNumber().orElseThrow().floatValue()));
            case Tag.TAG_DOUBLE -> assertEquals(
                Double.doubleToLongBits(expected.asNumber().orElseThrow().doubleValue()),
                Double.doubleToLongBits(actual.asNumber().orElseThrow().doubleValue()));
            case Tag.TAG_STRING -> assertEquals(
                expected.asString().orElseThrow(), actual.asString().orElseThrow());
            case Tag.TAG_BYTE_ARRAY -> assertArrayEquals(
                expected.asByteArray().orElseThrow(), actual.asByteArray().orElseThrow());
            case Tag.TAG_INT_ARRAY -> assertArrayEquals(
                expected.asIntArray().orElseThrow(), actual.asIntArray().orElseThrow());
            case Tag.TAG_LONG_ARRAY -> assertArrayEquals(
                expected.asLongArray().orElseThrow(), actual.asLongArray().orElseThrow());
            case Tag.TAG_LIST -> {
                ListTag expectedList = expected.asList().orElseThrow();
                ListTag actualList = actual.asList().orElseThrow();
                assertEquals(expectedList.size(), actualList.size());
                for (int index = 0; index < expectedList.size(); index++) {
                    assertSameTag(expectedList.get(index), actualList.get(index));
                }
            }
            case Tag.TAG_COMPOUND -> {
                Map<String, Tag> expectedValues = expected.asCompound().orElseThrow().entrySet()
                    .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                Map<String, Tag> actualValues = actual.asCompound().orElseThrow().entrySet()
                    .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                assertEquals(expectedValues.keySet(), actualValues.keySet());
                expectedValues.forEach((key, value) -> assertSameTag(value, actualValues.get(key)));
            }
            default -> { }
        }
    }
}
