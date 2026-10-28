package com.energymonitor.alert.application.command;

import com.energymonitor.alert.domain.model.AlertStatus;

/**
 * Input of {@code ListAlerts}: the alerts of a home, optionally filtered by status.
 *
 * @param homeId identifier of the home
 * @param status when {@code null}, alerts of every status are returned
 */
public record ListAlertsQuery(String homeId, AlertStatus status) {
}
