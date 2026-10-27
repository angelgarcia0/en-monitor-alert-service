package com.energymonitor.alert.adapter.out.persistence.mapper;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertEntity;
import com.energymonitor.alert.adapter.out.persistence.support.Instants;
import com.energymonitor.alert.domain.model.Alert;
import org.springframework.stereotype.Component;

/**
 * Converts {@link Alert} to and from {@link AlertEntity}.
 */
@Component
public class AlertMapper {

    public AlertEntity toEntity(Alert alert) {
        return new AlertEntity(alert.idAlert(), alert.homeId(), alert.deviceId(), alert.type(),
                alert.messageKey(), Instants.truncate(alert.dateTime()), alert.alertStatus(),
                alert.consumptionLevelId(), alert.measurementId());
    }

    public void applyTo(AlertEntity entity, Alert alert) {
        entity.setHomeId(alert.homeId());
        entity.setDeviceId(alert.deviceId());
        entity.setType(alert.type());
        entity.setMessageKey(alert.messageKey());
        entity.setDateTime(Instants.truncate(alert.dateTime()));
        entity.setAlertStatus(alert.alertStatus());
        entity.setConsumptionLevelId(alert.consumptionLevelId());
        entity.setMeasurementId(alert.measurementId());
    }

    public Alert toDomain(AlertEntity entity) {
        return new Alert(entity.getIdAlert(), entity.getHomeId(), entity.getDeviceId(),
                entity.getType(), entity.getMessageKey(), entity.getDateTime(),
                entity.getAlertStatus(), entity.getConsumptionLevelId(), entity.getMeasurementId());
    }
}
