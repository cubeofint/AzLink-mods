package com.azuriom.azlink.common.kits.manifest.render;

import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic server-side key linking a PNG to a server ItemStack snapshot.
 * Built only on dedicated server — client must echo it unchanged.
 */
public final class RenderKey {

    private final String value;

    private RenderKey(String value) {
        this.value = value;
    }

    public static RenderKey of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("render_key required");
        }
        return new RenderKey(value.trim().toLowerCase());
    }

    /**
     * Hash of opaque ItemStack network bytes (+ slot) for stable identity.
     */
    public static RenderKey fromItemStackBytes(int slot, byte[] itemStackBytes) {
        if (itemStackBytes == null) {
            itemStackBytes = new byte[0];
        }
        return of(hashBytes(slot, itemStackBytes));
    }

    private static String hashBytes(int slot, byte[] itemStackBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((byte) (slot >>> 24));
            digest.update((byte) (slot >>> 16));
            digest.update((byte) (slot >>> 8));
            digest.update((byte) slot);
            digest.update(itemStackBytes);
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RenderKey)) {
            return false;
        }
        return Objects.equals(this.value, ((RenderKey) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
