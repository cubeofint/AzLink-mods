package com.azuriom.azlink.common.kits.manifest.hash;

import com.azuriom.azlink.common.utils.Hash;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PHP-compatible canonical JSON (ksort object keys, preserve list order,
 * {@code JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE}, then SHA-256).
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static JsonElement normalize(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (element.isJsonPrimitive() || element.isJsonArray()) {
            if (element.isJsonArray()) {
                JsonArray out = new JsonArray();
                for (JsonElement child : element.getAsJsonArray()) {
                    out.add(normalize(child));
                }
                return out;
            }
            return element;
        }
        JsonObject object = element.getAsJsonObject();
        TreeMap<String, JsonElement> sorted = new TreeMap<String, JsonElement>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            sorted.put(entry.getKey(), normalize(entry.getValue()));
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
            out.add(entry.getKey(), entry.getValue());
        }
        return out;
    }

    public static String encode(JsonElement element) {
        return encodeNormalized(normalize(element));
    }

    public static String hash(JsonElement element) {
        return Hash.SHA_256.hash(encode(element));
    }

    private static String encodeNormalized(JsonElement element) {
        StringBuilder sb = new StringBuilder();
        write(sb, element);
        return sb.toString();
    }

    private static void write(StringBuilder sb, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            sb.append("null");
            return;
        }
        if (element.isJsonPrimitive()) {
            writePrimitive(sb, element.getAsJsonPrimitive());
            return;
        }
        if (element.isJsonArray()) {
            sb.append('[');
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, array.get(i));
            }
            sb.append(']');
            return;
        }
        sb.append('{');
        JsonObject object = element.getAsJsonObject();
        List<String> keys = new ArrayList<String>(object.keySet());
        Collections.sort(keys);
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, key);
            sb.append(':');
            write(sb, object.get(key));
        }
        sb.append('}');
    }

    private static void writePrimitive(StringBuilder sb, JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            sb.append(primitive.getAsBoolean());
            return;
        }
        if (primitive.isNumber()) {
            Number number = primitive.getAsNumber();
            if (number instanceof Double || number instanceof Float) {
                double value = number.doubleValue();
                if (value == Math.rint(value) && !Double.isInfinite(value)) {
                    sb.append((long) value);
                } else {
                    sb.append(Double.toString(value));
                }
            } else {
                sb.append(number.toString());
            }
            return;
        }
        writeString(sb, primitive.getAsString());
    }

    /**
     * Escape like PHP {@code json_encode} with UNESCAPED_SLASHES | UNESCAPED_UNICODE.
     */
    private static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }
}
