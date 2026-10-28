package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.port.out.DeviceBaselinePort;
import com.energymonitor.alert.application.usecase.RegisterAnomalyAlertService;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegisterAnomalyAlertServiceTest {

    private Optional<DeviceBaselinePort.Baseline> baseline;
    private AlertUseCaseFixtures.FakeAlertPersistencePort alertPort;
    private AlertUseCaseFixtures.FakeHomeLookupPort homes;
    private AlertUseCaseFixtures.FakeAlertEventPort events;
    private RegisterAnomalyAlertService service;

    @BeforeEach
    void setUp() {
        baseline = Optional.of(new DeviceBaselinePort.Baseline(100, 100));
        alertPort = new AlertUseCaseFixtures.FakeAlertPersistencePort();
        homes = new AlertUseCaseFixtures.FakeHomeLookupPort();
        events = new AlertUseCaseFixtures.FakeAlertEventPort();
        homes.link("dev0000001", "hom0000001");
        DeviceBaselinePort baselines = (deviceId, until) -> baseline;
        service = new RegisterAnomalyAlertService(alertPort, baselines,
                new AlertUseCaseFixtures.FakeConsumptionLevelLookupPort(), homes,
                new AlertUseCaseFixtures.FakeIdentifierGeneratorPort(), events);
    }

    private RegisterThresholdAlertCommand reading(double activePower) {
        return new RegisterThresholdAlertCommand("dev0000001", "mea0000001",
                AlertUseCaseFixtures.NOW, activePower);
    }

    @Test
    @DisplayName("a reading far above the device's usual power raises an anomaly alert")
    void raisesWhenFarAboveTheBaseline() {
        var result = service.evaluate(reading(300));

        assertTrue(result.isPresent());
        assertEquals("alert.anomaly.device", result.get().messageKey());
        assertEquals(AlertType.THRESHOLD, result.get().type());
        assertEquals("dev0000001", result.get().deviceId());
        assertEquals(AlertStatus.PENDING, result.get().alertStatus());
        assertEquals(1, events.published.size());
    }

    @Test
    @DisplayName("a reading under 2.5 times the usual power is normal")
    void normalReadingRaisesNothing() {
        assertTrue(service.evaluate(reading(240)).isEmpty());
        assertTrue(events.published.isEmpty());
    }

    @Test
    @DisplayName("a reading under 200 W is never an anomaly, however small the baseline")
    void smallPowerIsNeverAnAnomaly() {
        baseline = Optional.of(new DeviceBaselinePort.Baseline(20, 100));

        assertTrue(service.evaluate(reading(150)).isEmpty());
    }

    @Test
    @DisplayName("without enough history there is no usual consumption to compare against")
    void needsHistory() {
        baseline = Optional.of(new DeviceBaselinePort.Baseline(100, 23));
        assertTrue(service.evaluate(reading(900)).isEmpty());

        baseline = Optional.empty();
        assertTrue(service.evaluate(reading(900)).isEmpty());
    }

    @Test
    @DisplayName("a device that is almost always off has no baseline either")
    void almostAlwaysOffHasNoBaseline() {
        baseline = Optional.of(new DeviceBaselinePort.Baseline(2, 100));

        assertTrue(service.evaluate(reading(900)).isEmpty());
    }

    @Test
    @DisplayName("while one is pending, another reading above the line adds nothing")
    void oneAlertWhilePending() {
        assertTrue(service.evaluate(reading(300)).isPresent());
        assertTrue(service.evaluate(reading(350)).isEmpty());

        assertEquals(1, alertPort.listActiveByHome("hom0000001", AlertStatus.PENDING).size());
    }

    @Test
    @DisplayName("the pending alert resolves itself when consumption is back to normal")
    void resolvesWhenBackToNormal() {
        service.evaluate(reading(300));

        service.evaluate(reading(120));

        assertTrue(alertPort.listActiveByHome("hom0000001", AlertStatus.PENDING).isEmpty());
        assertFalse(alertPort.listActiveByHome("hom0000001", AlertStatus.RESOLVED).isEmpty());
    }

    @Test
    @DisplayName("a device linked to no home raises nothing")
    void deviceWithoutHome() {
        var other = new RegisterThresholdAlertCommand("dev0000009", "mea0000001",
                Instant.parse("2026-10-24T20:00:00Z"), 900);

        assertTrue(service.evaluate(other).isEmpty());
    }
}
