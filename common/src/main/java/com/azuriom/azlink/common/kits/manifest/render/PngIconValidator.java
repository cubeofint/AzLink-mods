package com.azuriom.azlink.common.kits.manifest.render;

import com.azuriom.azlink.common.kits.manifest.icon.IconContentAddressing;

/**
 * Server-side PNG validation for client-assisted icons (64x64 PNG v1).
 */
public final class PngIconValidator {

    private PngIconValidator() {
    }

    public static void validateIconPng(byte[] bytes, int expectedSize) {
        IconContentAddressing.validatePng(bytes);
        if (bytes.length < 24) {
            throw new IllegalArgumentException("PNG too small for IHDR");
        }
        // IHDR starts at offset 8 (sig) + 4 (len) + 4 ("IHDR") = 16
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width != expectedSize || height != expectedSize) {
            throw new IllegalArgumentException("expected " + expectedSize + "x" + expectedSize
                    + " got " + width + "x" + height);
        }
    }

    /**
     * Authoritative hash: always recompute from bytes (ignore client-provided hash).
     */
    public static String authoritativeSha256(byte[] bytes) {
        return IconContentAddressing.sha256Hex(bytes);
    }

    public static void assertClientHashMatches(byte[] bytes, String clientSha256) {
        if (clientSha256 == null || clientSha256.trim().isEmpty()) {
            return; // client hash optional for diagnostics
        }
        String server = authoritativeSha256(bytes);
        if (!server.equalsIgnoreCase(clientSha256.trim())) {
            throw new IllegalArgumentException("client_hash_mismatch");
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }
}
