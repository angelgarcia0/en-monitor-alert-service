package com.energymonitor.alert.adapter.out.client;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient.RemoteDevice;
import com.energymonitor.alert.adapter.out.device.RemoteDevices;
import com.energymonitor.alert.application.port.out.DeviceLookupPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * When a device was linked and the devices of a home, from the device service. The installation
 * date of a device is the moment it was last linked: a relink moves it forward.
 */
@Component
public class DeviceLookupAdapter implements DeviceLookupPort {

    private final RemoteDevices devices;

    public DeviceLookupAdapter(RemoteDevices devices) {
        this.devices = devices;
    }

    @Override
    public Optional<Instant> linkedAt(String deviceId) {
        return devices.device(deviceId).map(RemoteDevice::installationDate);
    }

    @Override
    public List<String> deviceIdsOfHome(String homeId) {
        return devices.devicesOfHome(homeId).stream().map(RemoteDevice::idDevice).toList();
    }
}
