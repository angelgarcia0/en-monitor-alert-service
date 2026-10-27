package com.energymonitor.alert.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeAlertEventPort;
import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeAlertPersistencePort;
import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeHomeLookupPort;
import com.energymonitor.alert.application.AlertUseCaseFixtures.FakeIdentifierGeneratorPort;
import com.energymonitor.alert.application.port.out.ConsumptionLimitPort;
import com.energymonitor.alert.application.port.out.DeviceLookupPort;
import com.energymonitor.alert.application.usecase.RegisterLimitAlertService;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterLimitAlertServiceTest {

    /** 14:00 in Bogotá. */
    private Instant now = Instant.parse("2026-01-15T19:00:00Z");
    private double consumed;
    private ConsumptionLimitPort.Limit limit = new ConsumptionLimitPort.Limit(false, 0.15);
    private Instant askedFrom;

    private final FakeAlertPersistencePort alerts = new FakeAlertPersistencePort();
    private final FakeHomeLookupPort homes = new FakeHomeLookupPort();
    private final FakeAlertEventPort events = new FakeAlertEventPort();

    private final RegisterLimitAlertService service = new RegisterLimitAlertService(alerts, homes,
            new DeviceLookupPort() {
                @Override
                public Optional<Instant> linkedAt(String deviceId) {
                    return Optional.empty();
                }

                @Override
                public List<String> deviceIdsOfHome(String homeId) {
                    return List.of("dev0000001");
                }
            },
            new ConsumptionLimitPort() {
                @Override
                public Optional<Limit> limitOf(String homeId) {
                    return Optional.ofNullable(limit);
                }

                @Override
                public double energy(Collection<String> deviceIds, Instant from, Instant to) {
                    askedFrom = from;
                    return consumed;
                }

                @Override
                public ZoneId zone() {
                    return ZoneId.of("America/Bogota");
                }

                @Override
                public Instant now() {
                    return now;
                }
            },
            new FakeIdentifierGeneratorPort(), events);

    @BeforeEach
    void home() {
        homes.link("dev0000001", "hom0000001");
    }

    @Test
    void reachingTheDailyLimitRaisesOneAlert() {
        consumed = 0.15;
        service.evaluate("dev0000001", now);
        service.evaluate("dev0000001", now.plusSeconds(60));

        assertEquals(1, events.published.size());
        assertEquals(AlertType.LIMIT, events.published.getFirst().type());
        assertEquals("alert.limit.daily", events.published.getFirst().messageKey());
        assertEquals(Instant.parse("2026-01-15T05:00:00Z"), askedFrom);
    }

    @Test
    void underTheLimitNothingIsRaised() {
        consumed = 0.10;
        service.evaluate("dev0000001", now);

        assertEquals(0, events.published.size());
    }

    @Test
    void aNewDayResolvesItAndAllowsANewOne() {
        consumed = 0.20;
        service.evaluate("dev0000001", now);

        now = now.plusSeconds(86400);
        consumed = 0.01;
        service.evaluate("dev0000001", now);
        assertFalse(alerts.listActiveByHome("hom0000001", AlertStatus.PENDING).stream()
                .anyMatch(a -> a.type() == AlertType.LIMIT));

        consumed = 0.16;
        service.evaluate("dev0000001", now.plusSeconds(60));
        assertEquals(2, events.published.size());
    }

    @Test
    void theMonthlyLimitCountsFromTheFirstOfTheMonth() {
        limit = new ConsumptionLimitPort.Limit(true, 4.5);
        consumed = 5;
        service.evaluate("dev0000001", now);

        assertEquals("alert.limit.monthly", events.published.getFirst().messageKey());
        assertEquals(Instant.parse("2026-01-01T05:00:00Z"), askedFrom);
    }
}
