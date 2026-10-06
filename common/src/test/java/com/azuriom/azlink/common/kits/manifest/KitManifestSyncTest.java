package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.kits.manifest.build.KitManifestBuilder;
import com.azuriom.azlink.common.kits.manifest.extract.DefaultItemMetadataExtractor;
import com.azuriom.azlink.common.kits.manifest.extract.DefaultTooltipExtractor;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import com.azuriom.azlink.common.kits.manifest.hash.CanonicalJson;
import com.azuriom.azlink.common.kits.manifest.icon.IconContentAddressing;
import com.azuriom.azlink.common.kits.manifest.model.EnchantmentRef;
import com.azuriom.azlink.common.kits.manifest.model.ItemIconRef;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadRequest;
import com.azuriom.azlink.common.kits.manifest.model.ManifestItem;
import com.azuriom.azlink.common.kits.manifest.model.NamedAttribute;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitManifestSyncTest {

    private static final String PHP_FIXTURE_HASH =
            "a96335ef1e73e45ab019fa71400a59a0a349e93b7a9f54ab5c7c0ee348d11ccb";

    @Test
    void metadataExtractionMapsSnapshotFields() {
        ItemStackSnapshot snapshot = ItemStackSnapshot.builder()
                .slot(3)
                .semanticKey("emerald")
                .registryId("minecraft:emerald")
                .displayName("Emerald")
                .quantity(16)
                .modName("Minecraft")
                .rarity("uncommon")
                .putMetadata("custom_model_data", 42)
                .attributes(Collections.singletonList(new NamedAttribute("Fortune", "+1")))
                .enchantments(Collections.singletonList(new EnchantmentRef("Unbreaking", 3)))
                .build();

        ManifestItem item = new DefaultItemMetadataExtractor().extract(snapshot, ItemIconRef.none());
        assertEquals(3, item.getSlot());
        assertEquals("emerald", item.getSemanticKey());
        assertEquals("minecraft:emerald", item.getRegistryId());
        assertEquals("Emerald", item.getDisplayName());
        assertEquals(16, item.getQuantity().intValue());
        assertEquals("Minecraft", item.getModName());
        assertEquals("uncommon", item.getRarity());
        assertEquals(42, item.getMetadata().get("custom_model_data"));
        assertEquals(1, item.getAttributes().size());
        assertEquals(1, item.getEnchantments().size());
        assertEquals("none", item.getIcon().getType());
    }

    @Test
    void tooltipExtractorTruncatesAndDropsBlanks() {
        String longLine = repeat('x', KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH + 10);
        ItemStackSnapshot snapshot = ItemStackSnapshot.builder()
                .slot(0)
                .semanticKey("a")
                .registryId("minecraft:stone")
                .displayName("Stone")
                .tooltipLines(Arrays.asList("  hello  ", "", longLine, "keep"))
                .build();

        List<String> lines = new DefaultTooltipExtractor().extract(snapshot);
        assertEquals(3, lines.size());
        assertEquals("hello", lines.get(0));
        assertEquals(KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH, lines.get(1).length());
        assertEquals("keep", lines.get(2));
    }

    @Test
    void iconPipelineValidatesPngAndHashes() {
        byte[] png = minimalPng();
        String hash = IconContentAddressing.hashPng(png);
        assertEquals(64, hash.length());
        assertThrows(IllegalArgumentException.class, () -> IconContentAddressing.validatePng(new byte[]{1, 2, 3}));
    }

    @Test
    void canonicalHashMatchesPhpFixture() throws Exception {
        ManifestItem item = new ManifestItem(
                1,
                "sword",
                "minecraft:diamond_sword",
                "Diamond Sword",
                1,
                null,
                "rare",
                Arrays.asList("Sharp blade", "Line 2"),
                Collections.singletonList(new NamedAttribute("Damage", "7")),
                Collections.singletonList(new EnchantmentRef("Sharpness", 5)),
                Collections.singletonMap("custom_model_data", 100),
                ItemIconRef.contentHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        );

        String hash = KitManifestBuilder.computeManifestHash(Collections.singletonList(item));
        assertEquals(PHP_FIXTURE_HASH, hash);

        try (Reader reader = new InputStreamReader(
                KitManifestSyncTest.class.getClassLoader()
                        .getResourceAsStream("fixtures/kit-manifest-v1/canonical-payload.json"),
                StandardCharsets.UTF_8)) {
            JsonObject expected = new JsonParser().parse(reader).getAsJsonObject();
            JsonObject actual = KitManifestBuilder.toCanonicalPayload(Collections.singletonList(item));
            assertEquals(CanonicalJson.encode(expected), CanonicalJson.encode(actual));
        }
    }

    @Test
    void manifestUploadRequestContainsRequiredWireFields() {
        ManifestItem item = new DefaultItemMetadataExtractor().extract(
                ItemStackSnapshot.builder()
                        .slot(0)
                        .semanticKey("dirt")
                        .registryId("minecraft:dirt")
                        .displayName("Dirt")
                        .quantity(1)
                        .build(),
                ItemIconRef.none());

        KitManifestUploadRequest request = KitManifestBuilder.buildRequest(
                "kit.emerald", 2, "1.3.11", Collections.singletonList(item));

        assertEquals(1, request.getProtocolVersion());
        assertEquals("1.3.11", request.getExecutorVersion());
        assertEquals("kit.emerald", request.getDeliveryKey());
        assertEquals(2, request.getManifestVersion());
        assertEquals(64, request.getManifestHash().length());
        assertFalse(request.getManifestHash().isEmpty());
        assertEquals(1, request.getItems().size());
    }

    @Test
    void publicApiInterfaceIsStable() {
        assertNotNull(KitManifestProvider.class);
        assertEquals(2, KitManifestProvider.class.getDeclaredMethods().length);
    }

    private static byte[] minimalPng() {
        // 1x1 transparent PNG
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

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
