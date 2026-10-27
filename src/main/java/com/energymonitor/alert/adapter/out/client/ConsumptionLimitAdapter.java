package com.energymonitor.alert.adapter.out.client;

import com.energymonitor.alert.adapter.out.home.HomeServiceClient.LimitPeriod;
import com.energymonitor.alert.adapter.out.home.RemoteHomes;
import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteHourlyEnergy;
import com.energymonitor.alert.adapter.out.measurement.RemoteMeasurements;
import com.energymonitor.alert.application.port.out.ConsumptionLimitPort;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The limit a home's owner set, from the home service, and the energy its devices consumed, from
 * the measurement service.
 */
@Component
public class ConsumptionLimitAdapter implements ConsumptionLimitPort {

    private final RemoteHomes homes;
    private final RemoteMeasurements measurements;
    private final ZoneId zone;

    public ConsumptionLimitAdapter(RemoteHomes homes, RemoteMeasurements measurements,
                                   @Value("${alert.limit.zone:America/Bogota}") String zone) {
        this.homes = homes;
        this.measurements = measurements;
        this.zone = ZoneId.of(zone);
    }

    @Override
    public Optional<Limit> limitOf(String homeId) {
        return homes.limits(homeId).map(l -> l.limitPeriod() == LimitPeriod.MONTHLY
                ? new Limit(true, l.monthlyLimit())
                : new Limit(false, l.dailyLimit()));
    }

    @Override
    public double energy(Collection<String> deviceIds, Instant from, Instant to) {
        return measurements.hourlyEnergy(deviceIds, from, to).stream()
                .mapToDouble(RemoteHourlyEnergy::energy)
                .sum();
    }

    @Override
    public ZoneId zone() {
        return zone;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }
}
