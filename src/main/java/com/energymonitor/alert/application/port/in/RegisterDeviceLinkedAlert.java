package com.energymonitor.alert.application.port.in;

import java.time.Instant;

/**
 * Raises {@code alert.device.linked} once the device is actually working.
 */
public interface RegisterDeviceLinkedAlert {

    /**
     * Called for every reading of a device. The first reading taken after the device was linked
     * raises the alert; any later one, or another link attempt that never connected, does not.
     */
    void onMeasurement(String deviceId, Instant dateTime);
}
