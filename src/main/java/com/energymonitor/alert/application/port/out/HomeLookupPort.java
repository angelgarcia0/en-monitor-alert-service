package com.energymonitor.alert.application.port.out;

import java.util.Optional;

/**
 * Port to the homes a device belongs to and the people who belong to a home.
 */
public interface HomeLookupPort {

    Optional<String> findHomeIdByDeviceId(String deviceId);

    boolean isMember(String userId, String homeId);
}
