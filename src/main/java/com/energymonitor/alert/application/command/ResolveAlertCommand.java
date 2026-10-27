package com.energymonitor.alert.application.command;

/**
 * Input of {@code ResolveAlert}: marks an alert as resolved.
 *
 * @param alertId identifier of the alert to resolve
 */
public record ResolveAlertCommand(String alertId) {
}
