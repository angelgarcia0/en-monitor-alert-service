package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.energymonitor.alert.application.command.ResolveAlertCommand;
import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.usecase.ResolveAlertService;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolveAlertServiceTest {

    private AlertUseCaseFixtures.FakeAlertPersistencePort alertPort;
    private ResolveAlertService service;

    @BeforeEach
    void setUp() {
        alertPort = new AlertUseCaseFixtures.FakeAlertPersistencePort();
        service = new ResolveAlertService(alertPort);
    }

    private Alert seedPending() {
        Alert alert = Alert.device("ale0000001", "hom0000001", "dev0000001", "alert.device.linked",
                AlertUseCaseFixtures.NOW);
        alertPort.seed(alert);
        return alert;
    }

    @Test
    void alertsTheSystemResolvesCannotBeResolvedByHand() {
        alertPort.seed(Alert.threshold("ale0000002", "hom0000001", "dev0000001",
                "alert.threshold.high", AlertUseCaseFixtures.NOW, "high000001", "mea0000001"));
        alertPort.seed(Alert.connectivity("ale0000003", "hom0000001", "dev0000001",
                "alert.connectivity.offline", AlertUseCaseFixtures.NOW));

        assertThrows(IllegalStateException.class, () -> service.resolve(new ResolveAlertCommand("ale0000002")));
        assertThrows(IllegalStateException.class, () -> service.resolve(new ResolveAlertCommand("ale0000003")));
    }

    @Test
    void resolveMarksTheAlertAsResolved() {
        seedPending();

        var result = service.resolve(new ResolveAlertCommand("ale0000001"));

        assertEquals(AlertStatus.RESOLVED, result.alertStatus());
        assertEquals(AlertStatus.RESOLVED,
                alertPort.findActive("ale0000001").orElseThrow().alertStatus());
    }

    @Test
    void resolveUnknownAlertThrows() {
        assertThrows(AlertNotFoundException.class,
                () -> service.resolve(new ResolveAlertCommand("ale0000009")));
    }

    @Test
    void resolveTwiceThrows() {
        seedPending();
        service.resolve(new ResolveAlertCommand("ale0000001"));

        assertThrows(IllegalStateException.class,
                () -> service.resolve(new ResolveAlertCommand("ale0000001")));
    }
}
