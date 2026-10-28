package com.energymonitor.alert.application.port.out;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;

/**
 * The consumption limit of a home and the energy consumed, read from the home and measurement
 * services.
 */
public interface ConsumptionLimitPort {

    /**
     * @param monthly {@code true} for a monthly limit, {@code false} for a daily one
     * @param kwh     the limit, kWh
     */
    record Limit(boolean monthly, double kwh) {
    }

    /** The limit the owner chose: daily or monthly, never both. */
    Optional<Limit> limitOf(String homeId);

    /** Energy consumed by the devices in {@code [from, to)}, kWh. */
    double energy(Collection<String> deviceIds, Instant from, Instant to);

    /** Zone in which days and months start. */
    ZoneId zone();

    /** Current instant: a reading may be a late sample queued by the module that sent it. */
    Instant now();
}
