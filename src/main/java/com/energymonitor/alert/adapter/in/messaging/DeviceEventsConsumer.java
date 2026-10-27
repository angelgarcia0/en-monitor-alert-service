package com.energymonitor.alert.adapter.in.messaging;

import com.energymonitor.alert.adapter.out.device.RemoteDevices;
import com.energymonitor.alert.application.command.RegisterConnectivityAlertCommand;
import com.energymonitor.alert.application.port.in.RegisterConnectivityAlert;
import com.energymonitor.alert.application.port.in.ResolveConnectivityAlerts;
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
 * Hears what the device service announces on {@code energy-monitor.device}.
 *
 * <ul>
 *   <li>{@code device.connectivity-lost} raises a CONNECTIVITY alert and
 *       {@code device.connectivity-restored} resolves it, each in a transaction of its own, the
 *       {@code alert.raised} message included.</li>
 *   <li>{@code device.linked} and {@code device.unlinked} drop what this service keeps of the
 *       device and of the home, so a reading of a device just linked is not skipped for half a
 *       minute.</li>
 * </ul>
 *
 * <p>A message may arrive twice: a connectivity alert already pending is not raised again, a
 * resolved one stays resolved, and forgetting twice only costs one more request. An unknown type,
 * or a message without a device, is dropped.
 *
 * <p>Listens only with {@code messaging.consumers.enabled}: see
 * {@link AlertConsumersMessagingConfiguration}.
 */
public class DeviceEventsConsumer {

    static final String DEVICE_LINKED = "device.linked";
    static final String DEVICE_UNLINKED = "device.unlinked";
    static final String CONNECTIVITY_LOST = "device.connectivity-lost";
    static final String CONNECTIVITY_RESTORED = "device.connectivity-restored";

    private static final Logger LOG = LoggerFactory.getLogger(DeviceEventsConsumer.class);

    private final RegisterConnectivityAlert registerConnectivityAlert;
    private final ResolveConnectivityAlerts resolveConnectivityAlerts;
    private final RemoteDevices devices;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    public DeviceEventsConsumer(RegisterConnectivityAlert registerConnectivityAlert,
                                ResolveConnectivityAlerts resolveConnectivityAlerts,
                                RemoteDevices devices, PlatformTransactionManager transactions,
                                ObjectMapper json) {
        this.registerConnectivityAlert = registerConnectivityAlert;
        this.resolveConnectivityAlerts = resolveConnectivityAlerts;
        this.devices = devices;
        this.transaction = new TransactionTemplate(transactions);
        this.json = json;
    }

    @RabbitListener(queues = AlertConsumersMessagingConfiguration.DEVICE_EVENTS_QUEUE,
            autoStartup = "${messaging.consumers.enabled:true}")
    public void on(Message message) {
        String type = typeOf(message);
        DeviceMessage body = json.readValue(message.getBody(), DeviceMessage.class);
        if (body.deviceId() == null || body.deviceId().isBlank()) {
            LOG.warn("Ignoring {} {} without a deviceId", type, message.getMessageProperties().getMessageId());
            return;
        }
        switch (type == null ? "" : type) {
            case CONNECTIVITY_LOST -> transaction.executeWithoutResult(status ->
                    registerConnectivityAlert.register(
                            new RegisterConnectivityAlertCommand(body.deviceId(), body.at())));
            case CONNECTIVITY_RESTORED -> transaction.executeWithoutResult(status ->
                    resolveConnectivityAlerts.resolveFor(body.deviceId()));
            case DEVICE_LINKED, DEVICE_UNLINKED -> devices.forget(body.deviceId(), body.homeId());
            default -> LOG.warn("Ignoring device message of type '{}'", type);
        }
    }

    /** The routing key it was published with, which the device service also sets as the type. */
    private static String typeOf(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        return routingKey != null ? routingKey : message.getMessageProperties().getType();
    }

    /**
     * The fields of every device message this service reads; each type carries its own instant
     * ({@code occurredAt}, {@code linkedAt} or {@code unlinkedAt}).
     */
    record DeviceMessage(String deviceId, String homeId, Instant occurredAt, Instant linkedAt,
                         Instant unlinkedAt) {

        Instant at() {
            return occurredAt != null ? occurredAt : linkedAt != null ? linkedAt : unlinkedAt;
        }
    }
}
