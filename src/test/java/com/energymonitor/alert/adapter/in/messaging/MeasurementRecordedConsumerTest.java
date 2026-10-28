package com.energymonitor.alert.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.port.in.RegisterDeviceLinkedAlert;
import com.energymonitor.alert.application.port.in.RegisterLimitAlert;
import com.energymonitor.alert.application.port.in.RegisterThresholdAlert;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Measurement recorded consumer")
class MeasurementRecordedConsumerTest {

    private static final Instant AT = Instant.parse("2026-10-24T20:00:00Z");
    private static final String READING = "{\"idMeasurement\":\"MEA0000001\",\"deviceId\":\"DEV0000001\","
            + "\"dateTime\":\"2026-10-24T20:00:00Z\",\"voltage\":231.4,\"current\":1.2,"
            + "\"activePower\":285.5,\"storedEnergy\":12.8}";

    private final RegisterThresholdAlert threshold = mock(RegisterThresholdAlert.class);
    private final RegisterLimitAlert limit = mock(RegisterLimitAlert.class);
    private final RegisterDeviceLinkedAlert linked = mock(RegisterDeviceLinkedAlert.class);
    private final MeasurementRecordedConsumer consumer = new MeasurementRecordedConsumer(threshold,
            limit, linked, mock(PlatformTransactionManager.class), JsonMapper.builder().build());

    @Test
    @DisplayName("a reading goes to the threshold, limit and linked-device checks")
    void reading() {
        consumer.on(message("measurement.recorded", READING));

        verify(linked).onMeasurement("DEV0000001", AT);
        verify(threshold).register(new RegisterThresholdAlertCommand("DEV0000001", "MEA0000001", AT, 285.5));
        verify(limit).evaluate("DEV0000001", AT);
    }

    @Test
    @DisplayName("a failed limit or linked-device check does not stop the threshold alert")
    void checksDoNotStopTheThreshold() {
        willThrow(new IllegalStateException("home service down")).given(linked).onMeasurement(any(), any());
        willThrow(new IllegalStateException("measurement service down")).given(limit).evaluate(any(), any());

        consumer.on(message("measurement.recorded", READING));

        verify(threshold).register(new RegisterThresholdAlertCommand("DEV0000001", "MEA0000001", AT, 285.5));
    }

    @Test
    @DisplayName("the type is read from the message when the routing key is missing")
    void typeFallback() {
        MessageProperties properties = new MessageProperties();
        properties.setType("measurement.recorded");
        consumer.on(new Message(READING.getBytes(StandardCharsets.UTF_8), properties));

        verify(threshold).register(any());
    }

    @Test
    @DisplayName("another type, or a reading without a device or a time, is dropped")
    void dropped() {
        consumer.on(message("measurement.deleted", READING));
        consumer.on(message("measurement.recorded", READING.replace("\"deviceId\":\"DEV0000001\",", "")));
        consumer.on(message("measurement.recorded", READING.replace("\"dateTime\":\"2026-10-24T20:00:00Z\",", "")));

        verifyNoInteractions(threshold, limit, linked);
    }

    @Test
    @DisplayName("an unreadable body fails, so the broker retries it and then dead-letters it")
    void unreadable() {
        assertThatThrownBy(() -> consumer.on(message("measurement.recorded", "{not json")))
                .isInstanceOf(JacksonException.class);
    }

    private static Message message(String routingKey, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setType(routingKey);
        properties.setMessageId("MMO0000001");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
