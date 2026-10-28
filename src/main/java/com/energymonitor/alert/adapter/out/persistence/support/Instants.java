package com.energymonitor.alert.adapter.out.persistence.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Storage-precision helpers for the Alerts persistence adapter.
 */
public final class Instants {

    public static final ChronoUnit STORAGE_PRECISION = ChronoUnit.SECONDS;

    private Instants() {
    }

    /**
     * Truncates an instant to the precision the schema can actually store.
     *
     * @param instant the value to store, may be {@code null}
     * @return the truncated value, or {@code null} when the input was {@code null}
     */
    public static Instant truncate(Instant instant) {
        return instant == null ? null : instant.truncatedTo(STORAGE_PRECISION);
    }

    /**
     * Current instant already truncated to the storage precision.
     *
     * @return a value safe to persist
     */
    public static Instant now() {
        return truncate(Instant.now());
    }
}
