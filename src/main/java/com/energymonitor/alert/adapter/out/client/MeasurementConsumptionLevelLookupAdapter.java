package com.energymonitor.alert.adapter.out.client;

import com.energymonitor.alert.adapter.out.measurement.RemoteMeasurements;
import com.energymonitor.alert.application.port.out.ConsumptionLevelLookupPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Classifies a reading with the consumption level catalogue of the measurement service.
 */
@Component
public class MeasurementConsumptionLevelLookupAdapter implements ConsumptionLevelLookupPort {

    private final RemoteMeasurements measurements;

    public MeasurementConsumptionLevelLookupAdapter(RemoteMeasurements measurements) {
        this.measurements = measurements;
    }

    @Override
    public Optional<ConsumptionLevelView> classify(double activePower) {
        return measurements.classify(activePower)
                .map(level -> new ConsumptionLevelView(level.idConsumptionLevel(), level.name()));
    }
}
