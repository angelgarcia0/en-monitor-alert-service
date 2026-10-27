package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.alert.application.command.ListAlertsQuery;
import com.energymonitor.alert.application.usecase.ListAlertsService;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListAlertsServiceTest {

    private static final Instant T1 = Instant.parse("2026-01-15T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-15T10:00:00Z");

    private AlertUseCaseFixtures.FakeAlertPersistencePort alertPort;
    private ListAlertsService service;

    @BeforeEach
    void setUp() {
        alertPort = new AlertUseCaseFixtures.FakeAlertPersistencePort();
        service = new ListAlertsService(alertPort);
    }

    private void seedThree() {
        alertPort.seed(Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                "alert.threshold.high", T1, "high000001", "mea0000001"));
        alertPort.seed(Alert.threshold("ale0000002", "hom0000001", "dev0000001",
                "alert.threshold.high", T3, "high000001", "mea0000003"));
        alertPort.seed(Alert.connectivity("ale0000003", "hom0000001", "dev0000002",
                "alert.connectivity.lost", T2));
        // Another home, must not leak into the results
        alertPort.seed(Alert.threshold("ale0000004", "hom0000002", "dev0000009",
                "alert.threshold.high", T3, "high000001", "mea0000009"));
    }

    @Test
    void listReturnsHomeAlertsMostRecentFirst() {
        seedThree();

        var results = service.list(new ListAlertsQuery("hom0000001", null));

        assertEquals(3, results.size());
        assertEquals("ale0000002", results.get(0).idAlert());
        assertEquals("ale0000003", results.get(1).idAlert());
        assertEquals("ale0000001", results.get(2).idAlert());
    }

    @Test
    void listFilteredByStatus() {
        seedThree();
        alertPort.findActive("ale0000001").orElseThrow().resolve();

        var pending = service.list(new ListAlertsQuery("hom0000001", AlertStatus.PENDING));
        assertEquals(2, pending.size());

        var resolved = service.list(new ListAlertsQuery("hom0000001", AlertStatus.RESOLVED));
        assertEquals(1, resolved.size());
        assertEquals("ale0000001", resolved.getFirst().idAlert());
    }

    @Test
    void listWithNoAlertsReturnsEmpty() {
        assertTrue(service.list(new ListAlertsQuery("hom0000001", null)).isEmpty());
    }
}
