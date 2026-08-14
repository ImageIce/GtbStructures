package com.gtbstructures.stats;

import java.util.Locale;
import java.util.UUID;

/** Canonical Minecraft account UUID parsing for persisted stats ownership. */
public final class PlayerUuid {
    private PlayerUuid() {}

    public static String normalize(Object value) {
        return value == null ? null : normalize(value.toString());
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String clean = value.trim();
        if (clean.matches("(?i)[0-9a-f]{32}")) {
            clean = clean.substring(0, 8) + "-" + clean.substring(8, 12)
                    + "-" + clean.substring(12, 16) + "-" + clean.substring(16, 20)
                    + "-" + clean.substring(20);
        }
        try {
            return UUID.fromString(clean).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
