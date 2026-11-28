package com.azuriom.azlink.common.chunks;

public interface ChunkLoaderBackend {
    /** Return only after the game's idempotency receipt has been durably saved. */
    void credit(ChunkLoaderPurchase purchase);
}
