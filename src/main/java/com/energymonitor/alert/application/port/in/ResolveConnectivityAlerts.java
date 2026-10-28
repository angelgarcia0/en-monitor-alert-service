package com.energymonitor.alert.application.port.in;

/**
 * Use case: a device that reports again closes its pending CONNECTIVITY alerts, so the next
 * outage raises a new one.
 */
public interface ResolveConnectivityAlerts {

    /** @return how many alerts were resolved */
    int resolveFor(String deviceId);
}
