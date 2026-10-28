package com.energymonitor.alert.application.port.out;

import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Output port for persisting and querying alerts.
 */
public interface AlertPersistencePort {

    /**
     * Saves an alert (insert or update).
     *
     * @param alert the domain object
     * @return the same domain object
     */
    Alert save(Alert alert);

    /**
     * Finds an active alert by identifier.
     *
     * @param idAlert the identifier
     * @return the alert, empty when soft-deleted or missing
     */
    Optional<Alert> findActive(String idAlert);

    /**
     * Lists the active alerts of a home.
     *
     * @param homeId the home identifier
     * @param status when {@code null}, alerts of every status are returned
     * @return the alerts, most recent first
     */
    List<Alert> listActiveByHome(String homeId, AlertStatus status);

    /** Soft-deletes one alert. */
    void delete(String idAlert);

    /** Soft-deletes every RESOLVED alert of a home. @return how many */
    int deleteResolved(String homeId);

    /**
     * Whether an alert with this key was raised for the device since an instant, deleted ones
     * included: an alert the user already read and deleted must not come back.
     */
    boolean existsForDeviceSince(String deviceId, String messageKey, Instant since);
}
