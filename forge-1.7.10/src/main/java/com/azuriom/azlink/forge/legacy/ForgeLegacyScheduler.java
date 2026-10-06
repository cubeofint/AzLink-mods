package com.azuriom.azlink.forge.legacy;

import com.azuriom.azlink.common.scheduler.CancellableTask;
import com.azuriom.azlink.common.scheduler.JavaSchedulerAdapter;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Async via shared {@link JavaSchedulerAdapter}; sync work is drained on the server tick.
 */
public final class ForgeLegacyScheduler implements SchedulerAdapter {

    private final Queue<Runnable> syncQueue = new ArrayDeque<Runnable>();
    private final SchedulerAdapter asyncDelegate;
    private final Executor syncExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            synchronized (syncQueue) {
                syncQueue.add(command);
            }
        }
    };

    public ForgeLegacyScheduler() {
        this.asyncDelegate = new JavaSchedulerAdapter(this.syncExecutor);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        while (true) {
            Runnable next;
            synchronized (this.syncQueue) {
                next = this.syncQueue.poll();
            }
            if (next == null) {
                return;
            }
            try {
                next.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public Executor syncExecutor() {
        return this.syncExecutor;
    }

    @Override
    public Executor asyncExecutor() {
        return this.asyncDelegate.asyncExecutor();
    }

    @Override
    public CancellableTask scheduleAsyncLater(Runnable runnable, long delay, java.util.concurrent.TimeUnit unit) {
        return this.asyncDelegate.scheduleAsyncLater(runnable, delay, unit);
    }

    @Override
    public CancellableTask scheduleAsyncRepeating(Runnable runnable, long delay, long interval,
                                                  java.util.concurrent.TimeUnit unit) {
        return this.asyncDelegate.scheduleAsyncRepeating(runnable, delay, interval, unit);
    }

    @Override
    public void shutdown() throws Exception {
        this.asyncDelegate.shutdown();
    }
}
