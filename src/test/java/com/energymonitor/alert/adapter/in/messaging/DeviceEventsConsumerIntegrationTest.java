package com.energymonitor.alert.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient;
import com.energymonitor.alert.adapter.out.device.DeviceServiceClient.RemoteDevice;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
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
 * The connectivity events of the device service reach the real alert use cases. The device is
 * answered by a mocked client of the device service.
 * {@code @Transactional}: everything joins the test's transaction, which is rolled back.
 */
@SpringBootTest
@Transactional
@DisplayName("Device events, end to end in the alert service")
class DeviceEventsConsumerIntegrationTest extends JwtKeyedTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final String DEVICE = "dee0000001";
    private static final String HOME = "deh0000001";

    @Autowired
    private DeviceEventsConsumer consumer;
    @MockitoBean
    private DeviceServiceClient deviceService;
    @Autowired
    private AlertPersistencePort alerts;

    @BeforeEach
    void linkDevice() {
        given(deviceService.device(DEVICE)).willReturn(Optional.of(
                new RemoteDevice(DEVICE, "Nevera", "appl000001", null, null, NOW, "DE0001", HOME)));
    }

    @Test
    @DisplayName("a lost connection raises one alert, even delivered twice, and a restored one resolves it")
    void raisesAndResolves() {
        consumer.on(message("device.connectivity-lost"));
        consumer.on(message("device.connectivity-lost"));

        assertThat(pendingConnectivity()).hasSize(1);

        consumer.on(message("device.connectivity-restored"));

        assertThat(pendingConnectivity()).isEmpty();
    }

    private List<Alert> pendingConnectivity() {
        return alerts.listActiveByHome(HOME, AlertStatus.PENDING).stream()
                .filter(alert -> alert.type() == AlertType.CONNECTIVITY && DEVICE.equals(alert.deviceId()))
                .toList();
    }

    private static Message message(String type) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(type);
        properties.setType(type);
        properties.setMessageId("dmo0000001");
        String body = "{\"deviceId\":\"" + DEVICE + "\",\"homeId\":\"" + HOME
                + "\",\"occurredAt\":\"2026-10-23T15:00:00Z\"}";
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
