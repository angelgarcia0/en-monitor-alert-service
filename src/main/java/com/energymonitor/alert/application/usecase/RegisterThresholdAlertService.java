package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.port.in.RegisterThresholdAlert;
import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.ConsumptionLevelLookupPort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.application.result.AlertResult;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertRaised;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import com.energymonitor.alert.domain.model.RiskConsumption;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates a recorded measurement and raises a THRESHOLD alert when the reading falls
 * into a HIGH or CRITICAL consumption level.
 *
 * <p>This is the class diagram's {@code AlertObserver} + {@code AlertFactory.createFromMeasurement(m, risk)}
 * pair: the observer arrives as the {@code MeasurementRecordedListener} adapter, the
 * classification as the {@link ConsumptionLevelLookupPort}, and the factory as
 * {@link Alert#threshold}.
 *
 * <p><strong>Alerting rule:</strong> only HIGH and CRITICAL readings raise an alert, at most one
 * pending per device and level. A LOW or MEDIUM reading resolves the pending ones of that device:
 * the alert closes itself when consumption is back to normal. The message key follows the
 * level: {@code alert.threshold.high} or {@code alert.threshold.critical}.
 *
 * <p><strong>Home resolution:</strong> the home comes from the device through
 * {@link HomeLookupPort}. A device the device service links to no home resolves nothing and the
 * reading is skipped — no alert is raised against a guessed home.
 *
 * <p><strong>Transactional boundary:</strong> the alert and its {@code alert.raised} message are
 * written in the transaction of the caller, the consumer of the reading, so neither is stored
 * without the other. This service contains no Spring annotations.
 */
public class RegisterThresholdAlertService implements RegisterThresholdAlert {

    private static final Map<RiskConsumption, String> MESSAGE_KEY_BY_RISK = Map.of(
            RiskConsumption.HIGH, "alert.threshold.high",
            RiskConsumption.CRITICAL, "alert.threshold.critical");

    private final AlertPersistencePort alertPort;
    private final ConsumptionLevelLookupPort levels;
    private final HomeLookupPort homes;
    private final IdentifierGeneratorPort identifiers;
    private final AlertEventPort events;

    public RegisterThresholdAlertService(AlertPersistencePort alertPort,
                                         ConsumptionLevelLookupPort levels,
                                         HomeLookupPort homes,
                                         IdentifierGeneratorPort identifiers,
                                         AlertEventPort events) {
        this.alertPort = alertPort;
        this.levels = levels;
        this.homes = homes;
        this.identifiers = identifiers;
        this.events = events;
    }

    @Override
    public Optional<AlertResult> register(RegisterThresholdAlertCommand command) {
        var level = levels.classify(command.activePower());
        if (level.isEmpty()) {
            return Optional.empty();
        }
        String messageKey = MESSAGE_KEY_BY_RISK.get(level.get().name());
        var homeId = homes.findHomeIdByDeviceId(command.deviceId());
        if (homeId.isEmpty()) {
            // A device without a home has nobody to tell
            return Optional.empty();
        }
        if (messageKey == null) {
            // LOW and MEDIUM readings never alert, and they mean consumption is back to normal:
            // the threshold alerts still pending for this device are resolved by the system.
            resolvePendingThresholdAlerts(homeId.get(), command.deviceId());
            return Optional.empty();
        }

        // One reading per minute would otherwise raise one alert per minute: while an alert with
        // the same level is still PENDING for the device, the new reading adds nothing.
        boolean alreadyPending = alertPort.listActiveByHome(homeId.get(), AlertStatus.PENDING).stream()
                .anyMatch(a -> a.type() == AlertType.THRESHOLD && command.deviceId().equals(a.deviceId())
                        && messageKey.equals(a.messageKey()));
        if (alreadyPending) {
            return Optional.empty();
        }

        Alert alert = Alert.threshold(identifiers.generate(), homeId.get(), command.deviceId(),
                messageKey, command.dateTime(), level.get().idConsumptionLevel(),
                command.measurementId());
        alertPort.save(alert);

        events.publish(new AlertRaised(alert.idAlert(), alert.homeId(), alert.deviceId(),
                alert.type(), alert.messageKey(), alert.dateTime(),
                alert.consumptionLevelId(), alert.measurementId()));

        return Optional.of(AlertResult.from(alert));
    }

    private void resolvePendingThresholdAlerts(String homeId, String deviceId) {
        alertPort.listActiveByHome(homeId, AlertStatus.PENDING).stream()
                .filter(a -> a.type() == AlertType.THRESHOLD && deviceId.equals(a.deviceId()))
                .forEach(alert -> {
                    alert.resolve();
                    alertPort.save(alert);
                });
    }
}
