package com.energymonitor.alert.adapter.out.event;

import com.energymonitor.alert.adapter.out.messaging.AlertOutboxWriter;
import com.energymonitor.alert.application.port.out.AlertEventPort;
import com.energymonitor.alert.domain.model.AlertRaised;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Announces a raised alert as {@code alert.raised} on the topic exchange
 * {@code messaging.alert.exchange}, through the outbox, for the notification service, which
 * delivers it to the members of the home.
 *
 * <p>The body of {@code alert.raised} is the contract with the notification service, unchanged
 * from the one energy-monitor-backend published: {@code {idAlert, homeId, deviceId, type,
 * messageKey, dateTime}}.
 */
@Component
public class AlertEventAdapter implements AlertEventPort {

    static final String ALERT_RAISED = "alert.raised";

    private final AlertOutboxWriter outbox;
    private final Clock clock;

    public AlertEventAdapter(AlertOutboxWriter outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * The message is dated when it is written, not with the reading: a late sample queued by a
     * module must not overtake the alerts already waiting in the outbox.
     */
    @Override
    public void publish(AlertRaised event) {
        outbox.write(ALERT_RAISED, new AlertRaisedMessage(event.idAlert(), event.homeId(),
                event.deviceId(), event.type().name(), event.messageKey(), event.dateTime()),
                clock.instant());
    }

    /**
     * Body of {@code alert.raised}.
     *
     * @param deviceId null when the alert is about the whole home
     */
    record AlertRaisedMessage(String idAlert, String homeId, String deviceId, String type,
                              String messageKey, Instant dateTime) {
    }
}
