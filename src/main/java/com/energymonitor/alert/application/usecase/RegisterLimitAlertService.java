package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.port.in.RegisterLimitAlert;
import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.ConsumptionLimitPort;
import com.energymonitor.alert.application.port.out.DeviceLookupPort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertRaised;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Raises a LIMIT alert when the home's consumption of the day (or the month, whichever limit the
 * owner chose) reaches that limit, and resolves it when it is under the limit again: a new day or
 * month, or a higher limit.
 *
 * <p>Evaluated on every reading of any device of the home. At most one LIMIT alert is pending per
 * home: while it is, a new reading over the limit adds nothing. The message key says which limit:
 * {@code alert.limit.daily} or {@code alert.limit.monthly}.
 *
 * <p>This service contains no Spring annotations.
 */
public class RegisterLimitAlertService implements RegisterLimitAlert {

    static final String DAILY_KEY = "alert.limit.daily";
    static final String MONTHLY_KEY = "alert.limit.monthly";

    private final AlertPersistencePort alertPort;
    private final HomeLookupPort homes;
    private final DeviceLookupPort devices;
    private final ConsumptionLimitPort limits;
    private final IdentifierGeneratorPort identifiers;
    private final AlertEventPort events;

    public RegisterLimitAlertService(AlertPersistencePort alertPort, HomeLookupPort homes,
                                     DeviceLookupPort devices, ConsumptionLimitPort limits,
                                     IdentifierGeneratorPort identifiers, AlertEventPort events) {
        this.alertPort = alertPort;
        this.homes = homes;
        this.devices = devices;
        this.limits = limits;
        this.identifiers = identifiers;
        this.events = events;
    }

    @Override
    public void evaluate(String deviceId, Instant dateTime) {
        var homeId = homes.findHomeIdByDeviceId(deviceId).orElse(null);
        if (homeId == null) {
            return;
        }
        List<Alert> pending = alertPort.listActiveByHome(homeId, AlertStatus.PENDING).stream()
                .filter(a -> a.type() == AlertType.LIMIT)
                .toList();
        var limit = limits.limitOf(homeId).filter(l -> l.kwh() > 0).orElse(null);
        if (limit == null) {
            pending.forEach(this::resolve);
            return;
        }
        String key = limit.monthly() ? MONTHLY_KEY : DAILY_KEY;
        // The period is the current one: a late sample queued by the module still counts in the
        // energy, but must not reopen yesterday.
        Instant now = limits.now();
        Instant periodStart = periodStart(now, limits.zone(), limit.monthly());
        double consumed = limits.energy(devices.deviceIdsOfHome(homeId), periodStart, now.plusSeconds(1));
        boolean over = consumed >= limit.kwh();

        boolean alreadyRaised = false;
        for (Alert alert : pending) {
            // Raised for the current period and limit kind, and still over it: keep it.
            if (over && alert.messageKey().equals(key) && !alert.dateTime().isBefore(periodStart)) {
                alreadyRaised = true;
            } else {
                resolve(alert);
            }
        }
        if (!over || alreadyRaised) {
            return;
        }
        Alert alert = Alert.limit(identifiers.generate(), homeId, key, now);
        alertPort.save(alert);
        events.publish(new AlertRaised(alert.idAlert(), alert.homeId(), null, alert.type(),
                alert.messageKey(), alert.dateTime(), null, null));
    }

    private void resolve(Alert alert) {
        alert.resolve();
        alertPort.save(alert);
    }

    static Instant periodStart(Instant now, ZoneId zone, boolean monthly) {
        ZonedDateTime local = now.atZone(zone);
        return (monthly ? local.toLocalDate().withDayOfMonth(1) : local.toLocalDate())
                .atStartOfDay(zone).toInstant();
    }
}
