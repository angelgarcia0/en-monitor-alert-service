package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.port.in.DeleteAlerts;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;

/**
 * Soft-deletes resolved alerts. This service contains no Spring annotations.
 */
public class DeleteAlertsService implements DeleteAlerts {

    private final AlertPersistencePort alertPort;

    public DeleteAlertsService(AlertPersistencePort alertPort) {
        this.alertPort = alertPort;
    }

    @Override
    public void delete(String idAlert) {
        var alert = alertPort.findActive(idAlert)
                .orElseThrow(() -> new AlertNotFoundException("no alert " + idAlert));
        if (alert.isPending()) {
            throw new IllegalStateException("alert " + idAlert + " is still pending");
        }
        alertPort.delete(idAlert);
    }

    @Override
    public int deleteResolved(String homeId) {
        return alertPort.deleteResolved(homeId);
    }
}
