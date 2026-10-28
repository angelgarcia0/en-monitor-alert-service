package com.energymonitor.alert.application.port.in;

import com.energymonitor.alert.application.command.RegisterConnectivityAlertCommand;
import com.energymonitor.alert.application.result.AlertResult;
import java.util.Optional;

/**
 * Use case: raise a CONNECTIVITY alert when a device stops reporting.
 */
public interface RegisterConnectivityAlert {

    Optional<AlertResult> register(RegisterConnectivityAlertCommand command);
}
