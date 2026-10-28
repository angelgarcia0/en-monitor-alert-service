package com.energymonitor.alert.adapter.out.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteHourlyEnergy;
import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteLevel;
import com.energymonitor.alert.domain.model.RiskConsumption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a reading is classified with the cached level catalogue, what is served while the service
 * does not answer, and that the energy is never cached. The clock of the caches is moved by hand.
 */
@DisplayName("Measurements answered by the measurement service")
class RemoteMeasurementsTest {

    private static final List<RemoteLevel> LEVELS = List.of(
            new RemoteLevel("lev0000001", RiskConsumption.LOW, "Bajo", 0.0, 500.0),
            new RemoteLevel("lev0000002", RiskConsumption.MEDIUM, "Medio", 500.0, 1500.0),
            new RemoteLevel("lev0000003", RiskConsumption.HIGH, "Alto", 1500.0, 3000.0));
    private static final Instant FROM = Instant.parse("2026-10-24T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-24T23:59:59Z");

    private final MeasurementServiceClient client = mock(MeasurementServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final RemoteMeasurements measurements = new RemoteMeasurements(client,
            Duration.ofMinutes(10), Duration.ofHours(24), nanos::get);

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("classifies with the [minLimit, maxLimit) ranges, from one cached request")
    void classifies() {
        given(client.levels()).willReturn(LEVELS);

        assertThat(measurements.classify(0.0)).map(RemoteLevel::name).contains(RiskConsumption.LOW);
        assertThat(measurements.classify(499.9)).map(RemoteLevel::name).contains(RiskConsumption.LOW);
        assertThat(measurements.classify(500.0)).map(RemoteLevel::name).contains(RiskConsumption.MEDIUM);
        assertThat(measurements.classify(1500.0)).map(RemoteLevel::idConsumptionLevel).contains("lev0000003");
        assertThat(measurements.classify(3000.0)).as("no level covers it").isEmpty();

        verify(client, times(1)).levels();
    }

    @Test
    @DisplayName("asks the levels again once they expire, and serves the last ones while the service is down")
    void levelsExpireAndFallBack() {
        given(client.levels()).willReturn(LEVELS)
                .willThrow(new MeasurementServiceUnavailableException("down", null));

        measurements.classify(100.0);
        advance(Duration.ofMinutes(11));

        assertThat(measurements.classify(100.0)).isPresent();
        verify(client, times(2)).levels();
    }

    @Test
    @DisplayName("with no levels kept, an unavailable service fails the classification")
    void failsWithNothingKept() {
        given(client.levels()).willThrow(new MeasurementServiceUnavailableException("down", null));

        assertThatThrownBy(() -> measurements.classify(100.0))
                .isInstanceOf(MeasurementServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the energy is always asked")
    void hourlyEnergy() {
        RemoteHourlyEnergy hour = new RemoteHourlyEnergy("DEV0000001",
                Instant.parse("2026-10-24T10:00:00Z"), 200.0, 0.2);
        given(client.hourlyEnergy(List.of("DEV0000001"), FROM, TO)).willReturn(List.of(hour));

        assertThat(measurements.hourlyEnergy(List.of("DEV0000001"), FROM, TO)).containsExactly(hour);
        measurements.hourlyEnergy(List.of("DEV0000001"), FROM, TO);

        verify(client, times(2)).hourlyEnergy(List.of("DEV0000001"), FROM, TO);
    }

    @Test
    @DisplayName("no device asked for energy is no request")
    void noDevice() {
        assertThat(measurements.hourlyEnergy(List.of(), FROM, TO)).isEmpty();
        verifyNoInteractions(client);
    }
}
