package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.port.in.RegisterDeviceLinkedAlert;
import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.DeviceLookupPort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertRaised;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raises {@code alert.device.linked} when a linked module sends its first reading, so members
 * hear about a device that is really working, once per link.
 *
 * <p>Linking from the web can be retried many times before the module joins the network (wrong
 * password, network out of reach). Raising the alert at link time produced one notification per
 * attempt, even for a module that never connected. Now the first reading taken after the latest
 * link raises it; whether that happened is read back from the alerts themselves (deleted ones
 * included), so a restart never repeats it. The last link already announced is remembered per
 * device to avoid that lookup on every reading.
 *
 * <p>This service contains no Spring annotations.
 */
public class RegisterDeviceLinkedAlertService implements RegisterDeviceLinkedAlert {

    static final String MESSAGE_KEY = "alert.device.linked";

    private final AlertPersistencePort alertPort;
    private final DeviceLookupPort devices;
    private final HomeLookupPort homes;
    private final IdentifierGeneratorPort identifiers;
    private final AlertEventPort events;
    private final Map<String, Instant> announced = new ConcurrentHashMap<>();

    public RegisterDeviceLinkedAlertService(AlertPersistencePort alertPort, DeviceLookupPort devices,
                                            HomeLookupPort homes, IdentifierGeneratorPort identifiers,
                                            AlertEventPort events) {
        this.alertPort = alertPort;
        this.devices = devices;
        this.homes = homes;
        this.identifiers = identifiers;
        this.events = events;
    }

    @Override
    public void onMeasurement(String deviceId, Instant dateTime) {
        Instant linkedAt = devices.linkedAt(deviceId).orElse(null);
        if (linkedAt == null || linkedAt.equals(announced.get(deviceId))) {
            return;
        }
        // A sample queued by the module before it was linked again says nothing about the new link.
        if (dateTime.isBefore(linkedAt)) {
            return;
        }
        if (alertPort.existsForDeviceSince(deviceId, MESSAGE_KEY, linkedAt)) {
            announced.put(deviceId, linkedAt);
            return;
        }
        var homeId = homes.findHomeIdByDeviceId(deviceId);
        if (homeId.isEmpty()) {
            return;
        }
        Alert alert = Alert.device(identifiers.generate(), homeId.get(), deviceId, MESSAGE_KEY, dateTime);
        alertPort.save(alert);
        announced.put(deviceId, linkedAt);
        events.publish(new AlertRaised(alert.idAlert(), alert.homeId(), alert.deviceId(), alert.type(),
                alert.messageKey(), alert.dateTime(), null, null));
    }
}
