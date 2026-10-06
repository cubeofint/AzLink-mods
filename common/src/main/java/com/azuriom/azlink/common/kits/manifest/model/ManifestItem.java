package com.azuriom.azlink.common.kits.manifest.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One canonical kit inventory item (site {@code KitManifestNormalizer} shape).
 */
public final class ManifestItem {

    private final int slot;

    @SerializedName("semantic_key")
    private final String semanticKey;

    @SerializedName("registry_id")
    private final String registryId;

    @SerializedName("display_name")
    private final String displayName;

    private final Number quantity;

    @SerializedName("mod_name")
    private final String modName;

    private final String rarity;
    private final List<String> tooltip;
    private final List<NamedAttribute> attributes;
    private final List<EnchantmentRef> enchantments;
    private final Map<String, Object> metadata;
    private final ItemIconRef icon;

    public ManifestItem(int slot, String semanticKey, String registryId, String displayName,
                        Number quantity, String modName, String rarity,
                        List<String> tooltip, List<NamedAttribute> attributes,
                        List<EnchantmentRef> enchantments, Map<String, Object> metadata,
                        ItemIconRef icon) {
        this.slot = slot;
        this.semanticKey = semanticKey;
        this.registryId = registryId;
        this.displayName = displayName;
        this.quantity = quantity;
        this.modName = modName;
        this.rarity = rarity;
        this.tooltip = tooltip == null ? Collections.<String>emptyList() : tooltip;
        this.attributes = attributes == null ? Collections.<NamedAttribute>emptyList() : attributes;
        this.enchantments = enchantments == null ? Collections.<EnchantmentRef>emptyList() : enchantments;
        this.metadata = metadata == null ? Collections.<String, Object>emptyMap() : metadata;
        this.icon = icon == null ? ItemIconRef.none() : icon;
    }

    public int getSlot() {
        return this.slot;
    }

    public String getSemanticKey() {
        return this.semanticKey;
    }

    public String getRegistryId() {
        return this.registryId;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Number getQuantity() {
        return this.quantity;
    }

    public String getModName() {
        return this.modName;
    }

    public String getRarity() {
        return this.rarity;
    }

    public List<String> getTooltip() {
        return this.tooltip;
    }

    public List<NamedAttribute> getAttributes() {
        return this.attributes;
    }

    public List<EnchantmentRef> getEnchantments() {
        return this.enchantments;
    }

    public Map<String, Object> getMetadata() {
        return this.metadata;
    }

    public ItemIconRef getIcon() {
        return this.icon;
    }
}
