package com.energymonitor.alert.application.port.in;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.result.AlertResult;
import java.util.Optional;

/**
 * Compares a reading with what its device usually consumes and raises an alert when it is far
 * above it.
 */
public interface RegisterAnomalyAlert {

    /**
     * @param command the reading
     * @return the alert raised, empty when the consumption is normal or already alerted
     */
    Optional<AlertResult> evaluate(RegisterThresholdAlertCommand command);
}
