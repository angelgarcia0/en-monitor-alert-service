package com.energymonitor.alert.application;

import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.ConsumptionLevelLookupPort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertRaised;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.RiskConsumption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fakes of the output ports, for application-layer tests only.
 *
 * <p>They replace the {@code @Component} adapters so the use cases can be exercised
 * without a database, a Spring context or the measurement module.
 */
public final class AlertUseCaseFixtures {

    public static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");

    private AlertUseCaseFixtures() {
    }

    public static class FakeAlertPersistencePort implements AlertPersistencePort {

        private final Map<String, Alert> byId = new LinkedHashMap<>();
        private final Map<String, Alert> deleted = new LinkedHashMap<>();

        @Override
        public Alert save(Alert alert) {
            byId.put(alert.idAlert(), alert);
            return alert;
        }

        @Override
        public Optional<Alert> findActive(String idAlert) {
            return Optional.ofNullable(byId.get(idAlert));
        }

        @Override
        public List<Alert> listActiveByHome(String homeId, AlertStatus status) {
            return byId.values().stream()
                    .filter(a -> a.homeId().equals(homeId))
                    .filter(a -> status == null || a.alertStatus() == status)
                    .sorted(Comparator.comparing(Alert::dateTime).reversed())
                    .toList();
        }

        @Override
        public void delete(String idAlert) {
            Alert removed = byId.remove(idAlert);
            if (removed != null) {
                deleted.put(idAlert, removed);
            }
        }

        @Override
        public int deleteResolved(String homeId) {
            var resolved = byId.values().stream()
                    .filter(a -> a.homeId().equals(homeId) && !a.isPending())
                    .map(Alert::idAlert)
                    .toList();
            resolved.forEach(byId::remove);
            return resolved.size();
        }

        @Override
        public boolean existsForDeviceSince(String deviceId, String messageKey, Instant since) {
            return java.util.stream.Stream.concat(byId.values().stream(), deleted.values().stream())
                    .anyMatch(a -> deviceId.equals(a.deviceId())
                    && messageKey.equals(a.messageKey()) && !a.dateTime().isBefore(since));
        }

        public void seed(Alert alert) {
            byId.put(alert.idAlert(), alert);
        }
    }

    public static class FakeConsumptionLevelLookupPort implements ConsumptionLevelLookupPort {

        /**
         * Mirrors the seeded catalog ranges: LOW [0,500), MEDIUM [500,1500),
         * HIGH [1500,3000), CRITICAL [3000,1000000).
         */
        @Override
        public Optional<ConsumptionLevelView> classify(double activePower) {
            if (activePower < 0 || activePower >= 1_000_000) {
                return Optional.empty();
            }
            if (activePower < 500) {
                return Optional.of(new ConsumptionLevelView("low0000001", RiskConsumption.LOW));
            }
            if (activePower < 1500) {
                return Optional.of(new ConsumptionLevelView("medi000001", RiskConsumption.MEDIUM));
            }
            if (activePower < 3000) {
                return Optional.of(new ConsumptionLevelView("high000001", RiskConsumption.HIGH));
            }
            return Optional.of(new ConsumptionLevelView("crit000001", RiskConsumption.CRITICAL));
        }
    }

    public static class FakeHomeLookupPort implements HomeLookupPort {

        private final Map<String, String> homeByDevice = new LinkedHashMap<>();

        @Override
        public Optional<String> findHomeIdByDeviceId(String deviceId) {
            return Optional.ofNullable(homeByDevice.get(deviceId));
        }

        @Override
        public boolean isMember(String userId, String homeId) {
            return true;
        }

        public void link(String deviceId, String homeId) {
            homeByDevice.put(deviceId, homeId);
        }
    }

    public static class FakeIdentifierGeneratorPort implements IdentifierGeneratorPort {

        private int sequence;

        @Override
        public String generate() {
            sequence++;
            return "id" + String.format("%07d", sequence);
        }
    }

    public static class FakeAlertEventPort implements AlertEventPort {

        public final List<AlertRaised> published = new ArrayList<>();

        @Override
        public void publish(AlertRaised event) {
            published.add(event);
        }
    }
}
