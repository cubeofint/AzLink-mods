package com.azuriom.azlink.common.kits;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/** Optional core-mod integration. A missing executor fails closed, rather than acknowledging an unissued kit. */
public final class ReflectiveKitCredit {
    private ReflectiveKitCredit() {}
    public static int credit(String bridge, UUID player, String kit, int quantity, String receipt) {
        try {
            return ((Number) Class.forName(bridge).getMethod("credit", UUID.class, String.class, int.class, String.class)
                    .invoke(null, player, kit, quantity, receipt)).intValue();
        } catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Kit credit result unknown", cause);
        } catch (ReflectiveOperationException absent) {
            throw new UnsupportedOperationException("Install the matching CointCore kit bridge", absent);
        }
    }
}
