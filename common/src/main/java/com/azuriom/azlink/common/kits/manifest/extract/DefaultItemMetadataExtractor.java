package com.azuriom.azlink.common.kits.manifest.extract;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;
import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.common.kits.manifest.model.ItemIconRef;
import com.azuriom.azlink.common.kits.manifest.model.ManifestItem;
import com.azuriom.azlink.common.kits.manifest.model.NamedAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Validates and maps snapshots to site-canonical manifest items.
 */
public final class DefaultItemMetadataExtractor implements ItemMetadataExtractor {

    private static final Pattern SEMANTIC_KEY = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    private static final List<String> RARITIES = Arrays.asList(
            "common", "uncommon", "rare", "epic", "legendary");

    private final TooltipExtractor tooltipExtractor;

    public DefaultItemMetadataExtractor() {
        this(new DefaultTooltipExtractor());
    }

    public DefaultItemMetadataExtractor(TooltipExtractor tooltipExtractor) {
        this.tooltipExtractor = tooltipExtractor;
    }

    @Override
    public ManifestItem extract(ItemStackSnapshot snapshot, ItemIconRef icon) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot required");
        }
        int slot = snapshot.getSlot();
        if (slot < 0 || slot > 1000) {
            throw new IllegalArgumentException("slot out of range: " + slot);
        }

        String semanticKey = requireString(snapshot.getSemanticKey(), KitManifestLimits.MAX_SEMANTIC_KEY, "semantic_key");
        if (!SEMANTIC_KEY.matcher(semanticKey).matches()) {
            throw new IllegalArgumentException("invalid semantic_key: " + semanticKey);
        }
        String registryId = requireString(snapshot.getRegistryId(), KitManifestLimits.MAX_REGISTRY_ID, "registry_id");
        String displayName = requireString(snapshot.getDisplayName(), KitManifestLimits.MAX_DISPLAY_NAME, "display_name");

        Number quantity = snapshot.getQuantity() == null ? 1 : snapshot.getQuantity();
        if (quantity.doubleValue() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (quantity.doubleValue() == Math.rint(quantity.doubleValue())) {
            quantity = quantity.intValue();
        }

        String rarity = snapshot.getRarity() == null ? "common" : snapshot.getRarity().trim().toLowerCase(Locale.ROOT);
        if (!RARITIES.contains(rarity)) {
            throw new IllegalArgumentException("invalid rarity: " + rarity);
        }

        String modName = optionalString(snapshot.getModName(), KitManifestLimits.MAX_STRING);

        List<NamedAttribute> attributes = new ArrayList<NamedAttribute>();
        if (snapshot.getAttributes() != null) {
            for (NamedAttribute attribute : snapshot.getAttributes()) {
                if (attributes.size() >= KitManifestLimits.MAX_ATTRIBUTES) {
                    break;
                }
                if (attribute == null) {
                    continue;
                }
                String name = optionalString(attribute.getName(), 100);
                String value = optionalString(attribute.getValue(), KitManifestLimits.MAX_STRING);
                if (name != null && value != null) {
                    attributes.add(new NamedAttribute(name, value));
                }
            }
        }

        List<EnchantmentRef> enchantments = new ArrayList<EnchantmentRef>();
        if (snapshot.getEnchantments() != null) {
            for (EnchantmentRef enchantment : snapshot.getEnchantments()) {
                if (enchantments.size() >= KitManifestLimits.MAX_ENCHANTMENTS) {
                    break;
                }
                if (enchantment == null) {
                    continue;
                }
                String name = optionalString(enchantment.getName(), 100);
                if (name == null || enchantment.getLevel() < 1) {
                    continue;
                }
                enchantments.add(new EnchantmentRef(name, enchantment.getLevel()));
            }
        }

        TreeMap<String, Object> metadata = new TreeMap<String, Object>();
        if (snapshot.getMetadata() != null) {
            int count = 0;
            for (Map.Entry<String, Object> entry : snapshot.getMetadata().entrySet()) {
                if (count >= KitManifestLimits.MAX_METADATA_KEYS) {
                    break;
                }
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                    continue;
                }
                if (value instanceof String) {
                    value = optionalString((String) value, KitManifestLimits.MAX_STRING);
                    if (value == null) {
                        continue;
                    }
                }
                metadata.put(key, value);
                count++;
            }
        }

        return new ManifestItem(
                slot,
                semanticKey,
                registryId,
                displayName,
                quantity,
                modName,
                rarity,
                this.tooltipExtractor.extract(snapshot),
                attributes,
                enchantments,
                new LinkedHashMap<String, Object>(metadata),
                icon == null ? ItemIconRef.none() : icon
        );
    }

    private static String requireString(String value, int max, String field) {
        String text = optionalString(value, max);
        if (text == null) {
            throw new IllegalArgumentException(field + " required");
        }
        return text;
    }

    private static String optionalString(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            return text.substring(0, max);
        }
        return text;
    }
}
