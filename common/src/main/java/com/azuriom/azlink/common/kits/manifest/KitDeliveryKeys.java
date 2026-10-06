package com.azuriom.azlink.common.kits.manifest;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizes FTB / in-game kit names to site {@code delivery_key}
 * ({@code ^[a-z0-9][a-z0-9._-]{0,63}$}).
 */
public final class KitDeliveryKeys {

    private static final Pattern SITE_KEY = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");

    private KitDeliveryKeys() {
    }

    public static Optional<String> normalize(String kitName) {
        if (kitName == null) {
            return Optional.empty();
        }
        String key = kitName.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || !SITE_KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        return Optional.of(key);
    }
}
