package com.azuriom.azlink.common.kits.manifest.render;

import com.azuriom.azlink.common.AzLinkPlatform;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;
import com.azuriom.azlink.common.kits.manifest.KitManifestSyncCoordinator;
import com.azuriom.azlink.common.kits.manifest.KitManifestSyncResult;
import com.azuriom.azlink.common.kits.manifest.ServerKitItem;
import com.azuriom.azlink.common.kits.manifest.client.KitManifestApiClient;
import com.azuriom.azlink.common.kits.manifest.extract.DefaultItemMetadataExtractor;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import com.azuriom.azlink.common.kits.manifest.model.*;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocalizedKitNameTest {
    private static final String RUSSIAN_NAME = "Жаропрочный машинный корпус";

    @Test
    void localizedResponseRoundTripsAndStillFitsBothForgePacketLimits() throws Exception {
        byte[] png = png64();
        String longName = String.join("", Collections.nCopies(KitManifestLimits.MAX_DISPLAY_NAME, "Я"));
        ItemIconRenderResponse response = new ItemIconRenderResponse(UUID.randomUUID(), 2, "test",
                Arrays.asList(IconRenderResult.rendered("key", png, PngIconValidator.authoritativeSha256(png), longName),
                        IconRenderResult.failed("failed", "no_model"), IconRenderResult.unsupported("old", "unsupported")));
        byte[] packet = ItemIconWireCodec.response(response);
        assertTrue(packet.length + 64 < 32767);
        ItemIconRenderResponse decoded = ItemIconWireCodec.response(packet);
        assertEquals(longName, decoded.getIcons().get(0).getDisplayName());
        assertEquals(IconRenderResult.Status.FAILED, decoded.getIcons().get(1).getStatus());
        assertEquals(IconRenderResult.Status.UNSUPPORTED, decoded.getIcons().get(2).getStatus());
        assertThrows(IllegalArgumentException.class, () -> ItemIconWireCodec.response(Arrays.copyOf(packet, packet.length - 1)));
        ClientCapabilities caps = ClientCapabilities.withLocalizedNames("test");
        assertEquals(caps, ItemIconWireCodec.capabilities(ItemIconWireCodec.capabilities(caps)));
        assertTrue(caps.isCompatibleWithServer());
    }

    @Test
    void oldWireFormatHasNoExtraNameField() {
        UUID request = UUID.randomUUID();
        IconRenderResult icon = IconRenderResult.rendered("key", new byte[]{1}, "hash", RUSSIAN_NAME);
        byte[] old = ItemIconWireCodec.response(new ItemIconRenderResponse(request, 1, "test", Collections.singletonList(icon)));
        byte[] unnamed = ItemIconWireCodec.response(new ItemIconRenderResponse(request, 1, "test",
                Collections.singletonList(IconRenderResult.rendered("key", new byte[]{1}, "hash"))));
        assertArrayEquals(unnamed, old);
        assertNull(ItemIconWireCodec.response(old).getIcons().get(0).getDisplayName());
        assertTrue(ClientCapabilities.v1("old").isCompatibleWithServer());
    }

    @Test
    void namesArePlainBoundedTextAndDoNotSplitUnicodeCharacters() {
        IconRenderResult result = IconRenderResult.rendered("key", new byte[]{1}, "hash", " §a" + RUSSIAN_NAME + "§r\n\u0000\u202e ");
        assertEquals(RUSSIAN_NAME, result.getDisplayName());
        assertNull(IconRenderResult.rendered("key", new byte[]{1}, "hash", " §r\n ").getDisplayName());
        String prefix = String.join("", Collections.nCopies(99, "Я"));
        assertEquals(prefix, IconRenderResult.rendered("key", new byte[]{1}, "hash", prefix + "\ud83d\udd28").getDisplayName());
    }

    @Test
    void cachedIconsStillRefreshNamesAfterChangingClientLanguage() throws Exception {
        Fixture f = new Fixture(2);
        f.cache.put(f.key, "a".repeat(64));
        CompletableFuture<ItemIconRenderCoordinator.BatchResult> first = f.render.requestIcons(f.player, f.payloads());
        assertEquals(2, f.transport.last.getProtocolVersion());
        f.reply(RUSSIAN_NAME);
        assertEquals(RUSSIAN_NAME, first.get(1, TimeUnit.SECONDS).getRendered().get(f.key).getDisplayName());
        CompletableFuture<ItemIconRenderCoordinator.BatchResult> second = f.render.requestIcons(f.player, f.payloads());
        assertFalse(second.isDone());
        f.reply("Heat Proof Machine Casing");
        assertEquals("Heat Proof Machine Casing", second.get(1, TimeUnit.SECONDS).getRendered().get(f.key).getDisplayName());
        assertEquals(2, f.transport.sent);
    }

    @Test
    void namesStillRequireTheSelectedClientRequestAndStackKey() throws Exception {
        Fixture f = new Fixture(2);
        CompletableFuture<ItemIconRenderCoordinator.BatchResult> future = f.render.requestIcons(f.player, f.payloads());
        byte[] png = png64();
        IconRenderResult icon = IconRenderResult.rendered(f.key, png, PngIconValidator.authoritativeSha256(png), RUSSIAN_NAME);
        f.render.onResponse(UUID.randomUUID(), new ItemIconRenderResponse(f.transport.last.getRequestId(), 2, "test", Collections.singletonList(icon)));
        assertFalse(future.isDone());
        f.render.onResponse(f.player, new ItemIconRenderResponse(f.transport.last.getRequestId(), 2, "test", Arrays.asList(
                IconRenderResult.rendered("unrequested", png, PngIconValidator.authoritativeSha256(png), "wrong"), icon)));
        ItemIconRenderCoordinator.BatchResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals(Collections.singleton(f.key), result.getRendered().keySet());
        assertEquals(RUSSIAN_NAME, result.getRendered().get(f.key).getDisplayName());
    }

    @Test
    void responseMustMatchTheNegotiatedProtocol() throws Exception {
        Fixture f = new Fixture(2);
        CompletableFuture<ItemIconRenderCoordinator.BatchResult> future = f.render.requestIcons(f.player, f.payloads());
        f.render.onResponse(f.player, new ItemIconRenderResponse(f.transport.last.getRequestId(), 1, "old", Collections.emptyList()));
        assertEquals("protocol_mismatch", future.get(1, TimeUnit.SECONDS).getError());
    }

    @Test
    void publicationChangesOnlyTheNameAndKeepsServerItemData() throws Exception {
        Fixture f = new Fixture(2);
        CompletableFuture<KitManifestSyncResult> future = f.publish();
        f.reply(RUSSIAN_NAME);
        assertTrue(future.get(1, TimeUnit.SECONDS).isSuccess());
        ManifestItem item = f.site.upload.getItems().get(0);
        assertEquals(RUSSIAN_NAME, item.getDisplayName());
        assertEquals("gregtech:gt.blockcasings", item.getRegistryId());
        assertEquals("casing", item.getSemanticKey());
        assertEquals(7, item.getSlot());
        assertEquals(64, item.getQuantity().intValue());
        assertEquals("GregTech", item.getModName());
        assertEquals("rare", item.getRarity());
        assertEquals(Collections.singletonMap("source", "server"), item.getMetadata());
        assertEquals(Collections.singletonList("Server tooltip"), item.getTooltip());
        assertEquals("server.enchantment", item.getEnchantments().get(0).getName());
        assertEquals(1, f.site.upload.getProtocolVersion()); // Website protocol did not change.
        assertEquals("site_test", f.site.upload.getDeliveryKey());
        assertTrue(f.site.upload.getAutoApply());
    }

    @Test
    void oldOrMissingClientAndBlankNameKeepTheServerFallback() throws Exception {
        for (int protocol : new int[]{0, 1, 2}) {
            Fixture f = new Fixture(protocol);
            CompletableFuture<KitManifestSyncResult> future = f.publish();
            if (protocol != 0) f.reply(protocol == 1 ? RUSSIAN_NAME : " §r ");
            assertTrue(future.get(1, TimeUnit.SECONDS).isSuccess());
            assertEquals("Heat Proof Machine Casing", f.site.upload.getItems().get(0).getDisplayName());
        }
    }

    private static byte[] png64() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, random.nextInt());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static final class Fixture {
        final UUID player = UUID.randomUUID();
        final byte[] stack = {1, 2, 3};
        final String key = RenderKey.fromItemStackBytes(7, stack).getValue();
        final IconRenderCache cache = new IconRenderCache();
        final Transport transport = new Transport();
        final Site site = new Site();
        final ItemIconRenderCoordinator render;
        final KitManifestSyncCoordinator sync;

        Fixture(int protocol) {
            LoggerAdapter log = (LoggerAdapter) Proxy.newProxyInstance(LoggerAdapter.class.getClassLoader(),
                    new Class[]{LoggerAdapter.class}, (p, m, a) -> null);
            ClientCapabilityRegistry registry = new ClientCapabilityRegistry();
            if (protocol != 0) registry.register(player, new ClientCapabilities(protocol, true, "test", 64, "png"));
            render = new ItemIconRenderCoordinator(registry, new PreferredPlayerRenderWorkerSelector(registry, player::equals), cache, transport, log);
            AzLinkPlatform platform = (AzLinkPlatform) Proxy.newProxyInstance(AzLinkPlatform.class.getClassLoader(),
                    new Class[]{AzLinkPlatform.class}, (p, m, a) -> m.getName().equals("getLoggerAdapter") ? log : null);
            AzLinkPlugin plugin = new AzLinkPlugin(platform) {
                @Override public boolean isConfigured() { return true; }
                @Override public boolean isDedicatedServerSiteLinkAllowed() { return true; }
            };
            sync = new KitManifestSyncCoordinator(plugin, site, new DefaultItemMetadataExtractor(), render, "test");
        }

        List<RenderItemPayload> payloads() {
            return Collections.singletonList(new RenderItemPayload(key, 7, stack));
        }

        CompletableFuture<KitManifestSyncResult> publish() {
            ItemStackSnapshot snapshot = ItemStackSnapshot.builder().slot(7).semanticKey("casing")
                    .registryId("gregtech:gt.blockcasings").displayName("Heat Proof Machine Casing")
                    .quantity(64).modName("GregTech").rarity("rare").tooltipLines(Collections.singletonList("Server tooltip"))
                    .metadata(Collections.singletonMap("source", "server"))
                    .enchantments(Collections.singletonList(new EnchantmentRef("server.enchantment", 3))).build();
            return sync.publish("site_test", 4, Collections.singletonList(new ServerKitItem(snapshot, stack)), player);
        }

        void reply(String name) throws Exception {
            byte[] png = png64();
            render.onResponse(player, new ItemIconRenderResponse(transport.last.getRequestId(), transport.last.getProtocolVersion(), "test",
                    Collections.singletonList(IconRenderResult.rendered(key, png, PngIconValidator.authoritativeSha256(png), name))));
        }
    }

    private static final class Transport implements ItemIconRenderTransport {
        ItemIconRenderRequest last;
        int sent;
        public void sendRequest(UUID player, ItemIconRenderRequest request) { last = request; sent++; }
        public void sendCapabilities(ClientCapabilities capabilities) {}
    }

    private static final class Site implements KitManifestApiClient {
        KitManifestUploadRequest upload;
        public CompletableFuture<ApiResult<IconUploadResult>> uploadIcon(byte[] bytes) {
            return CompletableFuture.completedFuture(ApiResult.ok(201, null));
        }
        public CompletableFuture<ApiResult<KitManifestUploadResponse>> uploadManifest(KitManifestUploadRequest request) {
            upload = request;
            return CompletableFuture.completedFuture(ApiResult.ok(201, new KitManifestUploadResponse()));
        }
        public CompletableFuture<ApiResult<KitLifecycleResponse>> ensureKit(KitEnsureRequest request) { throw new AssertionError(); }
        public CompletableFuture<ApiResult<KitLifecycleResponse>> retireKit(String key) { throw new AssertionError(); }
    }
}
