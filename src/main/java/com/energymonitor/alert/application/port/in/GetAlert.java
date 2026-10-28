package com.energymonitor.alert.application.port.in;

import com.energymonitor.alert.application.command.GetAlertQuery;
import com.energymonitor.alert.application.result.AlertResult;

/**
 * Input port for reading a single alert.
 */
public interface GetAlert {

    /**
     * @param query the alert identifier
     * @return the alert
     * @throws com.energymonitor.alert.application.exception.AlertNotFoundException
     *         when the alert does not exist
     */
    AlertResult get(GetAlertQuery query);
}
