package com.energymonitor.alert.domain.model;

/**
 * What triggered an alert.
 *
 * <p>Maps to the {@code ENUM('THRESHOLD', 'CONNECTIVITY', 'DEVICE', 'LIMIT')} column of
 * {@code alert}, and rides in the {@code alert.raised} message the notification service reads.
 */
public enum AlertType {
    /** A consumption reading crossed into a HIGH or CRITICAL consumption level. */
    THRESHOLD,
    /** A device stopped reporting. */
    CONNECTIVITY,
    /** Informational: something changed in the home's devices, e.g. a module was linked. */
    DEVICE,
    /** The home reached its daily or monthly consumption limit. */
    LIMIT
}
