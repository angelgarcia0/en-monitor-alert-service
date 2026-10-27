package com.energymonitor.alert.adapter.out.device;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient.RemoteDevice;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Devices answered by the device service: a device with its home and the date it was linked, and
 * the devices of a home.
 *
 * <p>Every reading asks for the home of its device, so answers are reused for a short while, a
 * device that does not exist included: a device publishing every minute costs one request per
 * {@code ttl}, not one per reading. A device unlinked in the service shows here once its entry
 * expires, or at once when {@link #forget} is called for it.
 *
 * <p>The last answer is also kept for {@code staleTtl}: when the service does not answer, that is
 * what is served, so a short outage of the device service does not stop the alerts of devices
 * already known. Only with nothing kept does a call throw {@link DeviceServiceUnavailableException}.
 */
public class RemoteDevices {

    private final DeviceServiceClient client;
    private final Cache<String, Optional<RemoteDevice>> devices;
    private final Cache<String, Optional<RemoteDevice>> lastKnownDevices;
    private final Cache<String, List<RemoteDevice>> homes;
    private final Cache<String, List<RemoteDevice>> lastKnownHomes;

    /**
     * @param client   the device service
     * @param ttl      how long an answer is reused before asking again
     * @param staleTtl how long the last answer is kept to serve while the service does not answer
     * @param ticker   the clock of the caches
     */
    public RemoteDevices(DeviceServiceClient client, Duration ttl, Duration staleTtl, Ticker ticker) {
        this.client = client;
        this.devices = cache(ttl, ticker);
        this.lastKnownDevices = cache(staleTtl, ticker);
        this.homes = cache(ttl, ticker);
        this.lastKnownHomes = cache(staleTtl, ticker);
    }

    private static <V> Cache<String, V> cache(Duration ttl, Ticker ticker) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).ticker(ticker).build();
    }

    /**
     * @param deviceId the device, may be null
     * @return the device, when it exists
     */
    public Optional<RemoteDevice> device(String deviceId) {
        if (deviceId == null) {
            return Optional.empty();
        }
        return fresh(devices, lastKnownDevices, deviceId, client::device);
    }

    /**
     * @param homeId the home, may be null
     * @return the devices linked to it, empty when it has none
     */
    public List<RemoteDevice> devicesOfHome(String homeId) {
        if (homeId == null) {
            return List.of();
        }
        return fresh(homes, lastKnownHomes, homeId, client::devicesOfHome);
    }

    /**
     * Drops what is known of a device and of a home, so the next call asks the service again.
     * Called when the device service announces a link or an unlink.
     *
     * @param deviceId the device that changed
     * @param homeId   the home it joined or left, may be null
     */
    public void forget(String deviceId, String homeId) {
        if (deviceId != null) {
            devices.invalidate(deviceId);
        }
        if (homeId != null) {
            homes.invalidate(homeId);
        }
    }

    /**
     * The cached answer, or a new one, which is also kept as the last known; when the service does
     * not answer, the last known one if there is any.
     */
    private static <V> V fresh(Cache<String, V> cache, Cache<String, V> lastKnown, String key,
                               Function<String, V> load) {
        V cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            V loaded = load.apply(key);
            cache.put(key, loaded);
            lastKnown.put(key, loaded);
            return loaded;
        } catch (DeviceServiceUnavailableException unavailable) {
            V stale = lastKnown.getIfPresent(key);
            if (stale != null) {
                return stale;
            }
            throw unavailable;
        }
    }
}
