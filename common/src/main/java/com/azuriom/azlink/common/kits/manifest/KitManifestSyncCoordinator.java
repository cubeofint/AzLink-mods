package com.azuriom.azlink.common.kits.manifest;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.kits.manifest.build.KitManifestBuilder;
import com.azuriom.azlink.common.kits.manifest.client.HttpKitManifestClient;
import com.azuriom.azlink.common.kits.manifest.client.KitManifestApiClient;
import com.azuriom.azlink.common.kits.manifest.extract.DefaultItemMetadataExtractor;
import com.azuriom.azlink.common.kits.manifest.extract.ItemMetadataExtractor;
import com.azuriom.azlink.common.kits.manifest.model.ItemIconRef;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadRequest;
import com.azuriom.azlink.common.kits.manifest.model.KitManifestUploadResponse;
import com.azuriom.azlink.common.kits.manifest.model.ManifestItem;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderCoordinator;
import com.azuriom.azlink.common.kits.manifest.render.RenderItemPayload;
import com.azuriom.azlink.common.kits.manifest.render.RenderKey;
import com.azuriom.azlink.common.utils.VersionInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Dedicated-server orchestration: authoritative metadata → optional client render → server site upload.
 * Client never talks to main-site.
 */
public final class KitManifestSyncCoordinator {

    private final AzLinkPlugin plugin;
    private final KitManifestApiClient siteClient;
    private final ItemMetadataExtractor metadataExtractor;
    private final ItemIconRenderCoordinator renderCoordinator;
    private final String executorVersion;

    public KitManifestSyncCoordinator(AzLinkPlugin plugin, ItemIconRenderCoordinator renderCoordinator) {
        this(plugin, new HttpKitManifestClient(plugin), new DefaultItemMetadataExtractor(),
                renderCoordinator, VersionInfo.VERSION);
    }

    public KitManifestSyncCoordinator(AzLinkPlugin plugin, KitManifestApiClient siteClient,
                                      ItemMetadataExtractor metadataExtractor,
                                      ItemIconRenderCoordinator renderCoordinator,
                                      String executorVersion) {
        this.plugin = plugin;
        this.siteClient = siteClient;
        this.metadataExtractor = metadataExtractor;
        this.renderCoordinator = renderCoordinator;
        this.executorVersion = executorVersion;
    }

    /**
     * Publish kit inventory and mirror kit shell on site (ensure + auto-apply).
     */
    public CompletableFuture<KitManifestSyncResult> publish(String deliveryKey, int manifestVersion,
                                                            List<ServerKitItem> serverItems,
                                                            UUID preferredRenderPlayer) {
        return publish(deliveryKey, manifestVersion, serverItems, preferredRenderPlayer,
                deliveryKey, null, true);
    }

    /**
     * @param kitName display name hint for site shell create
     * @param cooldownSeconds FTB cooldown in seconds ({@code null} = leave site policy unchanged on ensure)
     * @param mirrorToSite when true, ensure kit exists and auto-apply manifest
     */
    public CompletableFuture<KitManifestSyncResult> publish(String deliveryKey, int manifestVersion,
                                                            List<ServerKitItem> serverItems,
                                                            UUID preferredRenderPlayer,
                                                            String kitName,
                                                            Integer cooldownSeconds,
                                                            boolean mirrorToSite) {
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
        if (serverItems == null || serverItems.isEmpty()) {
            return CompletableFuture.completedFuture(
                    KitManifestSyncResult.fail(0, "items required", null));
        }

        List<RenderItemPayload> renderPayloads = new ArrayList<RenderItemPayload>();
        List<ServerKitItem> ordered = new ArrayList<ServerKitItem>(serverItems);
        Map<String, Integer> indexByKey = new HashMap<String, Integer>();

        for (int i = 0; i < ordered.size(); i++) {
            ServerKitItem item = ordered.get(i);
            RenderKey key = RenderKey.fromItemStackBytes(item.getSnapshot().getSlot(), item.getItemStackBytes());
            indexByKey.put(key.getValue(), i);
            renderPayloads.add(new RenderItemPayload(key.getValue(), item.getSnapshot().getSlot(),
                    item.getItemStackBytes()));
        }

        final String trimmedKey = deliveryKey.trim();
        final String nameHint = kitName == null ? trimmedKey : kitName;
        final Integer cooldown = cooldownSeconds;
        final boolean mirror = mirrorToSite;

        return this.renderCoordinator.requestIcons(preferredRenderPlayer, renderPayloads)
                .thenCompose(batch -> uploadIconsThenManifest(trimmedKey, manifestVersion,
                        ordered, indexByKey, batch, nameHint, cooldown, mirror));
    }

