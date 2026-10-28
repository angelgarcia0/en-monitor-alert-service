package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.port.in.RegisterAnomalyAlert;
import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.ConsumptionLevelLookupPort;
import com.energymonitor.alert.application.port.out.DeviceBaselinePort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.application.result.AlertResult;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertRaised;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.util.Optional;

/**
 * Raises an {@code alert.anomaly.device} alert when a device consumes far more than it usually
 * does, even when the reading is still in a low consumption level (a lamp at 400 W is not
 * critical, but it is not normal for a lamp).
 *
 * <p><strong>Rule:</strong> the reading is an anomaly when it is at least {@link #MIN_POWER} watts
 * and at least {@link #FACTOR} times the device's usual power. The usual power is the mean of its
 * hourly average power in the previous days, and only counts with {@link #MIN_HOURS} hours of
 * history and a mean of at least {@link #MIN_BASELINE} watts: a new device, or one that is almost
 * always off, has no usual consumption to compare against.
 *
 * <p>At most one anomaly alert is pending per device. When the reading is back under the line the
 * pending one is resolved by the system. Threshold alerts are separate and are not touched.
 *
 * <p>This service contains no Spring annotations of its own.
 */
public class RegisterAnomalyAlertService implements RegisterAnomalyAlert {

    /** Message key of the alert; the notification service and the clients word it. */
    public static final String MESSAGE_KEY = "alert.anomaly.device";
    /** How many times its usual power a reading must reach. */
    public static final double FACTOR = 2.5;
    /** Below this power nothing is an anomaly, whatever the baseline: it is not worth a notice. */
    public static final double MIN_POWER = 200.0;
    /** Hours of history needed before a device has a usual consumption. */
    public static final int MIN_HOURS = 24;
    /** A baseline under this is a device that is almost always off: nothing to compare with. */
    public static final double MIN_BASELINE = 5.0;

    private final AlertPersistencePort alertPort;
    private final DeviceBaselinePort baselines;
    private final ConsumptionLevelLookupPort levels;
    private final HomeLookupPort homes;
    private final IdentifierGeneratorPort identifiers;
    private final AlertEventPort events;

    public RegisterAnomalyAlertService(AlertPersistencePort alertPort, DeviceBaselinePort baselines,
                                       ConsumptionLevelLookupPort levels, HomeLookupPort homes,
                                       IdentifierGeneratorPort identifiers, AlertEventPort events) {
        this.alertPort = alertPort;
        this.baselines = baselines;
        this.levels = levels;
        this.homes = homes;
        this.identifiers = identifiers;
        this.events = events;
    }

    @Override
    public Optional<AlertResult> evaluate(RegisterThresholdAlertCommand command) {
        var homeId = homes.findHomeIdByDeviceId(command.deviceId());
        if (homeId.isEmpty()) {
            return Optional.empty();
        }
        var baseline = baselines.baselineOf(command.deviceId(), command.dateTime())
                .filter(b -> b.hours() >= MIN_HOURS && b.averagePower() >= MIN_BASELINE);
        boolean anomalous = baseline.isPresent()
                && command.activePower() >= MIN_POWER
                && command.activePower() >= FACTOR * baseline.get().averagePower();

        var pending = alertPort.listActiveByHome(homeId.get(), AlertStatus.PENDING).stream()
                .filter(a -> a.type() == AlertType.THRESHOLD && MESSAGE_KEY.equals(a.messageKey())
                        && command.deviceId().equals(a.deviceId()))
                .toList();
        if (!anomalous) {
            // Back to normal, or no baseline to judge by: whatever was pending closes itself.
            pending.forEach(alert -> {
                alert.resolve();
                alertPort.save(alert);
            });
            return Optional.empty();
        }
        if (!pending.isEmpty()) {
            return Optional.empty();
        }
        var level = levels.classify(command.activePower());
        if (level.isEmpty()) {
            return Optional.empty();
        }
        Alert alert = Alert.threshold(identifiers.generate(), homeId.get(), command.deviceId(),
                MESSAGE_KEY, command.dateTime(), level.get().idConsumptionLevel(),
                command.measurementId());
        alertPort.save(alert);
        events.publish(new AlertRaised(alert.idAlert(), alert.homeId(), alert.deviceId(),
                alert.type(), alert.messageKey(), alert.dateTime(),
                alert.consumptionLevelId(), alert.measurementId()));
        return Optional.of(AlertResult.from(alert));
    }
}
