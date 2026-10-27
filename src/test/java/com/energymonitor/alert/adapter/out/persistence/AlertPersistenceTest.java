package com.energymonitor.alert.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertEntity;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.infrastructure.JwtKeyedTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Round trips of the {@code alert} aggregate.
 *
 * <p>No fixtures are needed: every foreign value is an application-level reference with
 * no SQL foreign key, so alerts persist against bare identifiers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertPersistenceTest extends JwtKeyedTest {

    private static final Instant T1 = Instant.parse("2026-01-15T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-15T09:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AlertPersistenceAdapter alerts;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Alert seedThreshold(String id, Instant dateTime) {
        Alert alert = Alert.threshold(id, "hom0000001", "dev0000001",
                "alert.threshold.high", dateTime, "high000001", "mea0000001");
        alerts.save(alert);
        return alert;
    }

    @Test
    void insertAndFindById() {
        Alert alert = seedThreshold("ale0000001", T1);
        flushAndClear();

        Alert read = alerts.findActive(alert.idAlert()).orElseThrow();
        assertEquals(alert.idAlert(), read.idAlert());
        assertEquals(alert.homeId(), read.homeId());
        assertEquals(alert.deviceId(), read.deviceId());
        assertEquals(alert.type(), read.type());
        assertEquals(alert.messageKey(), read.messageKey());
        assertEquals(alert.dateTime(), read.dateTime());
        assertEquals(AlertStatus.PENDING, read.alertStatus());
        assertEquals(alert.consumptionLevelId(), read.consumptionLevelId());
        assertEquals(alert.measurementId(), read.measurementId());
    }

    @Test
    void resolveRoundTrips() {
        Alert alert = seedThreshold("ale0000001", T1);
        alert.resolve();
        alerts.save(alert);
        flushAndClear();

        Alert read = alerts.findActive(alert.idAlert()).orElseThrow();
        assertEquals(AlertStatus.RESOLVED, read.alertStatus());
    }

    @Test
    void listByHomeOrdersMostRecentFirstAndFiltersByStatus() {
        Alert older = seedThreshold("ale0000001", T1);
        seedThreshold("ale0000002", T2);
        older.resolve();
        alerts.save(older);
        flushAndClear();

        var all = alerts.listActiveByHome("hom0000001", null);
        assertEquals(2, all.size());
        assertEquals("ale0000002", all.get(0).idAlert());
        assertEquals("ale0000001", all.get(1).idAlert());

        var pending = alerts.listActiveByHome("hom0000001", AlertStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals("ale0000002", pending.getFirst().idAlert());
    }

    @Test
    void softDeletedRowsAreExcluded() {
        Alert alert = seedThreshold("ale0000001", T1);
        flushAndClear();

        entityManager.find(AlertEntity.class, alert.idAlert()).setDeletedAt(T2);
        flushAndClear();

        assertTrue(alerts.findActive(alert.idAlert()).isEmpty());
        assertTrue(alerts.listActiveByHome("hom0000001", null).isEmpty());
    }
}
