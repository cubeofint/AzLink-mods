package com.azuriom.azlink.common.kits.manifest.build;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;
import com.azuriom.azlink.common.kits.manifest.hash.CanonicalJson;
import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.common.kits.manifest.model.ItemIconRef;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadRequest;
import com.azuriom.azlink.common.kits.manifest.model.ManifestItem;
import com.azuriom.azlink.common.kits.manifest.model.NamedAttribute;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds a hashed upload request matching site canonicalize + hash rules.
 */
public final class KitManifestBuilder {

    private KitManifestBuilder() {
    }

    public static List<ManifestItem> sortItems(List<ManifestItem> items) {
        List<ManifestItem> sorted = new ArrayList<ManifestItem>(items);
        Collections.sort(sorted, new Comparator<ManifestItem>() {
            @Override
            public int compare(ManifestItem a, ManifestItem b) {
                int slotCmp = Integer.compare(a.getSlot(), b.getSlot());
                if (slotCmp != 0) {
                    return slotCmp;
                }
                String ka = a.getSemanticKey() == null ? "" : a.getSemanticKey();
                String kb = b.getSemanticKey() == null ? "" : b.getSemanticKey();
                return ka.compareTo(kb);
            }
        });
        return sorted;
    }

    public static JsonObject toCanonicalPayload(List<ManifestItem> items) {
        List<ManifestItem> sorted = sortItems(items);
        JsonArray array = new JsonArray();
        for (ManifestItem item : sorted) {
            array.add(toCanonicalItem(item));
        }
        JsonObject payload = new JsonObject();
        payload.add("items", array);
        return CanonicalJson.normalize(payload).getAsJsonObject();
    }

    public static String computeManifestHash(List<ManifestItem> items) {
        return CanonicalJson.hash(toCanonicalPayload(items));
    }

    public static KitManifestUploadRequest buildRequest(String deliveryKey, int manifestVersion,
                                                        String executorVersion, List<ManifestItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items required");
        }
        if (items.size() > KitManifestLimits.MAX_ITEMS) {
            throw new IllegalArgumentException("too many items");
        }
        List<ManifestItem> sorted = sortItems(items);
        String hash = computeManifestHash(sorted);
        return new KitManifestUploadRequest(
                KitManifestLimits.PROTOCOL_VERSION,
                executorVersion,
                deliveryKey,
                manifestVersion,
                hash,
                sorted
        );
    }

    public static ManifestItem withIcon(ManifestItem item, ItemIconRef icon) {
        return new ManifestItem(
                item.getSlot(),
                item.getSemanticKey(),
                item.getRegistryId(),
                item.getDisplayName(),
                item.getQuantity(),
                item.getModName(),
                item.getRarity(),
                item.getTooltip(),
                item.getAttributes(),
                item.getEnchantments(),
                item.getMetadata(),
                icon
        );
    }

    /**
     * Wire shape matching {@code KitManifestNormalizer::normalizeItem} (including null icon.reference).
     */
    static JsonObject toCanonicalItem(ManifestItem item) {
        JsonObject json = new JsonObject();
        json.addProperty("slot", item.getSlot());
        json.addProperty("semantic_key", item.getSemanticKey());
        json.addProperty("registry_id", item.getRegistryId());
        json.addProperty("display_name", item.getDisplayName());
        Number quantity = item.getQuantity();
        if (quantity instanceof Double || quantity instanceof Float) {
            double value = quantity.doubleValue();
            if (value == Math.rint(value)) {
                json.addProperty("quantity", (int) value);
            } else {
                json.addProperty("quantity", value);
            }
        } else {
            json.addProperty("quantity", quantity.intValue());
        }
        if (item.getModName() != null) {
            json.addProperty("mod_name", item.getModName());
        }
        json.addProperty("rarity", item.getRarity());

        JsonArray tooltip = new JsonArray();
        for (String line : item.getTooltip()) {
            tooltip.add(line);
        }
        json.add("tooltip", tooltip);

        JsonArray attributes = new JsonArray();
        for (NamedAttribute attribute : item.getAttributes()) {
            JsonObject row = new JsonObject();
            row.addProperty("name", attribute.getName());
            row.addProperty("value", attribute.getValue());
            attributes.add(row);
        }
        json.add("attributes", attributes);

        JsonArray enchantments = new JsonArray();
        for (EnchantmentRef enchantment : item.getEnchantments()) {
            JsonObject row = new JsonObject();
            row.addProperty("name", enchantment.getName());
            row.addProperty("level", enchantment.getLevel());
            enchantments.add(row);
        }
        json.add("enchantments", enchantments);

        JsonObject metadata = new JsonObject();
        for (Map.Entry<String, Object> entry : item.getMetadata().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) {
                metadata.add(entry.getKey(), new JsonPrimitive((Number) value));
            } else if (value instanceof Boolean) {
                metadata.add(entry.getKey(), new JsonPrimitive((Boolean) value));
            } else if (value != null) {
                metadata.addProperty(entry.getKey(), String.valueOf(value));
            }
        }
        json.add("metadata", metadata);

        ItemIconRef icon = item.getIcon() == null ? ItemIconRef.none() : item.getIcon();
        JsonObject iconJson = new JsonObject();
        iconJson.addProperty("type", icon.getType());
        if (icon.getReference() == null) {
            iconJson.add("reference", JsonNull.INSTANCE);
        } else {
            iconJson.addProperty("reference", icon.getReference());
        }
        json.add("icon", iconJson);
        return json;
    }
}
