package com.energymonitor.alert.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.energymonitor.alert.adapter.out.event.AlertEventAdapter.AlertRaisedMessage;
import com.energymonitor.alert.adapter.out.messaging.AlertOutboxWriter;
import com.energymonitor.alert.domain.model.AlertRaised;
import com.energymonitor.alert.domain.model.AlertType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Alert events through the outbox")
class AlertEventAdapterTest {

    private static final Instant AT = Instant.parse("2026-10-23T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-23T15:00:03Z");

    private final AlertOutboxWriter outbox = mock(AlertOutboxWriter.class);
    private final AlertEventAdapter adapter =
            new AlertEventAdapter(outbox, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("an alert goes to the outbox as alert.raised, with the body the notification service reads")
    void writesAlertRaised() {
        AlertRaised event = new AlertRaised("alr0000001", "hom0000001", "dev0000001",
                AlertType.THRESHOLD, "alert.threshold.high", AT, "lvl0000001", "mea0000001");

        adapter.publish(event);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("alert.raised"), body.capture(), eq(NOW));
        assertThat(body.getValue()).isEqualTo(new AlertRaisedMessage("alr0000001", "hom0000001",
                "dev0000001", "THRESHOLD", "alert.threshold.high", AT));
    }

    @Test
    @DisplayName("an alert about the whole home goes without a device")
    void writesHomeScopedAlert() {
        AlertRaised event = new AlertRaised("alr0000002", "hom0000001", null, AlertType.LIMIT,
                "alert.limit.daily", AT, null, null);

        adapter.publish(event);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("alert.raised"), body.capture(), eq(NOW));
        assertThat(body.getValue()).isEqualTo(new AlertRaisedMessage("alr0000002", "hom0000001",
                null, "LIMIT", "alert.limit.daily", AT));
    }
}
