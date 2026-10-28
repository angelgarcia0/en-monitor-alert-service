package com.energymonitor.alert.application.port.in;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.result.AlertResult;
import java.util.Optional;

/**
 * Input port for evaluating a measurement and raising a THRESHOLD alert.
 */
public interface RegisterThresholdAlert {

    /**
     * @param command the measurement data
     * @return the raised alert, or empty when the reading does not warrant one
     *         (level below HIGH, no level covering the value, or the device's home
     *         cannot be resolved yet)
     */
    Optional<AlertResult> register(RegisterThresholdAlertCommand command);
}
