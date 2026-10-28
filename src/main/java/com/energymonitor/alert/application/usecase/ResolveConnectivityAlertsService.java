package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.port.in.ResolveConnectivityAlerts;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.util.List;

/**
 * Resolves the pending CONNECTIVITY alerts of a device once it is back online.
 *
 * <p>Few writes, each independent: no transaction. This service contains no Spring annotations.
 */
public class ResolveConnectivityAlertsService implements ResolveConnectivityAlerts {

    private final AlertPersistencePort alertPort;
    private final HomeLookupPort homes;

    public ResolveConnectivityAlertsService(AlertPersistencePort alertPort, HomeLookupPort homes) {
        this.alertPort = alertPort;
        this.homes = homes;
    }

    @Override
    public int resolveFor(String deviceId) {
        return homes.findHomeIdByDeviceId(deviceId)
                .map(homeId -> {
                    List<Alert> pending = alertPort.listActiveByHome(homeId, AlertStatus.PENDING).stream()
                            .filter(a -> a.type() == AlertType.CONNECTIVITY && deviceId.equals(a.deviceId()))
                            .toList();
                    pending.forEach(alert -> {
                        alert.resolve();
                        alertPort.save(alert);
                    });
                    return pending.size();
                })
                .orElse(0);
    }
}
