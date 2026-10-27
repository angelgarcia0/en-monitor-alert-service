package com.energymonitor.alert.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertTest {

    private static final Instant DATE_TIME = Instant.parse("2026-01-15T10:30:00Z");

    private Alert createThresholdAlert() {
        return Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                "alert.threshold.high", DATE_TIME, "high000001", "mea0000001");
    }

    @Test
    void thresholdAlertWithValidData() {
        Alert alert = createThresholdAlert();
        assertEquals("ale0000001", alert.idAlert());
        assertEquals("hom0000001", alert.homeId());
        assertEquals("dev0000001", alert.deviceId());
        assertEquals(AlertType.THRESHOLD, alert.type());
        assertEquals("alert.threshold.high", alert.messageKey());
        assertEquals(DATE_TIME, alert.dateTime());
        assertEquals(AlertStatus.PENDING, alert.alertStatus());
        assertEquals("high000001", alert.consumptionLevelId());
        assertEquals("mea0000001", alert.measurementId());
        assertTrue(alert.isPending());
    }

    @Test
    void connectivityAlertWithValidData() {
        Alert alert = Alert.connectivity("ale0000001", "hom0000001", "dev0000001",
                "alert.connectivity.lost", DATE_TIME);
        assertEquals(AlertType.CONNECTIVITY, alert.type());
        assertNull(alert.consumptionLevelId());
        assertNull(alert.measurementId());
        assertTrue(alert.isPending());
    }

    @Test
    void thresholdAlertWithoutConsumptionLevelThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                        "alert.threshold.high", DATE_TIME, null, "mea0000001"));
    }

    @Test
    void thresholdAlertWithoutMeasurementThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                        "alert.threshold.high", DATE_TIME, "high000001", null));
    }

    @Test
    void connectivityAlertWithConsumptionLevelThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Alert("ale0000001", "hom0000001", "dev0000001", AlertType.CONNECTIVITY,
                        "alert.connectivity.lost", DATE_TIME, AlertStatus.PENDING,
                        "high000001", null));
    }

    @Test
    void connectivityAlertWithMeasurementThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Alert("ale0000001", "hom0000001", "dev0000001", AlertType.CONNECTIVITY,
                        "alert.connectivity.lost", DATE_TIME, AlertStatus.PENDING,
                        null, "mea0000001"));
    }

    @Test
    void alertWithBlankIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("", "hom0000001", "dev0000001",
                        "alert.threshold.high", DATE_TIME, "high000001", "mea0000001"));
    }

    @Test
    void alertWithTooLongIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale00000012345", "hom0000001", "dev0000001",
                        "alert.threshold.high", DATE_TIME, "high000001", "mea0000001"));
    }

    @Test
    void alertWithBlankHomeIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "", "dev0000001",
                        "alert.threshold.high", DATE_TIME, "high000001", "mea0000001"));
    }

    @Test
    void alertWithTooLongDeviceIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "hom0000001", "dev00000012345",
                        "alert.threshold.high", DATE_TIME, "high000001", "mea0000001"));
    }

    @Test
    void alertWithBlankMessageKeyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                        "", DATE_TIME, "high000001", "mea0000001"));
    }

    @Test
    void alertWithTooLongMessageKeyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                        "a".repeat(101), DATE_TIME, "high000001", "mea0000001"));
    }

    @Test
    void alertWithNullDateTimeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Alert.threshold("ale0000001", "hom0000001", "dev0000001",
                        "alert.threshold.high", null, "high000001", "mea0000001"));
    }

    @Test
    void resolveMarksTheAlertAsResolved() {
        Alert alert = createThresholdAlert();
        alert.resolve();
        assertEquals(AlertStatus.RESOLVED, alert.alertStatus());
        assertFalse(alert.isPending());
    }

    @Test
    void resolvingTwiceThrows() {
        Alert alert = createThresholdAlert();
        alert.resolve();
        assertThrows(IllegalStateException.class, alert::resolve);
    }

    @Test
    void equalityIsBasedOnId() {
        Alert first = createThresholdAlert();
        Alert second = Alert.connectivity("ale0000001", "hom0000002", "dev0000002",
                "alert.connectivity.lost", DATE_TIME.plusSeconds(60));
        Alert different = Alert.threshold("ale0000002", "hom0000001", "dev0000001",
                "alert.threshold.high", DATE_TIME, "high000001", "mea0000001");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
    }
}
