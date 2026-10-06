package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.executor.model.AckRequest;
import com.azuriom.azlink.common.executor.model.OperationResultCode;
import com.azuriom.azlink.common.executor.model.OperationType;
import com.azuriom.azlink.common.executor.model.PollRequest;
import com.azuriom.azlink.common.executor.model.PollResponse;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.utils.VersionInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests against site-canonical shop-protocol-v1 JSON fixtures.
 */
class ShopProtocolContractTest {

    @Test
    void privilegeReconcileSerializesAsSnakeCase() {
        assertEquals("privilege_reconcile", OperationType.PRIVILEGE_RECONCILE.toWire());
        assertEquals("privilege_revoke", OperationType.PRIVILEGE_REVOKE.toWire());
        assertEquals("kit_redeem", OperationType.KIT_REDEEM.toWire());
    }

    @Test
    void snakeCaseDeserializesToEnum() {
        assertEquals(OperationType.PRIVILEGE_RECONCILE, OperationType.fromWire("privilege_reconcile"));
        assertEquals(OperationType.KIT_REDEEM, OperationType.fromWire("kit_redeem"));
        assertNull(OperationType.fromWire("PRIVILEGE_RECONCILE"));
    }

    @Test
    void pollRequestFixtureMatchesWire() throws Exception {
        JsonObject expected = readJson("fixtures/shop-protocol-v1/poll-request.json");
        PollRequest request = new PollRequest(
                expected.get("protocol_version").getAsInt(),
                expected.get("executor_version").getAsString(),
                java.util.Arrays.asList("privilege_reconcile", "privilege_revoke", "kit_redeem"),
                java.util.Collections.singletonList("fly"),
                expected.get("max_batch").getAsInt()
        );
        JsonObject actual = AzLinkPlugin.getGson().toJsonTree(request).getAsJsonObject();
        assertEquals(expected.get("protocol_version"), actual.get("protocol_version"));
        assertEquals(expected.get("supported_operation_types"), actual.get("supported_operation_types"));
        assertEquals(expected.get("supported_capabilities"), actual.get("supported_capabilities"));
        assertFalse(actual.has("server_id"));
    }

    @Test
    void pollResponseFixtureParsesTypeAndTopLevelProtocol() throws Exception {
        PollResponse response = AzLinkPlugin.getGson().fromJson(
                reader("fixtures/shop-protocol-v1/poll-response.json"), PollResponse.class);

        assertEquals(1, response.getProtocolVersion().intValue());
        assertEquals(1, response.getOperations().size());

        ShopOperation op = response.getOperations().get(0);
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", op.getOperationId());
        assertEquals("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", op.getClaimToken());
        assertEquals(OperationType.PRIVILEGE_RECONCILE, op.getOperationType());
        assertEquals("privilege_reconcile", op.getOperationTypeRaw());
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                op.getPayloadHash());
        assertEquals(64, op.getPayloadHash().length());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), op.getPlayerUuid());
        assertEquals("7", op.getEntitlementId());
        assertEquals(3L, op.getEntitlementVersion().longValue());
        assertEquals("vip", op.getSemanticKey());
        assertNotNull(op.getCapabilities());
        assertTrue(op.getCapabilities().has("fly"));
        assertEquals(Integer.valueOf(1), op.getAttemptNo());
    }

    @Test
    void ackSucceededSerializesRequiredFields() throws Exception {
        JsonObject expected = readJson("fixtures/shop-protocol-v1/ack-succeeded.json");
        AckRequest ack = new AckRequest(
                expected.get("operation_id").getAsString(),
                expected.get("claim_token").getAsString(),
                expected.get("payload_hash").getAsString(),
                OperationResultCode.SUCCEEDED,
                3L,
                VersionInfo.VERSION,
                null
        );
        JsonObject actual = AzLinkPlugin.getGson().toJsonTree(ack).getAsJsonObject();
        assertEquals("succeeded", actual.get("status").getAsString());
        assertEquals("applied", actual.get("result_code").getAsString());
        assertTrue(actual.has("executor_version"));
        assertFalse(actual.get("executor_version").getAsString().isEmpty());
        assertEquals(expected.get("operation_id"), actual.get("operation_id"));
        assertEquals(expected.get("claim_token"), actual.get("claim_token"));
        assertEquals(expected.get("payload_hash"), actual.get("payload_hash"));
    }

    @Test
    void ackAlreadyAppliedAndFailedFixtures() throws Exception {
        JsonObject already = readJson("fixtures/shop-protocol-v1/ack-already-applied.json");
        AckRequest replay = new AckRequest(
                already.get("operation_id").getAsString(),
                already.get("claim_token").getAsString(),
                already.get("payload_hash").getAsString(),
                OperationResultCode.ALREADY_APPLIED,
                3L,
                VersionInfo.VERSION,
                null
        );
        assertEquals("already_applied",
                AzLinkPlugin.getGson().toJsonTree(replay).getAsJsonObject().get("status").getAsString());

        JsonObject failed = readJson("fixtures/shop-protocol-v1/ack-failed.json");
        AckRequest integrity = new AckRequest(
                failed.get("operation_id").getAsString(),
                failed.get("claim_token").getAsString(),
                failed.get("payload_hash").getAsString(),
                OperationResultCode.INTEGRITY_ERROR,
                null,
                VersionInfo.VERSION,
                failed.get("message").getAsString()
        );
        JsonObject integrityJson = AzLinkPlugin.getGson().toJsonTree(integrity).getAsJsonObject();
        assertEquals("failed", integrityJson.get("status").getAsString());
        assertEquals("integrity_error", integrityJson.get("result_code").getAsString());
    }

    private static JsonObject readJson(String path) throws Exception {
        try (Reader reader = reader(path)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static Reader reader(String path) {
        return new InputStreamReader(
                ShopProtocolContractTest.class.getClassLoader().getResourceAsStream(path),
                StandardCharsets.UTF_8);
    }
}
