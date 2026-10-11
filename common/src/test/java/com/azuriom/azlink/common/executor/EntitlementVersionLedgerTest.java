package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.ledger.GrantedNodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementVersionLedgerTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @TempDir
    Path tempDir;

    @Test
    void storesGrantAndReloadsExactNodes() throws Exception {
        Path file = this.tempDir.resolve("versions.json");
        EntitlementVersionLedger ledger = new EntitlementVersionLedger(file);
        Instant expires = Instant.parse("2027-01-01T00:00:00Z");
        ledger.putGrant(PLAYER, new GrantedNodes(
                "vip", 3, "vip",
                Arrays.asList("cointcore.bonus_claim_chunks=25"),
                Arrays.asList("azlink.capability.fly"),
                expires, Instant.parse("2026-10-11T00:00:00Z")));

        EntitlementVersionLedger reloaded = new EntitlementVersionLedger(file);
        GrantedNodes grant = reloaded.getGrant(PLAYER, "vip").get();
        assertEquals(3L, grant.getVersion());
        assertEquals("vip", grant.getGroup());
        assertEquals(Collections.singletonList("cointcore.bonus_claim_chunks=25"), grant.getMeta());
        assertEquals(Collections.singletonList("azlink.capability.fly"), grant.getPermissions());
        assertEquals(expires, grant.getExpiresAt());
        assertEquals(3L, reloaded.getGrantVersion(PLAYER, "vip").get().longValue());
    }

    @Test
    void replaceGrantKeepsLatestVersionPerKey() throws Exception {
        EntitlementVersionLedger ledger = new EntitlementVersionLedger(this.tempDir.resolve("versions.json"));
        ledger.putGrant(PLAYER, GrantedNodes.versionOnly("vip", 2, "vip", Instant.parse("2026-12-01T00:00:00Z")));
        ledger.putGrant(PLAYER, GrantedNodes.versionOnly("vip", 5, "vip", Instant.parse("2027-06-01T00:00:00Z")));

        GrantedNodes grant = ledger.getGrant(PLAYER, "vip").get();
        assertEquals(5L, grant.getVersion());
        assertEquals(Instant.parse("2027-06-01T00:00:00Z"), grant.getExpiresAt());
    }

    @Test
    void removeGrantIsIdempotent() throws Exception {
        EntitlementVersionLedger ledger = new EntitlementVersionLedger(this.tempDir.resolve("versions.json"));
        ledger.putGrant(PLAYER, GrantedNodes.versionOnly("vip", 1, "vip", null));
        ledger.removeGrant(PLAYER, "vip");
        ledger.removeGrant(PLAYER, "vip");
        assertFalse(ledger.getGrant(PLAYER, "vip").isPresent());
    }

    @Test
    void legacyPlayerVersionFileStillLoads() throws Exception {
        Path file = this.tempDir.resolve("legacy.json");
        Files.write(file, ("[{\"player_uuid\":\"" + PLAYER + "\",\"version\":4,"
                + "\"updated_at\":\"2026-10-01T00:00:00Z\"}]").getBytes());
        EntitlementVersionLedger ledger = new EntitlementVersionLedger(file);
        assertEquals(4L, ledger.getAppliedVersion(PLAYER).get().longValue());
        assertFalse(ledger.getGrant(PLAYER, "vip").isPresent());
    }

    @Test
    void playerVersionAndGrantKeysAreIndependent() throws Exception {
        EntitlementVersionLedger ledger = new EntitlementVersionLedger(this.tempDir.resolve("versions.json"));
        ledger.putAppliedVersion(PLAYER, 9);
        ledger.putGrant(PLAYER, GrantedNodes.versionOnly("mvp", 2, "mvp", null));
        assertEquals(9L, ledger.getAppliedVersion(PLAYER).get().longValue());
        assertEquals(2L, ledger.getGrantVersion(PLAYER, "mvp").get().longValue());
        assertTrue(!ledger.getGrantVersion(PLAYER, "vip").isPresent());
    }
}
