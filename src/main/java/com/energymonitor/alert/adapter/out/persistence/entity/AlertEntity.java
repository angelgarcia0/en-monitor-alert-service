package com.energymonitor.alert.adapter.out.persistence.entity;

import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping of the {@code alert} table.
 *
 * <p>{@code home_id}, {@code device_id}, {@code consumption_level_id} and
 * {@code measurement_id} have no SQL foreign keys: they reference other bounded contexts
 * at application level, per the MER conventions. The enums are MySQL {@code ENUM} columns
 * mapped as strings, the same approach {@code user_home.role} already uses.
 */
@Entity
@Table(name = "alert")
public class AlertEntity extends BaseAuditEntity {

    @Id
    @Column(name = "id_alert", nullable = false, length = 10)
    private String idAlert;

    @Column(name = "home_id", nullable = false, length = 10)
    private String homeId;

    @Column(name = "device_id", length = 10)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private AlertType type;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(name = "date_time", nullable = false)
    private Instant dateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_status", nullable = false, length = 8)
    private AlertStatus alertStatus;

    @Column(name = "consumption_level_id", length = 10)
    private String consumptionLevelId;

    @Column(name = "measurement_id", length = 10)
    private String measurementId;

    public AlertEntity() {
    }

    public AlertEntity(String idAlert, String homeId, String deviceId, AlertType type,
                       String messageKey, Instant dateTime, AlertStatus alertStatus,
                       String consumptionLevelId, String measurementId) {
        this.idAlert = idAlert;
        this.homeId = homeId;
        this.deviceId = deviceId;
        this.type = type;
        this.messageKey = messageKey;
        this.dateTime = dateTime;
        this.alertStatus = alertStatus;
        this.consumptionLevelId = consumptionLevelId;
        this.measurementId = measurementId;
    }

    public String getIdAlert() {
        return idAlert;
    }

    public void setIdAlert(String idAlert) {
        this.idAlert = idAlert;
    }

    public String getHomeId() {
        return homeId;
    }

    public void setHomeId(String homeId) {
        this.homeId = homeId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public AlertType getType() {
        return type;
    }

    public void setType(AlertType type) {
        this.type = type;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public Instant getDateTime() {
        return dateTime;
    }

    public void setDateTime(Instant dateTime) {
        this.dateTime = dateTime;
    }

    public AlertStatus getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(AlertStatus alertStatus) {
        this.alertStatus = alertStatus;
    }

    public String getConsumptionLevelId() {
        return consumptionLevelId;
    }

    public void setConsumptionLevelId(String consumptionLevelId) {
        this.consumptionLevelId = consumptionLevelId;
    }

    public String getMeasurementId() {
        return measurementId;
    }

    public void setMeasurementId(String measurementId) {
        this.measurementId = measurementId;
    }
}
