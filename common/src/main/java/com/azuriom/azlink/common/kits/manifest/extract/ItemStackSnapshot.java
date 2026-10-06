package com.azuriom.azlink.common.kits.manifest.extract;

import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.common.kits.manifest.model.NamedAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-agnostic extraction result for one ItemStack.
 * Platform modules fill this from native ItemStack — no Minecraft types in common.
 */
public final class ItemStackSnapshot {

    private final int slot;
    private final String semanticKey;
    private final String registryId;
    private final String displayName;
    private final Number quantity;
    private final String modName;
    private final String rarity;
    private final List<String> tooltipLines;
    private final List<NamedAttribute> attributes;
    private final List<EnchantmentRef> enchantments;
    private final Map<String, Object> metadata;
    private final byte[] iconPng;

    private ItemStackSnapshot(Builder builder) {
        this.slot = builder.slot;
        this.semanticKey = builder.semanticKey;
        this.registryId = builder.registryId;
        this.displayName = builder.displayName;
        this.quantity = builder.quantity;
        this.modName = builder.modName;
        this.rarity = builder.rarity;
        this.tooltipLines = Collections.unmodifiableList(new ArrayList<String>(builder.tooltipLines));
        this.attributes = Collections.unmodifiableList(new ArrayList<NamedAttribute>(builder.attributes));
        this.enchantments = Collections.unmodifiableList(new ArrayList<EnchantmentRef>(builder.enchantments));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(builder.metadata));
        this.iconPng = builder.iconPng == null ? null : builder.iconPng.clone();
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

    public List<String> getTooltipLines() {
        return this.tooltipLines;
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

    public byte[] getIconPng() {
        return this.iconPng == null ? null : this.iconPng.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int slot;
        private String semanticKey;
        private String registryId;
        private String displayName;
        private Number quantity = 1;
        private String modName;
        private String rarity = "common";
        private final List<String> tooltipLines = new ArrayList<String>();
        private final List<NamedAttribute> attributes = new ArrayList<NamedAttribute>();
        private final List<EnchantmentRef> enchantments = new ArrayList<EnchantmentRef>();
        private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        private byte[] iconPng;

        public Builder slot(int slot) {
            this.slot = slot;
            return this;
        }

        public Builder semanticKey(String semanticKey) {
            this.semanticKey = semanticKey;
            return this;
        }

        public Builder registryId(String registryId) {
            this.registryId = registryId;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder quantity(Number quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder modName(String modName) {
            this.modName = modName;
            return this;
        }

        public Builder rarity(String rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder tooltipLines(List<String> lines) {
            this.tooltipLines.clear();
            if (lines != null) {
                this.tooltipLines.addAll(lines);
            }
            return this;
        }

        public Builder addTooltipLine(String line) {
            this.tooltipLines.add(line);
            return this;
        }

        public Builder attributes(List<NamedAttribute> attributes) {
            this.attributes.clear();
            if (attributes != null) {
                this.attributes.addAll(attributes);
            }
            return this;
        }

        public Builder enchantments(List<EnchantmentRef> enchantments) {
            this.enchantments.clear();
            if (enchantments != null) {
                this.enchantments.addAll(enchantments);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Builder putMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder iconPng(byte[] iconPng) {
            this.iconPng = iconPng;
            return this;
        }

        public ItemStackSnapshot build() {
            return new ItemStackSnapshot(this);
        }
    }
}
