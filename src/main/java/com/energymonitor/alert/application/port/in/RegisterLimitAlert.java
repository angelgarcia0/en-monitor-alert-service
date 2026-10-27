package com.energymonitor.alert.application.port.in;

import java.time.Instant;

/**
 * Compares the consumption of the device's home with its daily or monthly limit.
 */
public interface RegisterLimitAlert {

    void evaluate(String deviceId, Instant dateTime);
}
