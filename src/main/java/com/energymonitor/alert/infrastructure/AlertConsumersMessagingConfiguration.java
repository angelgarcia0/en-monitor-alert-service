package com.energymonitor.alert.infrastructure;

import com.energymonitor.alert.adapter.in.messaging.DeviceEventsConsumer;
import com.energymonitor.alert.adapter.in.messaging.MeasurementRecordedConsumer;
import com.energymonitor.alert.adapter.out.device.RemoteDevices;
import com.energymonitor.alert.application.port.in.RegisterConnectivityAlert;
import com.energymonitor.alert.application.port.in.RegisterDeviceLinkedAlert;
import com.energymonitor.alert.application.port.in.RegisterLimitAlert;
import com.energymonitor.alert.application.port.in.RegisterThresholdAlert;
import com.energymonitor.alert.application.port.in.ResolveConnectivityAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * The queues through which this service hears the readings and the device events it raises
 * alerts from.
 *
 * <ul>
 *   <li>{@value #MEASUREMENT_RECORDED_QUEUE}: {@code measurement.recorded} of
 *       {@code energy-monitor.measurement}, every reading the measurement service stores.</li>
 *   <li>{@value #DEVICE_EVENTS_QUEUE}: {@code device.connectivity-lost},
 *       {@code device.connectivity-restored}, {@code device.linked} and {@code device.unlinked} of
 *       {@code energy-monitor.device}.</li>
 * </ul>
 *
 * <p>Both are durable, and dead-lettered to their {@code .dlq} once a message has failed every
 * retry.
 *
 * <p>The queues are always declared, but their listeners start only with
 * {@code messaging.consumers.enabled}, off by default: while energy-monitor-backend still raises
 * the alerts from its own queues, both would raise them. Meanwhile the readings and the device
 * events pile up here. What piled up was already handled by the backend, so the queues are purged
 * right before the switch is turned on: an old reading replayed after its alert was resolved would
 * raise it again.
 */
@Configuration(proxyBeanMethods = false)
public class AlertConsumersMessagingConfiguration {

    public static final String MEASUREMENT_RECORDED_QUEUE = "alert-service.measurement.recorded";
    public static final String DEVICE_EVENTS_QUEUE = "alert-service.device.events";

    private static final Logger LOG = LoggerFactory.getLogger(AlertConsumersMessagingConfiguration.class);

    @Bean
    public Declarables alertConsumerQueues(
            @Value("${messaging.measurement-exchange}") String measurementExchange,
            @Value("${messaging.device-exchange}") String deviceExchange,
            @Value("${messaging.dead-letter-exchange}") String deadLetterExchange) {
        TopicExchange measurement = new TopicExchange(measurementExchange, true, false);
        TopicExchange device = new TopicExchange(deviceExchange, true, false);
        DirectExchange deadLetters = new DirectExchange(deadLetterExchange, true, false);

        Queue recorded = queue(MEASUREMENT_RECORDED_QUEUE, deadLetterExchange);
        Queue recordedDead = QueueBuilder.durable(MEASUREMENT_RECORDED_QUEUE + ".dlq").build();
        Queue events = queue(DEVICE_EVENTS_QUEUE, deadLetterExchange);
        Queue eventsDead = QueueBuilder.durable(DEVICE_EVENTS_QUEUE + ".dlq").build();

        return new Declarables(measurement, device, deadLetters,
                recorded, recordedDead, events, eventsDead,
                BindingBuilder.bind(recorded).to(measurement).with("measurement.recorded"),
                BindingBuilder.bind(events).to(device).with("device.connectivity-lost"),
                BindingBuilder.bind(events).to(device).with("device.connectivity-restored"),
                BindingBuilder.bind(events).to(device).with("device.linked"),
                BindingBuilder.bind(events).to(device).with("device.unlinked"),
                BindingBuilder.bind(recordedDead).to(deadLetters).with(MEASUREMENT_RECORDED_QUEUE),
                BindingBuilder.bind(eventsDead).to(deadLetters).with(DEVICE_EVENTS_QUEUE));
    }

    /**
     * Declares the queues at startup. Spring AMQP declares them when the first connection opens,
     * and with the listeners off nothing may open one for a long while: the queues would not exist
     * and the readings published meanwhile would be lost. A broker that is down is only logged;
     * the queues are declared as soon as a connection opens.
     *
     * <p>Off in the test profile: queues left on a local broker by the suite would keep piling up
     * the readings of a running stack with nobody consuming them.
     */
    @Bean
    @ConditionalOnProperty(name = "messaging.consumers.declare-on-startup", havingValue = "true",
            matchIfMissing = true)
    public ApplicationRunner declareAlertConsumerQueues(AmqpAdmin admin) {
        return args -> {
            try {
                admin.initialize();
            } catch (AmqpException unavailable) {
                LOG.warn("The alert queues could not be declared yet, the broker did not answer: {}",
                        unavailable.getMessage());
            }
        };
    }

    private static Queue queue(String name, String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(name)
                .build();
    }

    @Bean
    public MeasurementRecordedConsumer measurementRecordedConsumer(
            RegisterThresholdAlert registerThresholdAlert, RegisterLimitAlert registerLimitAlert,
            RegisterDeviceLinkedAlert registerDeviceLinkedAlert,
            PlatformTransactionManager transactions, ObjectMapper json) {
        return new MeasurementRecordedConsumer(registerThresholdAlert, registerLimitAlert,
                registerDeviceLinkedAlert, transactions, json);
    }

    /** Forgetting goes to the cache of devices, {@link RemoteDevices}. */
    @Bean
    public DeviceEventsConsumer deviceEventsConsumer(
            RegisterConnectivityAlert registerConnectivityAlert,
            ResolveConnectivityAlerts resolveConnectivityAlerts, RemoteDevices devices,
            PlatformTransactionManager transactions, ObjectMapper json) {
        return new DeviceEventsConsumer(registerConnectivityAlert, resolveConnectivityAlerts,
                devices, transactions, json);
    }
}
