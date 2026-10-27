package com.energymonitor.alert.adapter.in.web.dto;

import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.time.Instant;

/**
 * Response DTO for an alert.
 *
 * @param idAlert            identifier
 * @param homeId             identifier of the owning home
 * @param deviceId           identifier of the involved device, {@code null} when home-scoped
 * @param type               what triggered the alert
 * @param messageKey         i18n message key
 * @param dateTime           business timestamp of the event
 * @param alertStatus        lifecycle state
 * @param consumptionLevelId referenced consumption level, populated for THRESHOLD alerts
 * @param measurementId      referenced measurement, populated for THRESHOLD alerts
 */
public record AlertResponse(String idAlert, String homeId, String deviceId, AlertType type,
                            String messageKey, Instant dateTime, AlertStatus alertStatus,
                            String consumptionLevelId, String measurementId) {
}
