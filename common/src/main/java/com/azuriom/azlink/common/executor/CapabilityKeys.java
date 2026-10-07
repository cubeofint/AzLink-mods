package com.azuriom.azlink.common.executor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shop capability keys on the poll wire. Site validation rejects {@code *}, {@code meta:...}
 * and other non-key tokens. LuckPerms meta nodes stay internal to {@code PrivilegePlan}.
 */
public final class CapabilityKeys {

    /**
     * Documented poll format: lowercase token, optional {@code . _ -} after the first letter.
     */
    public static final String WIRE_REGEX = "^[a-z][a-z0-9._-]{0,63}$";

    private static final Pattern WIRE = Pattern.compile(WIRE_REGEX);

    private static final List<String> DEFAULT_ADVERTISED = Collections.unmodifiableList(Arrays.asList(
            "fly",
            "claim_chunks",
            "cointcore.bonus_claim_chunks",
            "cointcore.bonus_forceload_chunks"
    ));

    private CapabilityKeys() {
    }

    public static List<String> defaultAdvertised() {
        return DEFAULT_ADVERTISED;
    }

    public static boolean isWireSafe(String key) {
        return key != null && WIRE.matcher(key).matches();
    }

    public static Resolution resolve(List<String> configured, boolean luckPermsAcceptAllWhenEmpty) {
        List<String> source = configured == null ? Collections.<String>emptyList() : configured;
        boolean star = false;
        Set<String> sanitized = new LinkedHashSet<String>();
        for (String raw : source) {
            if (raw == null) {
                continue;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            if ("*".equals(key)) {
                star = true;
                continue;
            }
            if (key.startsWith("meta:")) {
                continue;
            }
            if (isWireSafe(key)) {
                sanitized.add(key);
            }
        }

        boolean acceptAll = star || (luckPermsAcceptAllWhenEmpty && sanitized.isEmpty());
        List<String> advertised = sanitized.isEmpty() && acceptAll
                ? new ArrayList<String>(DEFAULT_ADVERTISED)
                : new ArrayList<String>(sanitized);
        return new Resolution(advertised, acceptAll);
    }

    public static final class Resolution {
        private final List<String> advertised;
        private final boolean acceptAll;

        Resolution(List<String> advertised, boolean acceptAll) {
            this.advertised = advertised;
            this.acceptAll = acceptAll;
        }

        public List<String> getAdvertised() {
            return this.advertised;
        }

        public boolean isAcceptAll() {
            return this.acceptAll;
        }
    }
}
