package dev.aerogel.loader.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.aerogel.api.storage.StorageException;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

/** A readable JSON projection that retains every NBT tag type across text parsing. */
final class LosslessNbtJson {
    private static final String TYPE = "$nbt";
    private static final String VALUE = "value";

    private LosslessNbtJson() {
    }

    static JsonElement encode(Tag tag) {
        return switch (tag.getId()) {
            case Tag.TAG_END -> tagged("end", (JsonElement) null);
            case Tag.TAG_BYTE -> tagged("byte", number(tag).byteValue());
            case Tag.TAG_SHORT -> tagged("short", number(tag).shortValue());
            case Tag.TAG_INT -> new JsonPrimitive(number(tag).intValue());
            case Tag.TAG_LONG -> tagged("long", Long.toString(number(tag).longValue()));
            case Tag.TAG_FLOAT -> tagged("float", Float.toString(number(tag).floatValue()));
            case Tag.TAG_DOUBLE -> encodeDouble(number(tag).doubleValue());
            case Tag.TAG_BYTE_ARRAY -> tagged("byte_array", byteArray(tag));
            case Tag.TAG_STRING -> new JsonPrimitive(tag.asString().orElseThrow());
            case Tag.TAG_LIST -> encodeList(tag.asList().orElseThrow());
            case Tag.TAG_COMPOUND -> encodeCompound(tag.asCompound().orElseThrow());
            case Tag.TAG_INT_ARRAY -> tagged("int_array", intArray(tag));
            case Tag.TAG_LONG_ARRAY -> tagged("long_array", longArray(tag));
            default -> throw new StorageException("Unknown NBT tag id " + tag.getId());
        };
    }

    static Tag decode(JsonElement json) {
        return decode(json, "$");
    }

    private static JsonElement encodeDouble(double value) {
        return tagged("double", Double.toString(value));
    }

    private static JsonArray encodeList(ListTag list) {
        JsonArray json = new JsonArray(list.size());
        for (int index = 0; index < list.size(); index++) json.add(encode(list.get(index)));
        return json;
    }

    private static JsonElement encodeCompound(CompoundTag compound) {
        JsonObject json = compoundObject(compound);
        return compound.contains(TYPE) ? tagged("compound", json) : json;
    }

