package com.energymonitor.alert.application.port.in;

import com.energymonitor.alert.application.command.ListAlertsQuery;
import com.energymonitor.alert.application.result.AlertResult;
import java.util.List;

/**
 * Input port for listing the alerts of a home.
 */
public interface ListAlerts {

    /**
     * @param query the home and optional status filter
     * @return the alerts, most recent first
     */
    List<AlertResult> list(ListAlertsQuery query);
}
