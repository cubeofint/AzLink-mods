package com.azuriom.azlink.common.kits.manifest.render;

import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemIconRenderSecurityTest {

    private ClientCapabilityRegistry registry;
    private IconRenderCache cache;
    private FakeTransport transport;
    private ItemIconRenderCoordinator coordinator;
    private UUID player;

    @BeforeEach
    void setUp() {
        this.registry = new ClientCapabilityRegistry();
        this.cache = new IconRenderCache();
        this.transport = new FakeTransport();
        this.player = UUID.randomUUID();
        this.coordinator = new ItemIconRenderCoordinator(
                this.registry,
                new PreferredPlayerRenderWorkerSelector(this.registry, id -> id.equals(this.player)),
                this.cache,
                this.transport,
                new QuietLogger()
        );
    }

    @Test
    void renderRequestAndResponseContainNoTokenFields() {
        ItemIconRenderRequest request = new ItemIconRenderRequest(
                UUID.randomUUID(), ItemIconRenderProtocol.VERSION, 64,
                Collections.singletonList(new RenderItemPayload("abc", 0, new byte[]{1, 2, 3})));
        String req = request.getRequestId() + request.getItems().get(0).getRenderKey();
        assertFalse(req.toLowerCase().contains("token"));
        assertFalse(req.toLowerCase().contains("azuriom"));

        ItemIconRenderResponse response = new ItemIconRenderResponse(
                request.getRequestId(), 1, "1.0",
                Collections.singletonList(IconRenderResult.failed("abc", "x")));
        assertFalse(response.getRendererVersion().contains("http"));
    }

    @Test
    void unsupportedCapabilityNotSelected() {
        this.registry.register(this.player, new ClientCapabilities(1, false, "x", 64, "png"));
        assertFalse(new PreferredPlayerRenderWorkerSelector(this.registry, id -> true)
                .select(this.player).isPresent());
    }

    @Test
    void compatibleClientSelected() throws Exception {
        this.registry.register(this.player, ClientCapabilities.v1("test"));
        assertTrue(new PreferredPlayerRenderWorkerSelector(this.registry, id -> true)
                .select(this.player).isPresent());
    }

    @Test
    void protocolMismatchRejected() {
        assertFalse(new ClientCapabilities(99, true, "x", 64, "png").isCompatibleWithServer());
    }

    @Test
    void unknownRequestIdIgnored() {
        this.coordinator.onResponse(this.player, new ItemIconRenderResponse(
                UUID.randomUUID(), 1, "t", Collections.<IconRenderResult>emptyList()));
        // no throw
    }

    @Test
    void unknownRenderKeyRejectedAndValidCached() throws Exception {
        this.registry.register(this.player, ClientCapabilities.v1("t"));
        byte[] stack = new byte[]{9, 9, 9};
        String key = RenderKey.fromItemStackBytes(0, stack).getValue();
        RenderItemPayload payload = new RenderItemPayload(key, 0, stack);

        java.util.concurrent.CompletableFuture<ItemIconRenderCoordinator.BatchResult> future =
                this.coordinator.requestIcons(this.player, Collections.singletonList(payload));

        ItemIconRenderRequest sent = this.transport.lastRequest;
        byte[] png = png64();
        String hash = PngIconValidator.authoritativeSha256(png);

        this.coordinator.onResponse(this.player, new ItemIconRenderResponse(
                sent.getRequestId(), 1, "t",
                Arrays.asList(
                        IconRenderResult.rendered("deadbeef", png, hash), // unknown
                        IconRenderResult.rendered(key, png, hash)
                )));

        ItemIconRenderCoordinator.BatchResult result = future.get(2, TimeUnit.SECONDS);
        assertTrue(result.getRendered().containsKey(key));
        assertFalse(result.getRendered().containsKey("deadbeef"));
        assertEquals(hash, this.cache.findIconHash(key).get());
    }

    @Test
    void clientHashMismatchRejected() throws Exception {
        this.registry.register(this.player, ClientCapabilities.v1("t"));
        byte[] stack = new byte[]{1};
        String key = RenderKey.fromItemStackBytes(1, stack).getValue();
        java.util.concurrent.CompletableFuture<ItemIconRenderCoordinator.BatchResult> future =
                this.coordinator.requestIcons(this.player,
                        Collections.singletonList(new RenderItemPayload(key, 1, stack)));
        byte[] png = png64();
        this.coordinator.onResponse(this.player, new ItemIconRenderResponse(
                this.transport.lastRequest.getRequestId(), 1, "t",
                Collections.singletonList(IconRenderResult.rendered(key, png, "0".repeat(64)))));
        ItemIconRenderCoordinator.BatchResult result = future.get(2, TimeUnit.SECONDS);
        assertTrue(result.getFailedKeys().contains(key));
    }

    @Test
    void wrongDimensionsRejected() {
        byte[] tiny = minimalPng1x1();
        assertThrows(IllegalArgumentException.class,
                () -> PngIconValidator.validateIconPng(tiny, 64));
    }

    @Test
    void cacheSkipsSecondRenderRequest() throws Exception {
        this.registry.register(this.player, ClientCapabilities.v1("t"));
        byte[] stack = new byte[]{7};
        String key = RenderKey.fromItemStackBytes(0, stack).getValue();
        this.cache.put(key, "a".repeat(64));

        ItemIconRenderCoordinator.BatchResult result = this.coordinator.requestIcons(
                this.player,
                Collections.singletonList(new RenderItemPayload(key, 0, stack))
        ).get(1, TimeUnit.SECONDS);

        assertTrue(result.getRendered().containsKey(key));
        assertEquals(0, this.transport.sendCount);
    }

    @Test
    void rendererUnavailableWithoutPreferredCapableClient() throws Exception {
        ItemIconRenderCoordinator.BatchResult result = this.coordinator.requestIcons(
                this.player,
                Collections.singletonList(new RenderItemPayload("k", 0, new byte[]{1}))
        ).get(1, TimeUnit.SECONDS);
        assertTrue(result.isRendererUnavailable());
    }

    @Test
    void clientSafeConfigHasNoSecrets() {
        com.azuriom.azlink.common.config.ClientSafeConfig cfg =
                new com.azuriom.azlink.common.config.ClientSafeConfig();
        String text = cfg.toString().toLowerCase();
        assertFalse(text.contains("token"));
        assertFalse(text.contains("siteurl"));
        assertFalse(text.contains("http"));
    }

    @Test
    void pluginConfigToStringRedactsToken() {
        com.azuriom.azlink.common.config.PluginConfig cfg =
                new com.azuriom.azlink.common.config.PluginConfig("supersecrettokenvalue", "https://example.test");
        assertFalse(cfg.toString().contains("supersecrettokenvalue"));
        assertTrue(cfg.toString().contains("redacted"));
    }

    @Test
    void largeManifestUsesSequentialExistingSizePackets() throws Exception {
        this.registry.register(this.player, ClientCapabilities.v1("t"));
        List<RenderItemPayload> items = payloads(129);
        java.util.concurrent.CompletableFuture<ItemIconRenderCoordinator.BatchResult> future =
                this.coordinator.requestIconsInBatches(this.player, items);
        byte[] png = png64();
        String hash = PngIconValidator.authoritativeSha256(png);
        int total = 0;
        for (int batch = 0; batch < 3; batch++) {
            assertFalse(future.isDone());
            assertEquals(batch + 1, this.transport.sendCount);
            ItemIconRenderRequest sent = this.transport.lastRequest;
            assertTrue(sent.getItems().size() <= ItemIconRenderProtocol.MAX_ITEMS_PER_REQUEST);
            List<IconRenderResult> response = new ArrayList<IconRenderResult>();
            for (RenderItemPayload item : sent.getItems()) {
                response.add(IconRenderResult.rendered(item.getRenderKey(), png, hash));
            }
            total += sent.getItems().size();
            this.coordinator.onResponse(this.player, new ItemIconRenderResponse(sent.getRequestId(), 1, "t", response));
        }
        ItemIconRenderCoordinator.BatchResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals(129, total);
        assertEquals(129, result.getRendered().size());
        assertTrue(result.getFailedKeys().isEmpty());
        assertNull(result.getError());
    }

    @Test
    void oversizedSinglePacketStillRejected() throws Exception {
        ItemIconRenderCoordinator.BatchResult result = this.coordinator.requestIcons(this.player, payloads(65))
                .get(1, TimeUnit.SECONDS);
        assertEquals("batch_too_large", result.getError());
        assertEquals(0, this.transport.sendCount);
        result = this.coordinator.requestIconsInBatches(this.player, payloads(KitManifestLimits.MAX_ITEMS + 1))
                .get(1, TimeUnit.SECONDS);
        assertEquals("batch_too_large", result.getError());
        assertEquals(0, this.transport.sendCount);
    }

    @Test
    void largeManifestRetainsCacheAndMissingRendererResult() throws Exception {
        List<RenderItemPayload> items = payloads(65);
        this.cache.put(items.get(0).getRenderKey(), "a".repeat(64));
        ItemIconRenderCoordinator.BatchResult result = this.coordinator.requestIconsInBatches(this.player, items)
                .get(1, TimeUnit.SECONDS);
        assertEquals(1, result.getRendered().size());
        assertEquals(64, result.getFailedKeys().size());
        assertTrue(result.isRendererUnavailable());
        assertEquals(0, this.transport.sendCount);
    }

    @Test
    void disconnectDuringLargeManifestCompletesRemainingBatches() throws Exception {
        this.registry.register(this.player, ClientCapabilities.v1("t"));
        java.util.concurrent.CompletableFuture<ItemIconRenderCoordinator.BatchResult> future =
                this.coordinator.requestIconsInBatches(this.player, payloads(129));
        this.coordinator.onDisconnect(this.player);
        ItemIconRenderCoordinator.BatchResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals("client_disconnect", result.getError());
        assertEquals(129, result.getFailedKeys().size());
        assertEquals(1, this.transport.sendCount);
    }

    private static List<RenderItemPayload> payloads(int count) {
        List<RenderItemPayload> items = new ArrayList<RenderItemPayload>();
        for (int slot = 0; slot < count; slot++) {
            byte[] bytes = new byte[]{(byte) slot, (byte) (slot >> 8)};
            items.add(new RenderItemPayload(RenderKey.fromItemStackBytes(slot, bytes).getValue(), slot, bytes));
        }
        return items;
    }

    private static byte[] png64() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] minimalPng1x1() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
    }

    private static final class FakeTransport implements ItemIconRenderTransport {
        private ItemIconRenderRequest lastRequest;
        private int sendCount;

        @Override
        public void sendRequest(UUID playerId, ItemIconRenderRequest request) {
            this.lastRequest = request;
            this.sendCount++;
        }

        @Override
        public void sendCapabilities(ClientCapabilities capabilities) {
        }
    }

    private static final class QuietLogger implements LoggerAdapter {
        @Override
        public void info(String message) {
        }

        @Override
        public void info(String message, Throwable throwable) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    }
}
