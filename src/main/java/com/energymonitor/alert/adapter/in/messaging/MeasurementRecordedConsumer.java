package com.energymonitor.alert.adapter.in.messaging;

import com.energymonitor.alert.application.command.RegisterThresholdAlertCommand;
import com.energymonitor.alert.application.port.in.RegisterDeviceLinkedAlert;
import com.energymonitor.alert.application.port.in.RegisterAnomalyAlert;
import com.energymonitor.alert.application.port.in.RegisterLimitAlert;
import com.energymonitor.alert.application.port.in.RegisterThresholdAlert;
import com.energymonitor.alert.infrastructure.AlertConsumersMessagingConfiguration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Hears {@code measurement.recorded} on {@code energy-monitor.measurement} and hands every reading
 * to the alert use cases: the first reading of a newly linked device, the power level of the
 * reading, whether it is far above what the device usually consumes and the home's consumption
 * limit. All of them run in one transaction, so an alert and
 * its {@code alert.raised} message are stored together or not at all.
 *
 * <p>A failure of the linked-device or limit checks is logged and never stops the threshold alert
 * of the same reading, as in energy-monitor-backend.
 *
 * <p>A message may arrive twice: a threshold alert already pending is not raised again, and the
 * limit and linked-device checks look at what is stored, so a second delivery changes nothing. A
 * message without a device or a time is dropped; one that fails (unreadable, or a service that
 * does not answer) goes back to the broker, which retries it and then dead-letters it.
 *
 * <p>Listens only with {@code messaging.consumers.enabled}: see
 * {@link AlertConsumersMessagingConfiguration}.
 */
public class MeasurementRecordedConsumer {

    static final String MEASUREMENT_RECORDED = "measurement.recorded";

    private static final Logger LOG = LoggerFactory.getLogger(MeasurementRecordedConsumer.class);

    private final RegisterThresholdAlert registerThresholdAlert;
    private final RegisterLimitAlert registerLimitAlert;
    private final RegisterAnomalyAlert registerAnomalyAlert;
    private final RegisterDeviceLinkedAlert registerDeviceLinkedAlert;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    public MeasurementRecordedConsumer(RegisterThresholdAlert registerThresholdAlert,
                                       RegisterLimitAlert registerLimitAlert,
                                       RegisterAnomalyAlert registerAnomalyAlert,
                                       RegisterDeviceLinkedAlert registerDeviceLinkedAlert,
                                       PlatformTransactionManager transactions, ObjectMapper json) {
        this.registerThresholdAlert = registerThresholdAlert;
        this.registerLimitAlert = registerLimitAlert;
        this.registerAnomalyAlert = registerAnomalyAlert;
        this.registerDeviceLinkedAlert = registerDeviceLinkedAlert;
        this.transaction = new TransactionTemplate(transactions);
        this.json = json;
    }

    @RabbitListener(queues = AlertConsumersMessagingConfiguration.MEASUREMENT_RECORDED_QUEUE,
            autoStartup = "${messaging.consumers.enabled:true}")
    public void on(Message message) {
        String type = typeOf(message);
        if (!MEASUREMENT_RECORDED.equals(type)) {
            LOG.warn("Ignoring measurement message of type '{}'", type);
            return;
        }
        Reading reading = json.readValue(message.getBody(), Reading.class);
        if (reading.deviceId() == null || reading.deviceId().isBlank() || reading.dateTime() == null) {
            LOG.warn("Ignoring {} {} without a device or a time", type, message.getMessageProperties().getMessageId());
            return;
        }
        transaction.executeWithoutResult(status -> evaluate(reading));
    }

    private void evaluate(Reading reading) {
        try {
            registerDeviceLinkedAlert.onMeasurement(reading.deviceId(), reading.dateTime());
        } catch (RuntimeException e) {
            LOG.warn("Linked-device alert of {} could not be evaluated: {}", reading.deviceId(), e.getMessage());
        }
        var command = new RegisterThresholdAlertCommand(
                reading.deviceId(), reading.idMeasurement(), reading.dateTime(), reading.activePower());
        registerThresholdAlert.register(command);
        try {
            registerAnomalyAlert.evaluate(command);
        } catch (RuntimeException e) {
            LOG.warn("Anomaly check of {} could not be evaluated: {}", reading.deviceId(), e.getMessage());
        }
        try {
            registerLimitAlert.evaluate(reading.deviceId(), reading.dateTime());
        } catch (RuntimeException e) {
            LOG.warn("Consumption limit of {} could not be evaluated: {}", reading.deviceId(), e.getMessage());
        }
    }

    /** The routing key it was published with, which the measurement service also sets as the type. */
    private static String typeOf(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        return routingKey != null ? routingKey : message.getMessageProperties().getType();
    }

    /** The body of {@code measurement.recorded}; only these fields are read. */
    record Reading(String idMeasurement, String deviceId, Instant dateTime, double activePower) {
    }
}
