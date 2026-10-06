package com.azuriom.azlink.common.kits.manifest.icon;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Content-addressed PNG hashing (SHA-256 hex), matching site {@code KitManifestIconStorage}.
 */
public final class IconContentAddressing {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private IconContentAddressing() {
    }

    public static void validatePng(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("icon empty");
        }
        if (bytes.length > KitManifestLimits.MAX_ICON_BYTES) {
            throw new IllegalArgumentException("icon too large");
        }
        if (bytes.length < PNG_SIGNATURE.length) {
            throw new IllegalArgumentException("PNG required");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw new IllegalArgumentException("PNG required");
            }
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xff);
                (hex.length() > 1 ? result : result.append('0')).append(hex);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback via string hash util is wrong for binary — rethrow.
            throw new UnsupportedOperationException("SHA-256 unavailable", e);
        }
    }

    /** Local content-hash without upload (for offline tests). */
    public static String hashPng(byte[] bytes) {
        validatePng(bytes);
        return sha256Hex(bytes);
    }
}
