package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeAlertEventPort;
import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeAlertPersistencePort;
import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeHomeLookupPort;
import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeIdentifierGeneratorPort;
import com.energymonitor.alert.application.port.out.DeviceLookupPort;
import com.energymonitor.alert.application.usecase.RegisterDeviceLinkedAlertService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterDeviceLinkedAlertServiceTest {

    private static final Instant LINKED = Instant.parse("2026-01-15T10:00:00Z");

    private final FakeAlertPersistencePort alerts = new FakeAlertPersistencePort();
    private final FakeHomeLookupPort homes = new FakeHomeLookupPort();
    private final FakeAlertEventPort events = new FakeAlertEventPort();
    private final Map<String, Instant> linkedAt = new HashMap<>();
    private final DeviceLookupPort devices = new DeviceLookupPort() {
        @Override
        public Optional<Instant> linkedAt(String deviceId) {
            return Optional.ofNullable(linkedAt.get(deviceId));
        }

        @Override
        public List<String> deviceIdsOfHome(String homeId) {
            return List.of();
        }
    };

    private RegisterDeviceLinkedAlertService service() {
        return new RegisterDeviceLinkedAlertService(alerts, devices, homes, new FakeIdentifierGeneratorPort(),
                events);
    }

    @BeforeEach
    void linked() {
        homes.link("dev0000001", "hom0000001");
        linkedAt.put("dev0000001", LINKED);
    }

    @Test
    void theFirstReadingAfterTheLinkRaisesItOnce() {
        var service = service();
        service.onMeasurement("dev0000001", LINKED.plusSeconds(60));
        service.onMeasurement("dev0000001", LINKED.plusSeconds(120));

        assertEquals(1, events.published.size());
        assertEquals("alert.device.linked", events.published.getFirst().messageKey());
    }

    @Test
    void aSampleQueuedBeforeTheLinkDoesNotCount() {
        service().onMeasurement("dev0000001", LINKED.minusSeconds(60));

        assertEquals(0, events.published.size());
    }

    @Test
    void aRestartDoesNotRepeatItEvenIfTheAlertWasDeleted() {
        service().onMeasurement("dev0000001", LINKED.plusSeconds(60));
        alerts.delete(events.published.getFirst().idAlert());

        service().onMeasurement("dev0000001", LINKED.plusSeconds(120));

        assertEquals(1, events.published.size());
    }

    @Test
    void aNewLinkIsAnnouncedAgain() {
        var service = service();
        service.onMeasurement("dev0000001", LINKED.plusSeconds(60));
        linkedAt.put("dev0000001", LINKED.plusSeconds(3600));

        service.onMeasurement("dev0000001", LINKED.plusSeconds(3660));

        assertEquals(2, events.published.size());
    }
}
