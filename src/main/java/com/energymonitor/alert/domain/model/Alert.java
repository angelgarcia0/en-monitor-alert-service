package com.energymonitor.alert.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root of an alert: the record that something requires attention.
 *
 * <p><strong>Cross-context references:</strong> {@code homeId}, {@code deviceId},
 * {@code consumptionLevelId} and {@code measurementId} reference other bounded contexts.
 * Per the MER they generate no SQL foreign key and are validated at application level;
 * they stay plain {@code String}s because those aggregates cannot be imported here.
 *
 * <p><strong>Type consistency (MER note on {@code alert}):</strong>
 * <ul>
 *   <li>THRESHOLD alerts must carry {@code consumptionLevelId} and {@code measurementId}
 *       (ALERT-INV-007).</li>
 *   <li>CONNECTIVITY alerts must carry neither (ALERT-INV-008).</li>
 * </ul>
 * The rule is enforced here, in the domain, so no persistence path can bypass it.
 * Prefer the {@link #threshold} and {@link #connectivity} factories: they make an
 * inconsistent alert unrepresentable by construction.
 *
 * <p>Invariants:
 * <ul>
 *   <li>ALERT-INV-001: {@code idAlert} is required, {@code VARCHAR(10)}</li>
 *   <li>ALERT-INV-002: {@code homeId} is required, {@code VARCHAR(10)}</li>
 *   <li>ALERT-INV-003: {@code deviceId} is optional, {@code VARCHAR(10)}</li>
 *   <li>ALERT-INV-004: {@code type} is required</li>
 *   <li>ALERT-INV-005: {@code messageKey} is required, {@code VARCHAR(100)}</li>
 *   <li>ALERT-INV-006: {@code dateTime} is required</li>
 *   <li>ALERT-INV-007: THRESHOLD ⇒ {@code consumptionLevelId} and {@code measurementId} populated</li>
 *   <li>ALERT-INV-008: CONNECTIVITY ⇒ {@code consumptionLevelId} and {@code measurementId} NULL</li>
 *   <li>ALERT-INV-009: alerts are created PENDING</li>
 * </ul>
 *
 * <p>Maps to the {@code alert} table.
 */
public class Alert {

    private final String idAlert;
    private final String homeId;
    private final String deviceId;
    private final AlertType type;
    private final String messageKey;
    private final Instant dateTime;
    private AlertStatus alertStatus;
    private final String consumptionLevelId;
    private final String measurementId;

    /**
     * Rehydrates an alert enforcing the type-consistency rules. Prefer the
     * {@link #threshold} and {@link #connectivity} factories for new alerts.
     *
     * @param idAlert            identifier, {@code VARCHAR(10)}
     * @param homeId             identifier of the owning home, {@code VARCHAR(10)}
     * @param deviceId           identifier of the involved device, may be {@code null}
     * @param type               what triggered the alert
     * @param messageKey         i18n message key, {@code VARCHAR(100)}
     * @param dateTime           business timestamp of the event
     * @param alertStatus        lifecycle state
     * @param consumptionLevelId referenced consumption level; required for THRESHOLD,
     *                           forbidden for CONNECTIVITY
     * @param measurementId      referenced measurement; required for THRESHOLD,
     *                           forbidden for CONNECTIVITY
     */
    public Alert(String idAlert, String homeId, String deviceId, AlertType type,
                 String messageKey, Instant dateTime, AlertStatus alertStatus,
                 String consumptionLevelId, String measurementId) {
        this.idAlert = Preconditions.text(idAlert, 10, "idAlert");
        this.homeId = Preconditions.text(homeId, 10, "homeId");
        this.deviceId = Preconditions.optionalText(deviceId, 10, "deviceId");
        this.type = Preconditions.notNull(type, "type");
        this.messageKey = Preconditions.text(messageKey, 100, "messageKey");
        this.dateTime = Preconditions.notNull(dateTime, "dateTime");
        this.alertStatus = Preconditions.notNull(alertStatus, "alertStatus");
        if (type == AlertType.THRESHOLD) {
            this.consumptionLevelId = Preconditions.text(consumptionLevelId, 10, "consumptionLevelId");
            this.measurementId = Preconditions.text(measurementId, 10, "measurementId");
        } else {
            if (consumptionLevelId != null || measurementId != null) {
                throw new IllegalArgumentException(
                        "CONNECTIVITY alerts must not carry consumptionLevelId nor measurementId");
            }
            this.consumptionLevelId = null;
            this.measurementId = null;
        }
    }

    /**
     * Raises a THRESHOLD alert: a consumption reading crossed into a risky level.
     *
     * @return a new PENDING alert
     */
    public static Alert threshold(String idAlert, String homeId, String deviceId,
                                  String messageKey, Instant dateTime,
                                  String consumptionLevelId, String measurementId) {
        return new Alert(idAlert, homeId, deviceId, AlertType.THRESHOLD, messageKey, dateTime,
                AlertStatus.PENDING, consumptionLevelId, measurementId);
    }

    /**
     * Raises a CONNECTIVITY alert: a device stopped reporting.
     *
     * @return a new PENDING alert
     */
    public static Alert connectivity(String idAlert, String homeId, String deviceId,
                                     String messageKey, Instant dateTime) {
        return new Alert(idAlert, homeId, deviceId, AlertType.CONNECTIVITY, messageKey, dateTime,
                AlertStatus.PENDING, null, null);
    }

    /**
     * Raises a DEVICE alert: informational, e.g. a module was linked to the home.
     *
     * @return a new PENDING alert; resolving it means "seen"
     */
    public static Alert device(String idAlert, String homeId, String deviceId, String messageKey,
                               Instant dateTime) {
        return new Alert(idAlert, homeId, deviceId, AlertType.DEVICE, messageKey, dateTime,
                AlertStatus.PENDING, null, null);
    }

    /**
     * Raises a LIMIT alert: the home's consumption reached the limit its owner set for the day or
     * the month. Home-scoped, so it carries no device.
     *
     * @return a new PENDING alert; the system resolves it when consumption is under the limit again
     */
    public static Alert limit(String idAlert, String homeId, String messageKey, Instant dateTime) {
        return new Alert(idAlert, homeId, null, AlertType.LIMIT, messageKey, dateTime,
                AlertStatus.PENDING, null, null);
    }

    /** @return the identifier */
    public String idAlert() {
        return idAlert;
    }

    /** @return identifier of the owning home */
    public String homeId() {
        return homeId;
    }

    /** @return identifier of the involved device, {@code null} when home-scoped */
    public String deviceId() {
        return deviceId;
    }

    /** @return what triggered the alert */
    public AlertType type() {
        return type;
    }

    /** @return the i18n message key */
    public String messageKey() {
        return messageKey;
    }

    /** @return business timestamp of the event */
    public Instant dateTime() {
        return dateTime;
    }

    /** @return the lifecycle state */
    public AlertStatus alertStatus() {
        return alertStatus;
    }

    /** @return the referenced consumption level for THRESHOLD alerts, {@code null} otherwise */
    public String consumptionLevelId() {
        return consumptionLevelId;
    }

    /** @return the referenced measurement for THRESHOLD alerts, {@code null} otherwise */
    public String measurementId() {
        return measurementId;
    }

    /** @return {@code true} while the alert has not been resolved */
    public boolean isPending() {
        return alertStatus == AlertStatus.PENDING;
    }

    /**
     * Marks the alert as resolved (the class diagram's {@code resolveAlert()}).
     *
     * @throws IllegalStateException when the alert is already resolved
     */
    public void resolve() {
        if (!isPending()) {
            throw new IllegalStateException("alert " + idAlert + " is already resolved");
        }
        this.alertStatus = AlertStatus.RESOLVED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Alert that)) {
            return false;
        }
        return idAlert.equals(that.idAlert);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idAlert);
    }

    @Override
    public String toString() {
        return "Alert{idAlert='" + idAlert + "', homeId='" + homeId + "', type=" + type + ", alertStatus=" + alertStatus + ", dateTime=" + dateTime + "}";
    }
}
