package com.energymonitor.alert.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient;
import com.energymonitor.alert.adapter.out.device.DeviceServiceClient.RemoteDevice;
import com.energymonitor.alert.adapter.out.home.HomeServiceClient;
import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient;
import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteLevel;
import com.energymonitor.alert.adapter.out.persistence.entity.AlertOutboxEntity;
import com.energymonitor.alert.adapter.out.persistence.repository.AlertOutboxRepository;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import com.energymonitor.alert.domain.model.RiskConsumption;
import com.energymonitor.alert.infrastructure.JwtKeyedTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The readings of the measurement service reach the real alert use cases: a HIGH reading raises
 * one THRESHOLD alert, however many times it is delivered, with its {@code alert.raised} in the
 * outbox, and a LOW one resolves it. The device, the levels and the limits are answered by mocked
 * clients of the three services.
 * {@code @Transactional}: everything joins the test's transaction, which is rolled back.
 */
@SpringBootTest
@Transactional
@DisplayName("Recorded readings, end to end in the alert service")
class MeasurementRecordedConsumerIntegrationTest extends JwtKeyedTest {

    private static final Instant NOW = Instant.parse("2026-10-24T20:00:00Z");
    private static final String DEVICE = "mre0000001";
    private static final String HOME = "mrh0000001";

    @Autowired
    private MeasurementRecordedConsumer consumer;
    @MockitoBean
    private DeviceServiceClient deviceService;
    @MockitoBean
    private MeasurementServiceClient measurementService;
    @MockitoBean
    private HomeServiceClient homeService;
    @Autowired
    private AlertPersistencePort alerts;
    @Autowired
    private AlertOutboxRepository outbox;

    @BeforeEach
    void linkDeviceAndLevels() {
        RemoteDevice fridge = new RemoteDevice(DEVICE, "Nevera", "appl000001", null, null, NOW, "MR0001", HOME);
        given(deviceService.device(DEVICE)).willReturn(Optional.of(fridge));
        given(deviceService.devicesOfHome(HOME)).willReturn(List.of(fridge));
        given(measurementService.levels()).willReturn(List.of(
                new RemoteLevel("lev0000001", RiskConsumption.LOW, "Bajo", 0.0, 1500.0),
                new RemoteLevel("lev0000003", RiskConsumption.HIGH, "Alto", 1500.0, 3000.0)));
        given(measurementService.hourlyEnergy(any(), any(), any())).willReturn(List.of());
        given(homeService.limits(HOME)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("a high reading raises one alert with its message, even delivered twice, and a low one resolves it")
    void raisesAndResolves() {
        consumer.on(message("MEA0000001", 2000.0));
        consumer.on(message("MEA0000001", 2000.0));

        List<Alert> pending = pendingThreshold();
        assertThat(pending).hasSize(1)
                .allMatch(alert -> "MEA0000001".equals(alert.measurementId()));
        assertThat(raisedFor(pending.getFirst().idAlert())).hasSize(1);

        consumer.on(message("MEA0000002", 300.0));

        assertThat(pendingThreshold()).isEmpty();
    }

    private List<Alert> pendingThreshold() {
        return alerts.listActiveByHome(HOME, AlertStatus.PENDING).stream()
                .filter(alert -> alert.type() == AlertType.THRESHOLD && DEVICE.equals(alert.deviceId()))
                .toList();
    }

    private List<AlertOutboxEntity> raisedFor(String idAlert) {
        return outbox.findAll().stream()
                .filter(m -> "alert.raised".equals(m.getMessageType()))
                .filter(m -> m.getPayload().contains("\"idAlert\":\"" + idAlert + "\""))
                .toList();
    }

    private static Message message(String idMeasurement, double activePower) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey("measurement.recorded");
        properties.setType("measurement.recorded");
        properties.setMessageId("MMO" + idMeasurement.substring(3));
        String body = "{\"idMeasurement\":\"" + idMeasurement + "\",\"deviceId\":\"" + DEVICE
                + "\",\"dateTime\":\"2026-10-24T20:00:00Z\",\"voltage\":230.0,\"current\":1.0,"
                + "\"activePower\":" + activePower + ",\"storedEnergy\":12.8}";
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
