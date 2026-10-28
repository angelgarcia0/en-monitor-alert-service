package com.energymonitor.alert.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * What a device usually consumes, taken from its own recent history.
 */
public interface DeviceBaselinePort {

    /**
     * The device's usual power in the days before {@code until}, leaving out the last hour so the
     * reading being judged does not pull its own baseline up.
     *
     * @param deviceId the device
     * @param until    the moment of the reading
     * @return its baseline, empty when the history is not available
     */
    Optional<Baseline> baselineOf(String deviceId, Instant until);

    /**
     * @param averagePower the mean of the hourly average power, in watts
     * @param hours        how many hours of history the mean comes from
     */
    record Baseline(double averagePower, int hours) {
    }
}
