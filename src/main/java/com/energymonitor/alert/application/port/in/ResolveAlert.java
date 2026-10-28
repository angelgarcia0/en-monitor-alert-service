package com.energymonitor.alert.application.port.in;

import com.energymonitor.alert.application.command.ResolveAlertCommand;
import com.energymonitor.alert.application.result.AlertResult;

/**
 * Input port for resolving an alert.
 */
public interface ResolveAlert {

    /**
     * @param command the alert to resolve
     * @return the resolved alert
     * @throws com.energymonitor.alert.application.exception.AlertNotFoundException
     *         when the alert does not exist
     * @throws IllegalStateException when the alert is already resolved
     */
    AlertResult resolve(ResolveAlertCommand command);
}
