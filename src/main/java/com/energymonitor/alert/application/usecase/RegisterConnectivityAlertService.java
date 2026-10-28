package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.command.RegisterConnectivityAlertCommand;
import com.energymonitor.alert.application.port.in.RegisterConnectivityAlert;
import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.application.result.AlertResult;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertRaised;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.util.Optional;

/**
 * Raises {@code alert.connectivity.offline} when the device service reports a device lost.
 *
 * <p>A device that keeps dropping does not flood the inbox: while a CONNECTIVITY alert of the
 * same device is still PENDING, no new one is raised. A device without a home is skipped.
 *
 * <p>A single write, so no transaction is needed. This service contains no Spring annotations.
 */
public class RegisterConnectivityAlertService implements RegisterConnectivityAlert {

    static final String MESSAGE_KEY = "alert.connectivity.offline";

    private final AlertPersistencePort alertPort;
    private final HomeLookupPort homes;
    private final IdentifierGeneratorPort identifiers;
    private final AlertEventPort events;

    public RegisterConnectivityAlertService(AlertPersistencePort alertPort, HomeLookupPort homes,
                                            IdentifierGeneratorPort identifiers, AlertEventPort events) {
        this.alertPort = alertPort;
        this.homes = homes;
        this.identifiers = identifiers;
        this.events = events;
    }

    @Override
    public Optional<AlertResult> register(RegisterConnectivityAlertCommand command) {
        Optional<String> homeId = homes.findHomeIdByDeviceId(command.deviceId());
        if (homeId.isEmpty()) {
            return Optional.empty();
        }
        boolean alreadyPending = alertPort.listActiveByHome(homeId.get(), AlertStatus.PENDING).stream()
                .anyMatch(a -> a.type() == AlertType.CONNECTIVITY && command.deviceId().equals(a.deviceId()));
        if (alreadyPending) {
            return Optional.empty();
        }
        Alert alert = Alert.connectivity(identifiers.generate(), homeId.get(), command.deviceId(), MESSAGE_KEY,
                command.dateTime());
        alertPort.save(alert);
        events.publish(new AlertRaised(alert.idAlert(), alert.homeId(), alert.deviceId(), alert.type(),
                alert.messageKey(), alert.dateTime(), alert.consumptionLevelId(), alert.measurementId()));
        return Optional.of(AlertResult.from(alert));
    }
}
