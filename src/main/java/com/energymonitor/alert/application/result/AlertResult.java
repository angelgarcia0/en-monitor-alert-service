package com.energymonitor.alert.application.result;

import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.time.Instant;

/**
 * Result of an alert query.
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
public record AlertResult(String idAlert, String homeId, String deviceId, AlertType type,
                          String messageKey, Instant dateTime, AlertStatus alertStatus,
                          String consumptionLevelId, String measurementId) {

    /**
     * Creates a result from a domain object.
     *
     * @param alert the domain object
     * @return the result
     */
    public static AlertResult from(Alert alert) {
        return new AlertResult(alert.idAlert(), alert.homeId(), alert.deviceId(), alert.type(),
                alert.messageKey(), alert.dateTime(), alert.alertStatus(),
                alert.consumptionLevelId(), alert.measurementId());
    }
}