    private CompletableFuture<KitManifestSyncResult> uploadIconsThenManifest(
            String deliveryKey, int manifestVersion,
            List<ServerKitItem> ordered,
            Map<String, Integer> indexByKey,
            ItemIconRenderCoordinator.BatchResult batch,
            String kitName,
            Integer cooldownSeconds,
            boolean mirrorToSite) {

        Map<String, String> iconHashes = new HashMap<String, String>();
        List<CompletableFuture<Void>> uploads = new ArrayList<CompletableFuture<Void>>();

        for (Map.Entry<String, ItemIconRenderCoordinator.RenderedIcon> entry : batch.getRendered().entrySet()) {
            ItemIconRenderCoordinator.RenderedIcon icon = entry.getValue();
            iconHashes.put(entry.getKey(), icon.getIconHash());
            if (icon.getPngBytes() != null) {
                uploads.add(this.siteClient.uploadIcon(icon.getPngBytes()).thenAccept(result -> {
                    if (!result.isSuccess()) {
                        this.plugin.getLogger().warn("[KitManifest] icon upload failed render_key="
                                + entry.getKey() + " HTTP " + result.getHttpStatus());
                    }
                }));
            }
        }

        CompletableFuture<Void> allUploads = uploads.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0]));

        return allUploads.thenCompose(v -> {
            List<ManifestItem> manifestItems = new ArrayList<ManifestItem>();
            for (int i = 0; i < ordered.size(); i++) {
                ServerKitItem serverItem = ordered.get(i);
                RenderKey key = RenderKey.fromItemStackBytes(
                        serverItem.getSnapshot().getSlot(), serverItem.getItemStackBytes());
                String hash = iconHashes.get(key.getValue());
                ItemIconRef icon = hash == null ? ItemIconRef.none() : ItemIconRef.contentHash(hash);
                ManifestItem item = this.metadataExtractor.extract(serverItem.getSnapshot(), icon);
                manifestItems.add(item);
            }

            KitManifestUploadRequest request;
            try {
                request = KitManifestBuilder.buildRequest(
                        deliveryKey, manifestVersion, this.executorVersion, manifestItems);
                if (mirrorToSite) {
                    request = request.withLifecycle(true, true, kitName, cooldownSeconds);
                }
            } catch (RuntimeException e) {
                return CompletableFuture.completedFuture(KitManifestSyncResult.fail(e));
            }

            final String hash = request.getManifestHash();
            return this.siteClient.uploadManifest(request).thenApply(result -> {
                if (result.isSuccess() && result.getBody() != null) {
                    KitManifestUploadResponse body = result.getBody();
                    this.plugin.getLogger().info("[KitManifest] publish ok delivery_key=" + deliveryKey
                            + " outcome=" + body.getOutcome()
                            + " icons=" + iconHashes.size()
                            + (batch.getError() != null ? " render_note=" + batch.getError() : ""));
                    return KitManifestSyncResult.ok(result.getHttpStatus(), body, hash);
                }
                String detail = result.getError() != null
                        ? result.getError().getMessage()
                        : ("HTTP " + result.getHttpStatus()
                        + (result.getErrorBody() == null ? "" : " " + result.getErrorBody()));
                return KitManifestSyncResult.fail(result.getHttpStatus(), detail, hash);
            });
        });
    }

    public ItemIconRenderCoordinator getRenderCoordinator() {
        return this.renderCoordinator;
    }

    /**
     * Retire (disable/delete) commercial kit shell on the website.
     */
    public CompletableFuture<KitManifestApiClient.ApiResult<com.azuriom.azlink.common.kits.manifest.model.KitLifecycleResponse>>
    retireOnSite(String deliveryKey) {
        if (!this.plugin.isConfigured()) {
            return CompletableFuture.completedFuture(
                    KitManifestApiClient.ApiResult.failure(new IllegalStateException("azlink not configured")));
        }
        if (!this.plugin.isDedicatedServerSiteLinkAllowed()) {
            return CompletableFuture.completedFuture(
                    KitManifestApiClient.ApiResult.failure(
                            new IllegalStateException("Website linking is only available on dedicated servers.")));
        }
        if (deliveryKey == null || deliveryKey.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    KitManifestApiClient.ApiResult.failure(new IllegalArgumentException("delivery_key required")));
        }
        return this.siteClient.retireKit(deliveryKey.trim());
    }
}
