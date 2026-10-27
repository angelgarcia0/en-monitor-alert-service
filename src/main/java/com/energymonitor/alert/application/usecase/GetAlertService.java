package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.command.GetAlertQuery;
import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.port.in.GetAlert;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.result.AlertResult;

/**
 * Reads a single alert.
 *
 * <p>Read-only: no transaction boundary. This service contains no Spring annotations.
 */
public class GetAlertService implements GetAlert {

    private final AlertPersistencePort alertPort;

    public GetAlertService(AlertPersistencePort alertPort) {
        this.alertPort = alertPort;
    }

    @Override
    public AlertResult get(GetAlertQuery query) {
        return alertPort.findActive(query.alertId())
                .map(AlertResult::from)
                .orElseThrow(() -> new AlertNotFoundException("no alert " + query.alertId()));
    }
}
