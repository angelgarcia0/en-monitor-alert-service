package com.energymonitor.alert.domain.model;

/**
 * Risk classification of a consumption value: the names of the {@code consumption_level}
 * catalogue of the measurement service. A reading classified HIGH or CRITICAL raises a THRESHOLD
 * alert.
 */
public enum RiskConsumption {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
