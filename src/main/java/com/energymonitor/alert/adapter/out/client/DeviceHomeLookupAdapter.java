package com.energymonitor.alert.adapter.out.client;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient.RemoteDevice;
import com.energymonitor.alert.adapter.out.device.RemoteDevices;
import com.energymonitor.alert.adapter.out.home.RemoteHomes;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The home of a device, from the device service, and the members of a home, from the home service.
 */
@Component
public class DeviceHomeLookupAdapter implements HomeLookupPort {

    private final RemoteDevices devices;
    private final RemoteHomes homes;

    public DeviceHomeLookupAdapter(RemoteDevices devices, RemoteHomes homes) {
        this.devices = devices;
        this.homes = homes;
    }

    @Override
    public Optional<String> findHomeIdByDeviceId(String deviceId) {
        return devices.device(deviceId).map(RemoteDevice::homeId);
    }

    @Override
    public boolean isMember(String userId, String homeId) {
        return homes.isMember(userId, homeId);
    }
}
