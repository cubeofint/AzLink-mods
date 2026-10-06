package com.azuriom.azlink.common.kits.manifest.render;

import com.azuriom.azlink.common.logger.LoggerAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated-server coordinator for client-assisted icon rendering.
 * Client never uploads to main-site; this component only collects validated PNGs.
 */
public final class ItemIconRenderCoordinator implements ItemIconRenderTransport.ServerHandler {

    public static final class RenderedIcon {
        private final String renderKey;
        private final String iconHash;
        private final byte[] pngBytes;

        public RenderedIcon(String renderKey, String iconHash, byte[] pngBytes) {
            this.renderKey = renderKey;
            this.iconHash = iconHash;
            this.pngBytes = pngBytes;
        }

        public String getRenderKey() {
            return this.renderKey;
        }

        public String getIconHash() {
            return this.iconHash;
        }

        public byte[] getPngBytes() {
            return this.pngBytes;
        }
    }

    public static final class BatchResult {
        private final Map<String, RenderedIcon> rendered;
        private final Set<String> failedKeys;
        private final String error;

        public BatchResult(Map<String, RenderedIcon> rendered, Set<String> failedKeys, String error) {
            this.rendered = Collections.unmodifiableMap(new HashMap<String, RenderedIcon>(rendered));
            this.failedKeys = Collections.unmodifiableSet(new HashSet<String>(failedKeys));
            this.error = error;
        }

        public Map<String, RenderedIcon> getRendered() {
            return this.rendered;
        }

        public Set<String> getFailedKeys() {
            return this.failedKeys;
        }

        public String getError() {
            return this.error;
        }

        public boolean isRendererUnavailable() {
            return "renderer_unavailable".equals(this.error);
        }
    }

    private final ClientCapabilityRegistry capabilities;
    private final RenderWorkerSelector workerSelector;
    private final IconRenderCache cache;
    private final ItemIconRenderTransport transport;
    private final LoggerAdapter logger;

