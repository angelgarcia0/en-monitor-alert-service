package com.energymonitor.alert.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Devices of a home and when each was linked, read from the device service.
 */
public interface DeviceLookupPort {

    /** When the device was last linked to its home; a relink moves it forward. */
    Optional<Instant> linkedAt(String deviceId);

    List<String> deviceIdsOfHome(String homeId);
}
