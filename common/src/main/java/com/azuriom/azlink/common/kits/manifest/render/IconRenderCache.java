package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory icon cache: render_key → authoritative icon SHA-256.
 * Does not store secrets.
 */
public final class IconRenderCache {

    private final Map<String, String> renderKeyToIconHash = new ConcurrentHashMap<String, String>();

    public Optional<String> findIconHash(String renderKey) {
        if (renderKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.renderKeyToIconHash.get(renderKey));
    }

    public void put(String renderKey, String iconHash) {
        if (renderKey == null || iconHash == null) {
            return;
        }
        this.renderKeyToIconHash.put(renderKey, iconHash.toLowerCase());
    }

    public boolean contains(String renderKey) {
        return renderKey != null && this.renderKeyToIconHash.containsKey(renderKey);
    }

    public int size() {
        return this.renderKeyToIconHash.size();
    }

    public void clear() {
        this.renderKeyToIconHash.clear();
    }
}