    private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<UUID, PendingRequest>();
    private final Map<UUID, AtomicInteger> inFlight = new ConcurrentHashMap<UUID, AtomicInteger>();
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "azlink-icon-render-timeout");
        t.setDaemon(true);
        return t;
    });

    public ItemIconRenderCoordinator(ClientCapabilityRegistry capabilities,
                                     RenderWorkerSelector workerSelector,
                                     IconRenderCache cache,
                                     ItemIconRenderTransport transport,
                                     LoggerAdapter logger) {
        this.capabilities = capabilities;
        this.workerSelector = workerSelector;
        this.cache = cache;
        this.transport = transport;
        this.logger = logger;
    }

    public ClientCapabilityRegistry getCapabilities() {
        return this.capabilities;
    }

    public IconRenderCache getCache() {
        return this.cache;
    }

    /**
     * Request icons for missing render keys. Uses preferred player only (no random pick).
     */
    public CompletableFuture<BatchResult> requestIcons(UUID preferredPlayer, List<RenderItemPayload> items) {
        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(new BatchResult(
                    Collections.<String, RenderedIcon>emptyMap(),
                    Collections.<String>emptySet(),
                    null));
        }
        if (items.size() > ItemIconRenderProtocol.MAX_ITEMS_PER_REQUEST) {
            return CompletableFuture.completedFuture(new BatchResult(
                    Collections.<String, RenderedIcon>emptyMap(),
                    Collections.<String>emptySet(),
                    "batch_too_large"));
        }

        Map<String, RenderedIcon> fromCache = new HashMap<String, RenderedIcon>();
        List<RenderItemPayload> missing = new ArrayList<RenderItemPayload>();
        for (RenderItemPayload item : items) {
            if (item.getSerializedSize() > ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES) {
                continue;
            }
            Optional<String> cached = this.cache.findIconHash(item.getRenderKey());
            if (cached.isPresent()) {
                fromCache.put(item.getRenderKey(), new RenderedIcon(item.getRenderKey(), cached.get(), null));
            } else {
                missing.add(item);
            }
        }

        if (missing.isEmpty()) {
            return CompletableFuture.completedFuture(new BatchResult(fromCache, Collections.<String>emptySet(), null));
        }

        Optional<UUID> worker = this.workerSelector.select(preferredPlayer);
        if (!worker.isPresent()) {
            return CompletableFuture.completedFuture(new BatchResult(fromCache, keySet(missing), "renderer_unavailable"));
        }

        UUID playerId = worker.get();
        AtomicInteger flight = this.inFlight.computeIfAbsent(playerId, id -> new AtomicInteger());
        if (flight.get() >= ItemIconRenderProtocol.MAX_IN_FLIGHT_PER_PLAYER) {
            return CompletableFuture.completedFuture(new BatchResult(fromCache, keySet(missing), "renderer_busy"));
        }

        UUID requestId = UUID.randomUUID();
        ItemIconRenderRequest request = new ItemIconRenderRequest(
                requestId,
                ItemIconRenderProtocol.VERSION,
                ItemIconRenderProtocol.ICON_SIZE,
                missing
        );

        CompletableFuture<BatchResult> future = new CompletableFuture<BatchResult>();
        Set<String> expected = keySet(missing);
        PendingRequest pendingRequest = new PendingRequest(playerId, expected, fromCache, future);
        this.pending.put(requestId, pendingRequest);
        flight.incrementAndGet();

        try {
            this.transport.sendRequest(playerId, request);
            this.logger.info("[ItemIconRender] request_id=" + requestId
                    + " player=" + playerId
                    + " items=" + missing.size());
        } catch (RuntimeException e) {
            cleanup(requestId, pendingRequest);
            future.complete(new BatchResult(fromCache, expected, "send_failed"));
            return future;
        }

        // Timeout without blocking caller thread.
        this.timeouts.schedule(new Runnable() {
            @Override
            public void run() {
                PendingRequest still = ItemIconRenderCoordinator.this.pending.remove(requestId);
                if (still != null) {
                    decrementFlight(still.playerId);
                    still.future.complete(new BatchResult(still.partial, still.expectedKeys, "timeout"));
                    ItemIconRenderCoordinator.this.logger.warn("[ItemIconRender] timeout request_id=" + requestId);
                }
            }
        }, ItemIconRenderProtocol.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        return future;
    }

    @Override
    public void onCapabilities(UUID playerId, ClientCapabilities capabilities) {
        this.capabilities.register(playerId, capabilities);
        this.logger.info("[ItemIconRender] capabilities player=" + playerId
                + " protocol=" + capabilities.getProtocolVersion()
                + " render=" + capabilities.supportsItemIconRendering());
    }

    @Override
    public void onResponse(UUID playerId, ItemIconRenderResponse response) {
        if (response == null || response.getRequestId() == null) {
            return;
        }
        PendingRequest pendingRequest = this.pending.remove(response.getRequestId());
        if (pendingRequest == null) {
            this.logger.warn("[ItemIconRender] unknown request_id=" + response.getRequestId());
            return;
        }
        if (!pendingRequest.playerId.equals(playerId)) {
            this.logger.warn("[ItemIconRender] player mismatch request_id=" + response.getRequestId());
            // Put back? reject — do not accept forged responses
            this.pending.put(response.getRequestId(), pendingRequest);
            return;
        }
        decrementFlight(playerId);

        if (response.getProtocolVersion() != ItemIconRenderProtocol.VERSION) {
            pendingRequest.future.complete(new BatchResult(
                    pendingRequest.partial, pendingRequest.expectedKeys, "protocol_mismatch"));
            return;
        }

        Map<String, RenderedIcon> rendered = new HashMap<String, RenderedIcon>(pendingRequest.partial);
        Set<String> failed = new HashSet<String>();
        Set<String> seen = new HashSet<String>();

        for (IconRenderResult icon : response.getIcons()) {
            if (icon == null || icon.getRenderKey() == null) {
                continue;
            }
            if (!pendingRequest.expectedKeys.contains(icon.getRenderKey())) {
                this.logger.warn("[ItemIconRender] unknown render_key=" + icon.getRenderKey());
                continue;
            }
            if (!seen.add(icon.getRenderKey())) {
                continue; // duplicate safe
            }
            if (icon.getStatus() != IconRenderResult.Status.RENDERED) {
                failed.add(icon.getRenderKey());
                continue;
            }
            try {
                byte[] png = icon.getPngBytes();
                PngIconValidator.validateIconPng(png, ItemIconRenderProtocol.ICON_SIZE);
                PngIconValidator.assertClientHashMatches(png, icon.getClientSha256());
                String hash = PngIconValidator.authoritativeSha256(png);
                this.cache.put(icon.getRenderKey(), hash);
                rendered.put(icon.getRenderKey(), new RenderedIcon(icon.getRenderKey(), hash, png));
            } catch (RuntimeException e) {
                failed.add(icon.getRenderKey());
                this.logger.warn("[ItemIconRender] reject icon render_key=" + icon.getRenderKey()
                        + " reason=" + e.getMessage());
            }
        }

        for (String key : pendingRequest.expectedKeys) {
            if (!rendered.containsKey(key) && !failed.contains(key)) {
                failed.add(key);
            }
        }

        pendingRequest.future.complete(new BatchResult(rendered, failed, null));
    }

    @Override
    public void onDisconnect(UUID playerId) {
        this.capabilities.unregister(playerId);
        this.inFlight.remove(playerId);
        List<UUID> toRemove = new ArrayList<UUID>();
        for (Map.Entry<UUID, PendingRequest> entry : this.pending.entrySet()) {
            if (entry.getValue().playerId.equals(playerId)) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID requestId : toRemove) {
            PendingRequest pr = this.pending.remove(requestId);
            if (pr != null) {
                pr.future.complete(new BatchResult(pr.partial, pr.expectedKeys, "client_disconnect"));
            }
        }
    }

    private void cleanup(UUID requestId, PendingRequest pendingRequest) {
        this.pending.remove(requestId);
        decrementFlight(pendingRequest.playerId);
    }

    private void decrementFlight(UUID playerId) {
        AtomicInteger flight = this.inFlight.get(playerId);
        if (flight != null) {
            flight.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private static Set<String> keySet(List<RenderItemPayload> items) {
        Set<String> keys = new HashSet<String>();
        for (RenderItemPayload item : items) {
            keys.add(item.getRenderKey());
        }
        return keys;
    }

    private static final class PendingRequest {
        private final UUID playerId;
        private final Set<String> expectedKeys;
        private final Map<String, RenderedIcon> partial;
        private final CompletableFuture<BatchResult> future;

        private PendingRequest(UUID playerId, Set<String> expectedKeys,
                               Map<String, RenderedIcon> partial,
                               CompletableFuture<BatchResult> future) {
            this.playerId = playerId;
            this.expectedKeys = expectedKeys;
            this.partial = new HashMap<String, RenderedIcon>(partial);
            this.future = future;
        }
    }
}
