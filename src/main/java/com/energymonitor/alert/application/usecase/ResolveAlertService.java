package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.command.ResolveAlertCommand;
import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.port.in.ResolveAlert;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.result.AlertResult;
import com.energymonitor.alert.domain.model.AlertType;

/**
 * Marks an alert as resolved.
 *
 * <p>A single write, so no transaction boundary. This service contains no Spring
 * annotations.
 */
public class ResolveAlertService implements ResolveAlert {

    private final AlertPersistencePort alertPort;

    public ResolveAlertService(AlertPersistencePort alertPort) {
        this.alertPort = alertPort;
    }

    @Override
    public AlertResult resolve(ResolveAlertCommand command) {
        var alert = alertPort.findActive(command.alertId())
                .orElseThrow(() -> new AlertNotFoundException("no alert " + command.alertId()));
        // THRESHOLD and CONNECTIVITY alerts close themselves when the system sees the problem
        // gone (consumption back to normal, device reporting again). Only an informational
        // alert can be dismissed by a person: for it, "resolved" means "read".
        if (alert.type() != AlertType.DEVICE) {
            throw new IllegalStateException("alert " + alert.idAlert() + " is resolved by the system, not by hand");
        }
        alert.resolve();
        alertPort.save(alert);
        return AlertResult.from(alert);
    }
}
