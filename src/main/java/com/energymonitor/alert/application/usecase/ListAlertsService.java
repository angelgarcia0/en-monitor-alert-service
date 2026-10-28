package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.command.ListAlertsQuery;
import com.energymonitor.alert.application.port.in.ListAlerts;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.result.AlertResult;
import java.util.List;

/**
 * Lists the alerts of a home, most recent first, optionally filtered by status.
 *
 * <p>Read-only: no transaction boundary. This service contains no Spring annotations.
 */
public class ListAlertsService implements ListAlerts {

    private final AlertPersistencePort alertPort;

    public ListAlertsService(AlertPersistencePort alertPort) {
        this.alertPort = alertPort;
    }

    @Override
    public List<AlertResult> list(ListAlertsQuery query) {
        return alertPort.listActiveByHome(query.homeId(), query.status()).stream()
                .map(AlertResult::from)
                .toList();
    }
}
