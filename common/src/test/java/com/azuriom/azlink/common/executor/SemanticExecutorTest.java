package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.ledger.JsonOperationLedger;
import com.azuriom.azlink.common.executor.model.ExecutionResult;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.OperationType;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.executor.parse.OperationParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticExecutorTest {

    @TempDir
    Path tempDir;

    private SemanticExecutor executor;
    private JsonOperationLedger operationLedger;
    private EntitlementVersionLedger entitlementLedger;
    private TestLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        this.logger = new TestLogger();
        this.operationLedger = new JsonOperationLedger(this.tempDir.resolve("ops.json"));
        this.entitlementLedger = new EntitlementVersionLedger(this.tempDir.resolve("versions.json"));
        this.executor = new SemanticExecutor(OperationFixtures.configWithFly(), this.operationLedger,
                this.entitlementLedger, this.logger);
    }

    @Test
    void operationValidationRejectsMissingFields() {
        OperationParser parser = new OperationParser(OperationFixtures.configWithFly());
        ShopOperation op = new ShopOperation("op-1", "claim", OperationType.PRIVILEGE_RECONCILE.toWire(),
                OperationFixtures.HASH_A, null);
        assertEquals(OperationResultCode.FAILED, parser.validate(op).getResultCode());
    }

    @Test
    void acceptAllCapabilitiesDoesNotRejectUnknownKeys() {
        ExecutorConfig config = OperationFixtures.configWithFly();
        config.setAcceptAllCapabilities(true);
        SemanticExecutor open = new SemanticExecutor(config, this.operationLedger, this.entitlementLedger, this.logger);
        JsonObject payload = OperationFixtures.basePrivilegePayload(1, true);
        payload.getAsJsonObject("capabilities").addProperty("claim_chunks", true);
        ShopOperation op = new ShopOperation("19191919-1919-1919-1919-191919191910",
                "20202020-2020-2020-2020-202020202021",
                OperationType.PRIVILEGE_RECONCILE.toWire(), OperationFixtures.HASH_A, payload);

        assertEquals(OperationResultCode.SUCCEEDED, open.process(op).getResult().getResultCode());
    }

    @Test
    void unsupportedCapabilityAck() {
        JsonObject payload = OperationFixtures.basePrivilegePayload(1, true);
        JsonObject caps = new JsonObject();
        caps.addProperty("luckperms", true);
        payload.add("capabilities", caps);
        ShopOperation op = new ShopOperation("19191919-1919-1919-1919-191919191919",
                "20202020-2020-2020-2020-202020202020",
                OperationType.PRIVILEGE_RECONCILE.toWire(), OperationFixtures.HASH_A, payload);

        SemanticExecutor.ProcessedOperation processed = this.executor.process(op);
        assertEquals(OperationResultCode.UNSUPPORTED, processed.getResult().getResultCode());
        assertEquals("unsupported", processed.getAckRequest().getStatus());
        assertTrue(processed.getResult().getMessage().contains("luckperms"));
    }

    @Test
    void localLedgerWriteAndAlreadyAppliedReplay() {
        ShopOperation op = OperationFixtures.privilegeReconcile(
                "22222222-2222-2222-2222-222222222222",
                "33333333-3333-3333-3333-333333333333",
                OperationFixtures.HASH_A, 2);
        assertEquals(OperationResultCode.SUCCEEDED, this.executor.process(op).getResult().getResultCode());
        assertEquals(1, this.executor.getMockApplyCount());

        ShopOperation replay = OperationFixtures.privilegeReconcile(
                "22222222-2222-2222-2222-222222222222",
                "44444444-4444-4444-4444-444444444444",
                OperationFixtures.HASH_A, 2);
        SemanticExecutor.ProcessedOperation second = this.executor.process(replay);
        assertEquals(OperationResultCode.ALREADY_APPLIED, second.getResult().getResultCode());
        assertEquals("already_applied", second.getAckRequest().getStatus());
        assertEquals(1, this.executor.getMockApplyCount());
        assertEquals("44444444-4444-4444-4444-444444444444", second.getAckRequest().getClaimToken());
        assertFalse(second.getAckRequest().getExecutorVersion().isEmpty());
    }

    @Test
    void hashMismatchIntegrityErrorMapsToFailedStatus() {
        ShopOperation first = OperationFixtures.privilegeReconcile(
                "55555555-5555-5555-5555-555555555555",
                "66666666-6666-6666-6666-666666666666",
                OperationFixtures.HASH_A, 1);
        this.executor.process(first);

        ShopOperation mismatch = OperationFixtures.privilegeReconcile(
                "55555555-5555-5555-5555-555555555555",
                "77777777-7777-7777-7777-777777777777",
                OperationFixtures.HASH_B, 1);
        SemanticExecutor.ProcessedOperation result = this.executor.process(mismatch);
        assertEquals(OperationResultCode.INTEGRITY_ERROR, result.getResult().getResultCode());
        assertEquals("failed", result.getAckRequest().getStatus());
        assertEquals("integrity_error", result.getAckRequest().getResultCode());
        assertEquals(1, this.executor.getMockApplyCount());
    }

    @Test
    void staleEntitlementVersion() throws Exception {
        this.entitlementLedger.putAppliedVersion(OperationFixtures.PLAYER, 5);
        ShopOperation op = OperationFixtures.privilegeReconcile(
                "88888888-8888-8888-8888-888888888888",
                "99999999-9999-9999-9999-999999999999",
                OperationFixtures.HASH_A, 3);
        SemanticExecutor.ProcessedOperation result = this.executor.process(op);
        assertEquals(OperationResultCode.STALE, result.getResult().getResultCode());
        assertEquals("stale", result.getAckRequest().getStatus());
        assertEquals(5L, result.getResult().getAppliedEntitlementVersion());
        assertEquals(0, this.executor.getMockApplyCount());
    }

    @Test
    void newerEntitlementVersionApplies() throws Exception {
        this.entitlementLedger.putAppliedVersion(OperationFixtures.PLAYER, 2);
        ShopOperation op = OperationFixtures.privilegeReconcile(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                OperationFixtures.HASH_A, 4);
        assertEquals(OperationResultCode.SUCCEEDED, this.executor.process(op).getResult().getResultCode());
        assertEquals(4L, this.entitlementLedger.getAppliedVersion(OperationFixtures.PLAYER).get());
    }

    @Test
    void revokeStaleProtection() throws Exception {
        this.entitlementLedger.putAppliedVersion(OperationFixtures.PLAYER, 10);
        ShopOperation revoke = OperationFixtures.privilegeRevoke(
                "cccccccc-cccc-cccc-cccc-cccccccccccc",
                "dddddddd-dddd-dddd-dddd-dddddddddddd",
                OperationFixtures.HASH_A, 4);
        assertEquals(OperationResultCode.STALE, this.executor.process(revoke).getResult().getResultCode());
        assertEquals(0, this.executor.getMockApplyCount());
    }

    @Test
    void restartPersistenceAlreadyApplied() throws Exception {
        ShopOperation op = OperationFixtures.kitRedeem(
                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                "ffffffff-ffff-ffff-ffff-ffffffffffff",
                OperationFixtures.HASH_A, 42);
        assertEquals(OperationResultCode.SUCCEEDED, this.executor.process(op).getResult().getResultCode());

        JsonOperationLedger reloadedOps = new JsonOperationLedger(this.tempDir.resolve("ops.json"));
        EntitlementVersionLedger reloadedVersions = new EntitlementVersionLedger(this.tempDir.resolve("versions.json"));
        SemanticExecutor restarted = new SemanticExecutor(OperationFixtures.configWithFly(), reloadedOps,
                reloadedVersions, this.logger);

        ShopOperation replay = OperationFixtures.kitRedeem(
                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                "12121212-1212-1212-1212-121212121212",
                OperationFixtures.HASH_A, 42);
        SemanticExecutor.ProcessedOperation result = restarted.process(replay);
        assertEquals(OperationResultCode.ALREADY_APPLIED, result.getResult().getResultCode());
        assertEquals("already_applied", result.getAckRequest().getStatus());
        assertEquals(0, restarted.getMockApplyCount());
    }

    @Test
    void leaseReclaimReplayUsesNewClaimToken() {
        ShopOperation first = OperationFixtures.kitRedeem(
                "13131313-1313-1313-1313-131313131313",
                "14141414-1414-1414-1414-141414141414",
                OperationFixtures.HASH_A, 9);
        this.executor.process(first);

        ShopOperation reclaim = OperationFixtures.kitRedeem(
                "13131313-1313-1313-1313-131313131313",
                "15151515-1515-1515-1515-151515151515",
                OperationFixtures.HASH_A, 9);
        SemanticExecutor.ProcessedOperation processed = this.executor.process(reclaim);
        assertEquals("already_applied", processed.getAckRequest().getStatus());
        assertEquals("15151515-1515-1515-1515-151515151515", processed.getAckRequest().getClaimToken());
        assertEquals(1, this.executor.getMockApplyCount());
    }

    @Test
    void retryableAndUncertainAckBuilders() {
        ShopOperation op = OperationFixtures.kitRedeem(
                "16161616-1616-1616-1616-161616161616",
                "17171717-1717-1717-1717-171717171717",
                OperationFixtures.HASH_A, 1);
        assertEquals("retryable_failed", this.executor.retryableAck(op, "network").getStatus());
        assertEquals("uncertain", this.executor.uncertainAck(op, "unknown").getStatus());
    }

    @Test
    void observabilityDoesNotLogToken() {
        ShopOperation op = OperationFixtures.privilegeReconcile(
                "18181818-1818-1818-1818-181818181818",
                "SECRET_CLAIM_SHOULD_NOT_APPEAR",
                OperationFixtures.HASH_A, 1);
        // claim token format not uuid — still shouldn't dump as "token="
        this.executor.process(op);
        for (String line : this.logger.infos) {
            assertFalse(line.contains("SECRET_CLAIM"));
            assertFalse(line.toLowerCase().contains("azuriom-link-token"));
        }
    }
}