    private static JsonObject compoundObject(CompoundTag compound) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Tag> entry : compound.entrySet()) {
            json.add(entry.getKey(), encode(entry.getValue()));
        }
        return json;
    }

    private static JsonArray byteArray(Tag tag) {
        JsonArray json = new JsonArray();
        for (byte value : tag.asByteArray().orElseThrow()) json.add(value);
        return json;
    }

    private static JsonArray intArray(Tag tag) {
        JsonArray json = new JsonArray();
        for (int value : tag.asIntArray().orElseThrow()) json.add(value);
        return json;
    }

    private static JsonArray longArray(Tag tag) {
        JsonArray json = new JsonArray();
        for (long value : tag.asLongArray().orElseThrow()) json.add(Long.toString(value));
        return json;
    }

    private static Number number(Tag tag) {
        return tag.asNumber().orElseThrow();
    }

    private static JsonObject tagged(String type, Number value) {
        return tagged(type, value == null ? null : new JsonPrimitive(value));
    }

    private static JsonObject tagged(String type, String value) {
        return tagged(type, value == null ? null : new JsonPrimitive(value));
    }

    private static JsonObject tagged(String type, JsonElement value) {
        JsonObject json = new JsonObject();
        json.addProperty(TYPE, type);
        if (value != null) json.add(VALUE, value);
        return json;
    }

    private static Tag decode(JsonElement json, String path) {
        if (json == null || json.isJsonNull()) fail(path, "null is not an NBT value");
        if (json.isJsonObject()) return decodeObject(json.getAsJsonObject(), path);
        if (json.isJsonArray()) return decodeList(json.getAsJsonArray(), path);
        if (!json.isJsonPrimitive()) return fail(path, "unsupported JSON value");

        JsonPrimitive primitive = json.getAsJsonPrimitive();
        if (primitive.isString()) return StringTag.valueOf(primitive.getAsString());
        if (primitive.isBoolean()) return ByteTag.valueOf(primitive.getAsBoolean());
        if (!primitive.isNumber()) return fail(path, "unsupported JSON primitive");

        String raw = primitive.getAsString();
        try {
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return DoubleTag.valueOf(Double.parseDouble(raw));
            }
            return IntTag.valueOf(primitive.getAsBigDecimal().intValueExact());
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new StorageException("Invalid untagged NBT number at " + path + ": " + raw, exception);
        }
    }

    private static Tag decodeObject(JsonObject json, String path) {
        if (!json.has(TYPE)) return decodeCompound(json, path);
        if (!json.get(TYPE).isJsonPrimitive() || !json.getAsJsonPrimitive(TYPE).isString()) {
            return fail(path, TYPE + " must be a string");
        }

        String type = json.get(TYPE).getAsString();
        if (type.equals("end")) {
            if (json.size() != 1) fail(path, "end tag must not contain a value");
            return EndTag.INSTANCE;
        }
        if (json.size() != 2 || !json.has(VALUE)) {
            return fail(path, "tagged " + type + " must contain only " + TYPE + " and " + VALUE);
        }
        JsonElement value = json.get(VALUE);
        try {
            return switch (type) {
                case "byte" -> ByteTag.valueOf(value.getAsByte());
                case "short" -> ShortTag.valueOf(value.getAsShort());
                case "long" -> LongTag.valueOf(Long.parseLong(value.getAsString()));
                case "float" -> FloatTag.valueOf(Float.parseFloat(value.getAsString()));
                case "double" -> DoubleTag.valueOf(Double.parseDouble(value.getAsString()));
                case "byte_array" -> new ByteArrayTag(readBytes(value, path));
                case "int_array" -> new IntArrayTag(readInts(value, path));
                case "long_array" -> new LongArrayTag(readLongs(value, path));
                case "compound" -> decodeCompound(value.getAsJsonObject(), path + ".value");
                default -> fail(path, "unknown NBT type " + type);
            };
        } catch (IllegalStateException | NumberFormatException exception) {
            throw new StorageException("Invalid " + type + " tag at " + path, exception);
        }
    }

    private static CompoundTag decodeCompound(JsonObject json, String path) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            compound.put(entry.getKey(), decode(entry.getValue(), child(path, entry.getKey())));
        }
        return compound;
    }

    private static ListTag decodeList(JsonArray json, String path) {
        ListTag list = new ListTag();
        for (int index = 0; index < json.size(); index++) {
            Tag value = decode(json.get(index), path + "[" + index + "]");
            if (!list.addTag(index, value)) {
                fail(path + "[" + index + "]", "NBT lists must contain one tag type");
            }
        }
        return list;
    }

    private static byte[] readBytes(JsonElement value, String path) {
        JsonArray json = value.getAsJsonArray();
        byte[] result = new byte[json.size()];
        for (int index = 0; index < result.length; index++) result[index] = json.get(index).getAsByte();
        return result;
    }

    private static int[] readInts(JsonElement value, String path) {
        JsonArray json = value.getAsJsonArray();
        int[] result = new int[json.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = json.get(index).getAsBigDecimal().intValueExact();
        }
        return result;
    }

    private static long[] readLongs(JsonElement value, String path) {
        JsonArray json = value.getAsJsonArray();
        long[] result = new long[json.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = Long.parseLong(json.get(index).getAsString());
        }
        return result;
    }

    private static String child(String path, String key) {
        return path + "." + key.replace("\\", "\\\\").replace(".", "\\.");
    }

    private static <T> T fail(String path, String message) {
        throw new StorageException("Invalid lossless NBT JSON at " + path + ": " + message);
    }
}
