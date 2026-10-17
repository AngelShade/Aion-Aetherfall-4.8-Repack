package com.aionemu.commons.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.reflect.TypeToken;

/** Shared JSON boundary. Constructors/record adapters are used; Unsafe is forbidden. */
public final class Json {
    private static final Gson CODEC = new GsonBuilder().disableJdkUnsafe()
        .disableHtmlEscaping().setStrictness(Strictness.STRICT).create();
    private static final Gson PRETTY = CODEC.newBuilder().setPrettyPrinting().create();
    // Old Fastjson file snapshots can contain unquoted numeric map keys.
    // Only the explicit legacy-file importer uses this reader.
    private static final Gson LEGACY_FILES = CODEC.newBuilder().setStrictness(Strictness.LENIENT).create();

    public static String write(Object value) { return CODEC.toJson(value); }
    public static String pretty(Object value) { return PRETTY.toJson(value); }
    public static byte[] bytes(Object value) { return write(value).getBytes(StandardCharsets.UTF_8); }
    public static byte[] sortedBytes(Object value) {
        return CODEC.toJson(sorted(CODEC.toJsonTree(value))).getBytes(StandardCharsets.UTF_8);
    }
    public static JsonObject read(String json) {
        JsonElement value = CODEC.fromJson(json, JsonElement.class);
        return absent(value) ? null : value.getAsJsonObject();
    }
    public static JsonObject read(byte[] json) { return read(new String(json, StandardCharsets.UTF_8)); }
    public static <T> T read(String json, Class<T> type) { return CODEC.fromJson(json, type); }
    public static <T> T read(String json, TypeToken<T> type) { return CODEC.fromJson(json, type); }
    public static <T> T readLegacyFile(String json, Class<T> type) { return LEGACY_FILES.fromJson(json, type); }
    public static <T> List<T> readList(String json, Class<T> type) {
        JsonElement value = CODEC.fromJson(json, JsonElement.class);
        if (absent(value)) return null;
        JsonArray array = value.getAsJsonArray();
        var result = new ArrayList<T>(array.size());
        for (JsonElement item : array) result.add(CODEC.fromJson(item, type));
        return result;
    }
    public static JsonObject object(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return absent(element) ? null : element.getAsJsonObject();
    }
    public static JsonObject object(JsonArray value, int index) {
        JsonElement element = value.get(index);
        return absent(element) ? null : element.getAsJsonObject();
    }
    public static JsonArray array(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return absent(element) ? null : element.getAsJsonArray();
    }
    public static JsonArray array(JsonArray value, int index) {
        JsonElement element = value.get(index);
        return absent(element) ? null : element.getAsJsonArray();
    }
    public static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return absent(element) ? null : element.getAsString();
    }
    public static int integer(JsonObject value, String key) { return integer(value.get(key)); }
    public static int integer(JsonArray value, int index) { return integer(value.get(index)); }
    public static long longValue(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return absent(element) ? 0 : element.getAsLong();
    }
    public static double decimal(JsonObject value, String key) { return decimal(value.get(key)); }
    public static double decimal(JsonArray value, int index) { return decimal(value.get(index)); }
    public static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return !absent(element) && element.getAsBoolean();
    }
    private static boolean absent(JsonElement value) { return value == null || value.isJsonNull(); }
    private static int integer(JsonElement value) { return absent(value) ? 0 : value.getAsInt(); }
    private static double decimal(JsonElement value) { return absent(value) ? 0 : value.getAsDouble(); }
    private static JsonElement sorted(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet()))
                result.add(key, sorted(value.getAsJsonObject().get(key)));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray(value.getAsJsonArray().size());
            for (JsonElement item : value.getAsJsonArray()) result.add(sorted(item));
            return result;
        }
        return value;
    }
    private Json() {}
}
