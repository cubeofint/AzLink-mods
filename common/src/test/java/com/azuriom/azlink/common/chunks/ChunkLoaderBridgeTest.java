package com.azuriom.azlink.common.chunks;

import com.azuriom.azlink.common.*;
import com.azuriom.azlink.common.executor.*;
import com.azuriom.azlink.common.executor.ledger.*;
import com.azuriom.azlink.common.executor.model.*;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.scheduler.JavaSchedulerAdapter;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkLoaderBridgeTest {
    @TempDir Path directory;
    private final LoggerAdapter logger=(LoggerAdapter)Proxy.newProxyInstance(LoggerAdapter.class.getClassLoader(),new Class[]{LoggerAdapter.class},(p,m,a)->null);
    private ShopOperation operation() {
        String receipt=UUID.randomUUID().toString(); JsonObject p=new JsonObject();
        p.addProperty("player_uuid",UUID.randomUUID().toString()); p.addProperty("player_name","FixturePlayer");
        p.addProperty("receipt",receipt); p.addProperty("quantity",51);
        p.addProperty("semantic_key","chunk-loaders."+receipt);
        p.addProperty("expires_at",Instant.now().plusSeconds(30L*86400).toString());
        return new ShopOperation(receipt,UUID.randomUUID().toString(),"chunk_loader_credit",String.join("",Collections.nCopies(64,"a")),p);
    }
    private AzLinkPlugin plugin(JavaSchedulerAdapter scheduler) {
        AzLinkPlatform platform=(AzLinkPlatform)Proxy.newProxyInstance(AzLinkPlatform.class.getClassLoader(),new Class[]{AzLinkPlatform.class},
                (p,m,a)->m.getName().equals("getSchedulerAdapter")?scheduler:m.getName().equals("getLoggerAdapter")?logger:null);
        return new AzLinkPlugin(platform);
    }
    @Test void validatesStrictQuantityIdentityReceiptAndAbsoluteExpiry() {
        ShopOperation op=operation(); ChunkLoaderPurchase p=ChunkLoaderPurchase.parse(op);
        assertEquals(51,p.quantity); assertEquals(op.getOperationId(),p.receipt);
        for(String bad:Arrays.asList("0","1000","1.5","4294967297")) {
            op.getPayload().add("quantity",com.google.gson.JsonParser.parseString(bad));
            assertThrows(IllegalArgumentException.class,()->ChunkLoaderPurchase.parse(op));
        }
        op.getPayload().addProperty("quantity",1);
        op.getPayload().addProperty("receipt",UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,()->ChunkLoaderPurchase.parse(op));
    }
    @Test void acceptsActualSitePollPayloadAndCanonicalHash() throws Exception {
        java.io.InputStream input=getClass().getResourceAsStream("/chunk-loader-site-wire.json");
        assertNotNull(input);
        ShopOperation operation;
        try(java.io.Reader reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)) {
            operation=AzLinkPlugin.getGson().fromJson(reader,ShopOperation.class);
        }
        ChunkLoaderPurchase purchase=ChunkLoaderPurchase.parse(operation);
        assertEquals(251,purchase.quantity);assertEquals(operation.getOperationId(),purchase.receipt);
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(
                AzLinkPlugin.getGson().toJson(operation.getPayload()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b&255));
        assertEquals(operation.getPayloadHash(),hex.toString());
    }
    @Test void onlyRealBackendAdvertisedAndOldConfigUpgradesWithoutLosingKitSettings() {
        ExecutorConfig c=AzLinkPlugin.getGson().fromJson("{\"supported_operation_types\":[\"kit_credit\"]}",ExecutorConfig.class);
        c.setChunkLoaderSupport(true); c.setChunkLoaderSupport(true);
        assertEquals(Arrays.asList("kit_credit","chunk_loader_credit"),c.getSupportedOperationTypes());
        c.setChunkLoaderSupport(false); assertEquals(Collections.singletonList("kit_credit"),c.getSupportedOperationTypes());
        assertNull(ReflectiveChunkCredit.find("unavailable"));
    }
    @Test void successWaitsForBackendAndDurableExecutorReplaySurvivesRestart() throws Exception {
        ExecutorConfig config=ExecutorConfig.defaults();config.setChunkLoaderSupport(true);
        ShopOperation op=operation(); AtomicInteger mutations=new AtomicInteger();
        JavaSchedulerAdapter scheduler=new JavaSchedulerAdapter(Runnable::run);
        try {
            for(int i=0;i<2;i++) {
                SemanticExecutor executor=new SemanticExecutor(config,new JsonOperationLedger(directory.resolve("ops.json")),
                        new EntitlementVersionLedger(directory.resolve("versions.json")),logger);
                executor.useChunkLoaderHandler(PlatformChunkLoaders.handler(plugin(scheduler),purchase->{
                    assertEquals(op.getOperationId(),purchase.receipt);assertEquals(51,purchase.quantity);mutations.incrementAndGet();
                }));
                assertEquals(i==0?OperationResultCode.SUCCEEDED:OperationResultCode.ALREADY_APPLIED,
                        executor.process(op).getResult().getResultCode());
            }
            assertEquals(1,mutations.get());
        } finally { scheduler.shutdown(); }
    }
    @Test void failedPersistenceIsUncertainAndNeverSuccessful() throws Exception {
        JavaSchedulerAdapter scheduler=new JavaSchedulerAdapter(Runnable::run);
        try {
            ExecutorConfig config=ExecutorConfig.defaults();config.setChunkLoaderSupport(true);
            SemanticExecutor executor=new SemanticExecutor(config,new JsonOperationLedger(directory.resolve("ops.json")),
                    new EntitlementVersionLedger(directory.resolve("versions.json")),logger);
            executor.useChunkLoaderHandler(PlatformChunkLoaders.handler(plugin(scheduler),p->{throw new IllegalStateException("disk unavailable");}));
            assertEquals(OperationResultCode.UNCERTAIN,executor.process(operation()).getResult().getResultCode());
        } finally { scheduler.shutdown(); }
    }
    @Test void abandonedMainThreadQueueCannotApplyLater() throws Exception {
        AtomicReference<Runnable> queued=new AtomicReference<>();AtomicReference<Thread> worker=new AtomicReference<>();
        AtomicInteger mutations=new AtomicInteger();JavaSchedulerAdapter scheduler=new JavaSchedulerAdapter(queued::set);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<ExecutionResult> result=pool.submit(()->{worker.set(Thread.currentThread());return PlatformChunkLoaders.handler(plugin(scheduler),p->mutations.incrementAndGet()).apply(operation(),logger);});
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(queued.get()==null&&System.nanoTime()<until)Thread.yield();
            assertNotNull(queued.get());worker.get().interrupt();
            assertEquals(OperationResultCode.RETRYABLE_FAILED,result.get(3,TimeUnit.SECONDS).getResultCode());
            queued.get().run();assertEquals(0,mutations.get());
        } finally { pool.shutdownNow();scheduler.shutdown(); }
    }
}
