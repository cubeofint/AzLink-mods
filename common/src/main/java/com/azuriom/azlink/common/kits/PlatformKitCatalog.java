package com.azuriom.azlink.common.kits;

import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import java.util.List;
import java.util.UUID;

/** Version-specific access; all native game objects stay in the platform module. */
public interface PlatformKitCatalog {
    List<String> names();
    Definition read(String name);
    int credit(UUID player, String key, int quantity, String receipt);

    final class Definition {
        public final String name;
        public final List<ServerKitItem> items;
        public final Integer cooldownSeconds;

        public Definition(String name, List<ServerKitItem> items, Integer cooldownSeconds) {
            this.name = name;
            this.items = items;
            this.cooldownSeconds = cooldownSeconds;
        }
    }
}
