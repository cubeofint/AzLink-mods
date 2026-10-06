package com.azuriom.azlink.common.kits.manifest;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KitDeliveryKeysTest {

    @Test
    void normalizesValidNames() {
        assertEquals(Optional.of("vip"), KitDeliveryKeys.normalize("VIP"));
        assertEquals(Optional.of("starter_kit"), KitDeliveryKeys.normalize("starter_kit"));
        assertEquals(Optional.of("a.b-c_1"), KitDeliveryKeys.normalize("A.B-c_1"));
    }

    @Test
    void rejectsInvalidNames() {
        assertFalse(KitDeliveryKeys.normalize("").isPresent());
        assertFalse(KitDeliveryKeys.normalize(null).isPresent());
        assertFalse(KitDeliveryKeys.normalize("_bad").isPresent());
        assertFalse(KitDeliveryKeys.normalize("has space").isPresent());
        assertFalse(KitDeliveryKeys.normalize("UPPER/slash").isPresent());
    }
}