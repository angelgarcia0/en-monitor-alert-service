package com.energymonitor.alert.domain.model;

/**
 * Lifecycle of an alert.
 *
 * <p>Maps to the {@code ENUM('PENDING', 'RESOLVED')} column of {@code alert}.
 */
public enum AlertStatus {
    PENDING,
    RESOLVED
}
