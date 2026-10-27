package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.energymonitor.alert.application.command.GetAlertQuery;
import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.usecase.GetAlertService;
import com.energymonitor.alert.domain.model.Alert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetAlertServiceTest {

    private AlertUseCaseFixtures.FakeAlertPersistencePort alertPort;
    private GetAlertService service;

    @BeforeEach
    void setUp() {
        alertPort = new AlertUseCaseFixtures.FakeAlertPersistencePort();
        service = new GetAlertService(alertPort);
    }

    @Test
    void getReturnsTheAlert() {
        alertPort.seed(Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                "alert.threshold.high", AlertUseCaseFixtures.NOW, "high000001", "mea0000001"));

        var result = service.get(new GetAlertQuery("ale0000001"));

        assertEquals("ale0000001", result.idAlert());
        assertEquals("alert.threshold.high", result.messageKey());
    }

    @Test
    void getUnknownAlertThrows() {
        assertThrows(AlertNotFoundException.class,
                () -> service.get(new GetAlertQuery("ale0000009")));
    }
}
