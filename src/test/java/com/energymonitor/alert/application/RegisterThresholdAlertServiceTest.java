package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.usecase.RegisterThresholdAlertService;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterThresholdAlertServiceTest {

    private AlertUseCaseFixtures.FakeAlertPersistencePort alertPort;
    private AlertUseCaseFixtures.FakeConsumptionLevelLookupPort levels;
    private AlertUseCaseFixtures.FakeHomeLookupPort homes;
    private AlertUseCaseFixtures.FakeIdentifierGeneratorPort identifiers;
    private AlertUseCaseFixtures.FakeAlertEventPort events;
    private RegisterThresholdAlertService service;

    @BeforeEach
    void setUp() {
        alertPort = new AlertUseCaseFixtures.FakeAlertPersistencePort();
        levels = new AlertUseCaseFixtures.FakeConsumptionLevelLookupPort();
        homes = new AlertUseCaseFixtures.FakeHomeLookupPort();
        identifiers = new AlertUseCaseFixtures.FakeIdentifierGeneratorPort();
        events = new AlertUseCaseFixtures.FakeAlertEventPort();
        service = new RegisterThresholdAlertService(alertPort, levels, homes, identifiers, events);
    }

    private RegisterThresholdAlertCommand command(double activePower) {
        return new RegisterThresholdAlertCommand("dev0000001", "mea0000001",
                AlertUseCaseFixtures.NOW, activePower);
    }

    @Test
    void highReadingRaisesAnAlert() {
        homes.link("dev0000001", "hom0000001");

        var result = service.register(command(2000));

        assertTrue(result.isPresent());
        var alert = result.get();
        assertEquals("id0000001", alert.idAlert());
        assertEquals("hom0000001", alert.homeId());
        assertEquals("dev0000001", alert.deviceId());
        assertEquals(AlertType.THRESHOLD, alert.type());
        assertEquals("alert.threshold.high", alert.messageKey());
        assertEquals(AlertStatus.PENDING, alert.alertStatus());
        assertEquals("high000001", alert.consumptionLevelId());
        assertEquals("mea0000001", alert.measurementId());

        // Persisted and published
        assertTrue(alertPort.findActive(alert.idAlert()).isPresent());
        assertEquals(1, events.published.size());
        assertEquals(alert.idAlert(), events.published.getFirst().idAlert());
        assertEquals(alert.messageKey(), events.published.getFirst().messageKey());
    }

    @Test
    void criticalReadingRaisesACriticalAlert() {
        homes.link("dev0000001", "hom0000001");

        var result = service.register(command(5000));

        assertTrue(result.isPresent());
        assertEquals("alert.threshold.critical", result.get().messageKey());
        assertEquals("crit000001", result.get().consumptionLevelId());
    }

    @Test
    void lowOrMediumReadingRaisesNothing() {
        homes.link("dev0000001", "hom0000001");

        assertTrue(service.register(command(250)).isEmpty());
        assertTrue(service.register(command(1000)).isEmpty());
        assertTrue(events.published.isEmpty());
    }

    @Test
    void valueWithoutCoveringLevelRaisesNothing() {
        homes.link("dev0000001", "hom0000001");

        assertTrue(service.register(command(2_000_000)).isEmpty());
        assertTrue(events.published.isEmpty());
    }

    @Test
    void unresolvedHomeSkipsTheAlert() {
        // Device not linked to any home: the documented gap until the Devices module lands

        var result = service.register(command(2000));

        assertTrue(result.isEmpty());
        assertTrue(alertPort.findActive("id0000001").isEmpty());
        assertTrue(events.published.isEmpty());
    }

    @Test
    void aRepeatedHighReadingDoesNotRaiseASecondAlert() {
        homes.link("dev0000001", "hom0000001");

        assertTrue(service.register(command(2000)).isPresent());
        assertTrue(service.register(command(2100)).isEmpty());
    }

    @Test
    void aNormalReadingResolvesThePendingThresholdAlert() {
        homes.link("dev0000001", "hom0000001");
        var raised = service.register(command(2000)).orElseThrow();

        service.register(command(250));

        assertEquals(AlertStatus.RESOLVED, alertPort.findActive(raised.idAlert()).orElseThrow().alertStatus());
        // and the next spike alerts again
        assertTrue(service.register(command(2000)).isPresent());
    }
}
