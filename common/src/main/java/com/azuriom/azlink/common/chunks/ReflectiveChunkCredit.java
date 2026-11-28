package com.azuriom.azlink.common.chunks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional game APIs; no remote-console command is executed. */
public final class ReflectiveChunkCredit {
    private ReflectiveChunkCredit() {}

    public static ChunkLoaderBackend find(String game) {
        try {
            final Method method;
            if ("gtnh".equals(game)) {
                method = Class.forName("coint.cluster.DonationDelivery").getMethod("creditChunks",
                        UUID.class, String.class, int.class, long.class, String.class);
            } else if ("gto".equals(game)) {
                method = Class.forName("dev.ftb.mods.ftbchunks.data.PaidChunkLoaders").getMethod("credit",
                        UUID.class, int.class, long.class, String.class);
            } else return null;
            return purchase -> {
                try {
                    if ("gtnh".equals(game)) method.invoke(null, purchase.player, purchase.name,
                            purchase.quantity, Math.multiplyExact(purchase.expiresAt, 1000L), purchase.receipt);
                    else method.invoke(null, purchase.player, purchase.quantity, purchase.expiresAt, purchase.receipt);
                } catch (InvocationTargetException failed) {
                    Throwable cause = failed.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    throw new IllegalStateException("Chunk-loader persistence outcome unknown", cause);
                } catch (ReflectiveOperationException failed) {
                    throw new IllegalStateException("Chunk-loader backend invocation failed", failed);
                }
            };
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }
}
