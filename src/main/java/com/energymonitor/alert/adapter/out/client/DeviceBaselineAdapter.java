package com.energymonitor.alert.adapter.out.client;

import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteHourlyEnergy;
import com.energymonitor.alert.adapter.out.measurement.RemoteMeasurements;
import com.energymonitor.alert.application.port.out.DeviceBaselinePort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A device's usual power, from the hourly energy the measurement service keeps.
 *
 * <p>A reading arrives every minute and the usual power moves slowly, so the baseline of a device
 * is reused for {@code alert.anomaly.baseline-ttl} (ten minutes by default) instead of asking the
 * measurement service on every reading. A measurement service that does not answer makes the
 * baseline unavailable: no anomaly is judged, and nothing is raised against a guess.
 */
@Component
public class DeviceBaselineAdapter implements DeviceBaselinePort {

    private final RemoteMeasurements measurements;
    private final Duration window;
    private final Cache<String, Optional<Baseline>> cache;

    public DeviceBaselineAdapter(RemoteMeasurements measurements,
                                 @Value("${alert.anomaly.window:P7D}") Duration window,
                                 @Value("${alert.anomaly.baseline-ttl:PT10M}") Duration ttl) {
        this.measurements = measurements;
        this.window = window;
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).build();
    }

    @Override
    public Optional<Baseline> baselineOf(String deviceId, Instant until) {
        return cache.get(deviceId, id -> compute(id, until));
    }

    private Optional<Baseline> compute(String deviceId, Instant until) {
        Instant to = until.minus(Duration.ofHours(1));
        List<RemoteHourlyEnergy> hours;
        try {
            hours = measurements.hourlyEnergy(List.of(deviceId), to.minus(window), to);
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
        var powers = hours.stream().filter(h -> deviceId.equals(h.deviceId()))
                .mapToDouble(RemoteHourlyEnergy::averagePower).toArray();
        if (powers.length == 0) {
            return Optional.empty();
        }
        double mean = java.util.Arrays.stream(powers).average().orElse(0);
        return Optional.of(new Baseline(mean, powers.length));
    }
}
