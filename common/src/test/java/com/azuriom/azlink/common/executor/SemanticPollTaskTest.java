package com.azuriom.azlink.common.executor;

import com.azuriom.azlink.common.AzLinkPlatform;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.executor.client.OperationsApiClient;
import com.azuriom.azlink.common.executor.ledger.EntitlementVersionLedger;
import com.azuriom.azlink.common.executor.ledger.JsonOperationLedger;
import com.azuriom.azlink.common.executor.model.AckRequest;
import com.azuriom.azlink.common.executor.model.AckResponse;
import com.azuriom.azlink.common.executor.model.PollRequest;
import com.azuriom.azlink.common.executor.model.PollResponse;
import com.azuriom.azlink.common.executor.model.ShopOperation;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.platform.PlatformInfo;
import com.azuriom.azlink.common.platform.PlatformType;
import com.azuriom.azlink.common.scheduler.CancellableTask;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticPollTaskTest {

    @TempDir
    Path tempDir;

    private FakeClient client;
    private SemanticPollTask task;
    private SemanticExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        this.client = new FakeClient();
        TestLogger logger = new TestLogger();
        JsonOperationLedger ops = new JsonOperationLedger(this.tempDir.resolve("ops.json"));
        EntitlementVersionLedger versions = new EntitlementVersionLedger(this.tempDir.resolve("ver.json"));
        this.executor = new SemanticExecutor(OperationFixtures.configWithFly(), ops, versions, logger);

        AzLinkPlugin plugin = new AzLinkPlugin(new FakePlatform(this.tempDir, logger));
        plugin.getConfig().setSiteUrl("https://example.test");
        plugin.getConfig().setSiteKey("test-token");

        this.task = new SemanticPollTask(plugin, this.executor, this.client);
    }

    @Test
    void pollSuccessProcessesAndAcks() throws Exception {
        ShopOperation op = OperationFixtures.kitRedeem(
                "21212121-2121-2121-2121-212121212121",
                "22222222-2222-2222-2222-222222222222",
                OperationFixtures.HASH_A, 1);
        this.client.nextPoll = OperationsApiClient.PollResult.success(200,
                new PollResponse(1, Collections.singletonList(op)));
        this.client.nextAck = OperationsApiClient.AckResult.success(200, new AckResponse());

        this.task.pollOnce().get(5, TimeUnit.SECONDS);

        assertEquals(1, this.executor.getMockApplyCount());
        assertEquals(1, this.client.acks.size());
        assertEquals("succeeded", this.client.acks.get(0).getStatus());
        assertEquals("applied", this.client.acks.get(0).getResultCode());
        assertTrue(this.client.acks.get(0).getExecutorVersion() != null
                && !this.client.acks.get(0).getExecutorVersion().isEmpty());
        assertTrue(this.client.lastPollRequest.getSupportedOperationTypes().contains("kit_redeem"));
    }

    @Test
    void unsupportedProtocolVersionSkipsBatch() throws Exception {
        ShopOperation op = OperationFixtures.kitRedeem(
                "23232323-2323-2323-2323-232323232323",
                "24242424-2424-2424-2424-242424242424",
                OperationFixtures.HASH_A, 1);
        this.client.nextPoll = OperationsApiClient.PollResult.success(200,
                new PollResponse(99, Collections.singletonList(op)));

        this.task.pollOnce().get(5, TimeUnit.SECONDS);

        assertEquals(0, this.executor.getMockApplyCount());
        assertTrue(this.client.acks.isEmpty());
    }

    @Test
    void invalidTokenDoesNotProcess() throws Exception {
        this.client.nextPoll = OperationsApiClient.PollResult.httpError(401, "{\"error\":\"invalid token\"}");

        this.task.pollOnce().get(5, TimeUnit.SECONDS);

        assertEquals(0, this.executor.getMockApplyCount());
        assertTrue(this.client.acks.isEmpty());
    }

    @Test
    void leaseReclaimAfterCompletedAcksAlreadyApplied() throws Exception {
        ShopOperation first = OperationFixtures.kitRedeem(
                "25252525-2525-2525-2525-252525252525",
                "26262626-2626-2626-2626-262626262626",
                OperationFixtures.HASH_A, 3);
        this.executor.process(first);

        ShopOperation reclaim = OperationFixtures.kitRedeem(
                "25252525-2525-2525-2525-252525252525",
                "27272727-2727-2727-2727-272727272727",
                OperationFixtures.HASH_A, 3);
        this.client.nextPoll = OperationsApiClient.PollResult.success(200,
                new PollResponse(1, Collections.singletonList(reclaim)));
        this.client.nextAck = OperationsApiClient.AckResult.success(200, new AckResponse());

        this.task.pollOnce().get(5, TimeUnit.SECONDS);

        assertEquals(1, this.executor.getMockApplyCount());
        assertEquals("already_applied", this.client.acks.get(0).getStatus());
        assertEquals("27272727-2727-2727-2727-272727272727", this.client.acks.get(0).getClaimToken());
    }

    private static final class FakeClient implements OperationsApiClient {
        int pollCount;
        PollRequest lastPollRequest;
        PollResult nextPoll = PollResult.success(200, new PollResponse(1, Collections.<ShopOperation>emptyList()));
        AckResult nextAck = AckResult.success(200, new AckResponse());
        final List<AckRequest> acks = new ArrayList<AckRequest>();

        @Override
        public CompletableFuture<PollResult> poll(PollRequest request) {
            this.pollCount++;
            this.lastPollRequest = request;
            return CompletableFuture.completedFuture(this.nextPoll);
        }

        @Override
        public CompletableFuture<AckResult> ack(AckRequest request) {
            this.acks.add(request);
            return CompletableFuture.completedFuture(this.nextAck);
        }
    }

    private static final class FakePlatform implements AzLinkPlatform {
        private final Path dataDir;
        private final LoggerAdapter logger;
        private final SchedulerAdapter scheduler = new ImmediateScheduler();

        FakePlatform(Path dataDir, LoggerAdapter logger) {
            this.dataDir = dataDir;
            this.logger = logger;
        }

        @Override
        public AzLinkPlugin getPlugin() {
            return null;
        }

        @Override
        public LoggerAdapter getLoggerAdapter() {
            return this.logger;
        }

        @Override
        public SchedulerAdapter getSchedulerAdapter() {
            return this.scheduler;
        }

        @Override
        public PlatformType getPlatformType() {
            return PlatformType.NEOFORGE;
        }

        @Override
        public PlatformInfo getPlatformInfo() {
            return new PlatformInfo("test", "1.0");
        }

        @Override
        public String getPluginVersion() {
            return "test";
        }

        @Override
        public Path getDataDirectory() {
            return this.dataDir;
        }

        @Override
        public Stream<CommandSender> getOnlinePlayers() {
            return Stream.empty();
        }

        @Override
        public int getMaxPlayers() {
            return 0;
        }

        @Override
        public void dispatchConsoleCommand(String command) {
        }
    }

    private static final class ImmediateScheduler implements SchedulerAdapter {
        private final Executor executor = Runnable::run;

        @Override
        public Executor syncExecutor() {
            return this.executor;
        }

        @Override
        public Executor asyncExecutor() {
            return this.executor;
        }

        @Override
        public CancellableTask scheduleAsyncLater(Runnable runnable, long delay, TimeUnit unit) {
            runnable.run();
            return () -> {
            };
        }

        @Override
        public CancellableTask scheduleAsyncRepeating(Runnable runnable, long delay, long interval, TimeUnit unit) {
            return () -> {
            };
        }
    }
}
