package com.azuriom.azlink.common.kits.manifest.render;

import java.util.UUID;

/**
 * Platform networking bridge for item-icon render packets.
 * Implementations live in server/client platform modules — never on HTTP to site.
 */
public interface ItemIconRenderTransport {

    void sendRequest(UUID playerId, ItemIconRenderRequest request);

    void sendCapabilities(ClientCapabilities capabilities);

    interface ServerHandler {
        void onCapabilities(UUID playerId, ClientCapabilities capabilities);

        void onResponse(UUID playerId, ItemIconRenderResponse response);

        void onDisconnect(UUID playerId);
    }

    interface ClientHandler {
        void onRequest(ItemIconRenderRequest request);
    }
}
