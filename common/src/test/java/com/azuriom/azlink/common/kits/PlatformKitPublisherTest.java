package com.azuriom.azlink.common.kits;

import com.azuriom.azlink.common.*;
import com.azuriom.azlink.common.executor.model.*;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.scheduler.JavaSchedulerAdapter;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformKitPublisherTest {
    @Test void kitOnlyPlatformDoesNotAdvertiseMockPrivilegesOrAddDisabledTypes() {
        com.azuriom.azlink.common.executor.ExecutorConfig defaults = com.azuriom.azlink.common.executor.ExecutorConfig.defaults();
        defaults.limitToKitOperations();
        assertEquals(Arrays.asList("kit_redeem", "kit_credit"), defaults.getSupportedOperationTypes());
        com.azuriom.azlink.common.executor.ExecutorConfig existing = AzLinkPlugin.getGson().fromJson("{\"supported_operation_types\":[\"privilege_reconcile\",\"kit_redeem\"]}", com.azuriom.azlink.common.executor.ExecutorConfig.class);
        existing.limitToKitOperations();
        assertEquals(Collections.singletonList("kit_redeem"), existing.getSupportedOperationTypes());
    }
    private final LoggerAdapter logger = (LoggerAdapter) Proxy.newProxyInstance(LoggerAdapter.class.getClassLoader(), new Class[]{LoggerAdapter.class}, (p,m,a) -> null);
    private AzLinkPlugin plugin(JavaSchedulerAdapter scheduler) {
        AzLinkPlatform platform = (AzLinkPlatform) Proxy.newProxyInstance(AzLinkPlatform.class.getClassLoader(), new Class[]{AzLinkPlatform.class},
            (p,m,a) -> m.getName().equals("getLoggerAdapter") ? logger : m.getName().equals("getSchedulerAdapter") ? scheduler : null);
        return new AzLinkPlugin(platform);
    }
    private ShopOperation operation() {
        JsonObject payload = new JsonObject(); payload.addProperty("uuid", UUID.randomUUID().toString());
        payload.addProperty("kit", "starter"); payload.addProperty("qty", 3);
        return new ShopOperation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "kit_credit", "hash", payload);
    }
    private abstract static class Catalog implements PlatformKitCatalog {
        public List<String> names() { return Collections.emptyList(); }
        public Definition read(String name) { throw new UnsupportedOperationException(); }
    }
    @Test void creditWaitsForPersistenceAndPassesStableReceipt() throws Exception {
        JavaSchedulerAdapter scheduler = new JavaSchedulerAdapter(Runnable::run);
        try {
            ShopOperation op = operation(); AtomicInteger count = new AtomicInteger();
            PlatformKitPublisher publisher = new PlatformKitPublisher(plugin(scheduler), new Catalog() {
                public int credit(UUID player,String key,int quantity,String receipt) {
                    assertEquals(op.getPlayerUuid(),player); assertEquals("starter",key); assertEquals(3,quantity); assertEquals(op.getOperationId(),receipt);
                    count.incrementAndGet(); return 3;
                }
            });
            assertEquals(OperationResultCode.SUCCEEDED,publisher.creditHandler(false).apply(op,logger).getResultCode());
            assertEquals(1,count.get());
        } finally { scheduler.shutdown(); }
    }
    @Test void missingBridgeIsRetryableAndPersistenceErrorNeverAcknowledgesSuccess() throws Exception {
        JavaSchedulerAdapter scheduler = new JavaSchedulerAdapter(Runnable::run);
        try {
            PlatformKitPublisher missing = new PlatformKitPublisher(plugin(scheduler), new Catalog() {
                public int credit(UUID player,String key,int quantity,String receipt) { return ReflectiveKitCredit.credit("missing.CoreBridge",player,key,quantity,receipt); }
            });
            assertEquals(OperationResultCode.RETRYABLE_FAILED,missing.creditHandler(false).apply(operation(),logger).getResultCode());
            PlatformKitPublisher failed = new PlatformKitPublisher(plugin(scheduler), new Catalog() {
                public int credit(UUID player,String key,int quantity,String receipt) { throw new IllegalStateException("disk failure"); }
            });
            assertThrows(IllegalStateException.class,()->failed.creditHandler(false).apply(operation(),logger));
        } finally { scheduler.shutdown(); }
    }
    @Test void abandonedServerQueueCannotApplyCreditLater() throws Exception {
        AtomicReference<Runnable> queued = new AtomicReference<>(); AtomicInteger mutations = new AtomicInteger();
        JavaSchedulerAdapter scheduler = new JavaSchedulerAdapter(queued::set);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PlatformKitPublisher publisher = new PlatformKitPublisher(plugin(scheduler),new Catalog() {
                public int credit(UUID player,String key,int quantity,String receipt) { return mutations.incrementAndGet(); }
            });
            AtomicReference<Thread> worker = new AtomicReference<>();
            Future<ExecutionResult> result = executor.submit(()->{ worker.set(Thread.currentThread()); return publisher.creditHandler(false).apply(operation(),logger); });
            long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(queued.get()==null && System.nanoTime()<until) Thread.yield();
            assertNotNull(queued.get()); worker.get().interrupt();
            assertEquals(OperationResultCode.RETRYABLE_FAILED,result.get(2,TimeUnit.SECONDS).getResultCode());
            queued.get().run(); assertEquals(0,mutations.get());
        } finally { executor.shutdownNow(); scheduler.shutdown(); }
    }
}
