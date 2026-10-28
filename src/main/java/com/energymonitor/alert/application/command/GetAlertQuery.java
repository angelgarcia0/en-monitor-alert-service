package com.energymonitor.alert.application.command;

/**
 * Input of {@code GetAlert}: a single alert by identifier.
 *
 * @param alertId identifier of the alert
 */
public record GetAlertQuery(String alertId) {
}
