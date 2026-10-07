package com.azuriom.azlink.common.executor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityKeysTest {

    @Test
    void luckPermsEmptyConfigAdvertisesPlainKeysNotStarOrMeta() {
        CapabilityKeys.Resolution resolved = CapabilityKeys.resolve(Collections.<String>emptyList(), true);

        assertTrue(resolved.isAcceptAll());
        assertEquals(CapabilityKeys.defaultAdvertised(), resolved.getAdvertised());
        assertTrue(resolved.getAdvertised().contains("fly"));
        assertTrue(resolved.getAdvertised().contains("claim_chunks"));
        for (String key : resolved.getAdvertised()) {
            assertTrue(CapabilityKeys.isWireSafe(key), key);
            assertFalse(key.contains(":"));
            assertFalse("*".equals(key));
        }
    }

    @Test
    void starAndMetaAreStrippedFromPollList() {
        CapabilityKeys.Resolution resolved = CapabilityKeys.resolve(
                Arrays.asList("*", "meta:cointcore.bonus_claim_chunks", "fly", "Not Valid"),
                false);

        assertTrue(resolved.isAcceptAll());
        assertEquals(Collections.singletonList("fly"), resolved.getAdvertised());
    }

    @Test
    void emptyWithoutLuckPermsStaysEmpty() {
        CapabilityKeys.Resolution resolved = CapabilityKeys.resolve(Collections.<String>emptyList(), false);

        assertFalse(resolved.isAcceptAll());
        assertTrue(resolved.getAdvertised().isEmpty());
    }

    @Test
    void configuredPlainKeysKept() {
        List<String> keys = Arrays.asList("fly", "claim_chunks", "cointcore.bonus_claim_chunks");
        CapabilityKeys.Resolution resolved = CapabilityKeys.resolve(keys, false);

        assertFalse(resolved.isAcceptAll());
        assertEquals(keys, resolved.getAdvertised());
    }
}
