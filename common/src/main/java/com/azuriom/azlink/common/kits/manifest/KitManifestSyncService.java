package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.build.KitManifestBuilder;
import com.azuriom.azlink.common.kits.manifest.client.HttpKitManifestClient;
import com.azuriom.azlink.common.kits.manifest.client.KitManifestApiClient;
import com.azuriom.azlink.common.kits.manifest.extract.DefaultItemMetadataExtractor;
import com.azuriom.azlink.common.kits.manifest.extract.ItemMetadataExtractor;
import com.azuriom.azlink.common.kits.manifest.extract.ItemStackSnapshot;
import com.azuriom.azlink.common.kits.manifest.icon.IconContentAddressing;
import com.azuriom.azlink.common.kits.manifest.model.IconUploadResult;
import com.azuriom.azlink.common.kits.manifest.model.ItemIconRef;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadRequest;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadResponse;
import com.azuriom.azlink.common.kits.manifest.model.ManifestItem;
import com.azuriom.azlink.common.utils.VersionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link KitManifestProvider}. Explicit sync API; NeoForge also hooks FTB kit CRUD via coordinator.
 */
public final class KitManifestSyncService implements KitManifestProvider {

    private final AzLinkPlugin plugin;
    private final KitManifestApiClient client;
    private final ItemMetadataExtractor metadataExtractor;
    private final String executorVersion;

    public KitManifestSyncService(AzLinkPlugin plugin) {
        this(plugin, new HttpKitManifestClient(plugin), new DefaultItemMetadataExtractor(), VersionInfo.VERSION);
    }

    public KitManifestSyncService(AzLinkPlugin plugin, KitManifestApiClient client,
                                  ItemMetadataExtractor metadataExtractor, String executorVersion) {
        this.plugin = plugin;
        this.client = client;
        this.metadataExtractor = metadataExtractor;
        this.executorVersion = executorVersion;
    }

    @Override
    public CompletableFuture<String> uploadIconPng(byte[] pngBytes) {
        if (!this.plugin.isDedicatedServerSiteLinkAllowed()) {
            CompletableFuture<String> failed = new CompletableFuture<String>();
            failed.completeExceptionally(new IllegalStateException(
                    "Website linking is only available on dedicated servers."));
            return failed;
        }
        return this.client.uploadIcon(pngBytes).thenApply(result -> {
            if (!result.isSuccess() || result.getBody() == null) {
                throw new IllegalStateException("icon upload failed HTTP " + result.getHttpStatus()
                        + (result.getErrorBody() == null ? "" : ": " + result.getErrorBody()));
            }
            return result.getBody().getHash();
        });
    }

    @Override
    public CompletableFuture<KitManifestSyncResult> sync(String deliveryKey, int manifestVersion,
                                                         List<ItemStackSnapshot> items) {
        if (!this.plugin.isConfigured()) {
            return CompletableFuture.completedFuture(
                    KitManifestSyncResult.fail(0, "azlink not configured", null));
        }
        if (!this.plugin.isDedicatedServerSiteLinkAllowed()) {
            return CompletableFuture.completedFuture(
                    KitManifestSyncResult.fail(0,
                            "Website linking is only available on dedicated servers.", null));
        }
        if (deliveryKey == null || deliveryKey.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    KitManifestSyncResult.fail(0, "delivery_key required", null));
        }
        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(
                    KitManifestSyncResult.fail(0, "items required", null));
        }

        return prepareItems(items).thenCompose(manifestItems -> {
            KitManifestUploadRequest request;
            try {
                request = KitManifestBuilder.buildRequest(
                        deliveryKey.trim(), manifestVersion, this.executorVersion, manifestItems);
            } catch (RuntimeException e) {
                return CompletableFuture.completedFuture(KitManifestSyncResult.fail(e));
            }

            final String hash = request.getManifestHash();
            return this.client.uploadManifest(request).thenApply(result -> {
                if (result.isSuccess() && result.getBody() != null) {
                    KitManifestUploadResponse body = result.getBody();
                    this.plugin.getLogger().info("[KitManifest] sync ok delivery_key=" + deliveryKey
                            + " outcome=" + body.getOutcome()
                            + " status=" + body.getStatus()
                            + " manifest_id=" + body.getManifestId());
                    return KitManifestSyncResult.ok(result.getHttpStatus(), body, hash);
                }
                String detail = result.getError() != null
                        ? result.getError().getMessage()
                        : ("HTTP " + result.getHttpStatus()
                        + (result.getErrorBody() == null ? "" : " " + result.getErrorBody()));
                return KitManifestSyncResult.fail(result.getHttpStatus(), detail, hash);
            });
        }).exceptionally(ex -> KitManifestSyncResult.fail(ex));
    }

    /**
     * Metadata + tooltip extraction and icon pipeline (hash + upload when PNG present).
     */
    CompletableFuture<List<ManifestItem>> prepareItems(List<ItemStackSnapshot> snapshots) {
        CompletableFuture<List<ManifestItem>> chain =
                CompletableFuture.completedFuture(new ArrayList<ManifestItem>());

        for (final ItemStackSnapshot snapshot : snapshots) {
            chain = chain.thenCompose(acc -> resolveIcon(snapshot).thenApply(icon -> {
                ManifestItem item = this.metadataExtractor.extract(snapshot, icon);
                acc.add(item);
                return acc;
            }));
        }
        return chain;
    }

    private CompletableFuture<ItemIconRef> resolveIcon(ItemStackSnapshot snapshot) {
        byte[] png = snapshot.getIconPng();
        if (png == null || png.length == 0) {
            return CompletableFuture.completedFuture(ItemIconRef.none());
        }
        try {
            IconContentAddressing.validatePng(png);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(ItemIconRef.none());
        }

        final String localHash = IconContentAddressing.sha256Hex(png);
        return this.client.uploadIcon(png).thenApply(result -> {
            if (result.isSuccess() && result.getBody() != null && result.getBody().getHash() != null) {
                IconUploadResult body = result.getBody();
                return ItemIconRef.contentHash(body.getHash());
            }
            this.plugin.getLogger().warn("[KitManifest] icon upload failed, continuing with local hash="
                    + localHash);
            return ItemIconRef.contentHash(localHash);
        });
    }

    public ItemMetadataExtractor getMetadataExtractor() {
        return this.metadataExtractor;
    }
}
