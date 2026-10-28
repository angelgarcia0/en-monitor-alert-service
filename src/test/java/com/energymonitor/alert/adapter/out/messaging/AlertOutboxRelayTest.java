package com.energymonitor.alert.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertOutboxEntity;
import com.energymonitor.alert.adapter.out.persistence.repository.AlertOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

@DisplayName("Alert outbox relay")
class AlertOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");

    private final AlertOutboxRepository outbox = mock(AlertOutboxRepository.class);
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final AlertOutboxRelay relay =
            new AlertOutboxRelay(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC), 50);

    @Test
    @DisplayName("publishes the pending messages oldest first and marks them as published")
    void publishesInOrder() {
        var first = message("hmo0000001");
        var second = message("hmo0000002");
        given(outbox.findPending(any(Pageable.class))).willReturn(List.of(first, second));

        int published = relay.relay();

        assertThat(published).isEqualTo(2);
        assertThat(publisher.ids).containsExactly("hmo0000001", "hmo0000002");
        assertThat(publisher.types).containsOnly("alert.raised");
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("stops at the first failure, so a later message never overtakes it")
    void stopsAtFirstFailure() {
        var failing = message("hmo0000003");
        var later = message("hmo0000004");
        given(outbox.findPending(any(Pageable.class))).willReturn(List.of(failing, later));
        publisher.failOn = "hmo0000003";

        int published = relay.relay();

        assertThat(published).isZero();
        assertThat(publisher.ids).containsExactly("hmo0000003");
        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo("no queue is bound for alert.raised yet");
        assertThat(later.getPublishedAt()).isNull();
        assertThat(later.getAttempts()).isZero();
    }

    @Test
    @DisplayName("a long error is cut to the column size")
    void truncatesLongErrors() {
        var failing = message("hmo0000005");
        given(outbox.findPending(any(Pageable.class))).willReturn(List.of(failing));
        publisher.failOn = "hmo0000005";
        publisher.error = "x".repeat(400);

        relay.relay();

        assertThat(failing.getLastError()).hasSize(255);
    }

    @Test
    @DisplayName("nothing pending publishes nothing")
    void nothingPending() {
        given(outbox.findPending(any(Pageable.class))).willReturn(List.of());

        assertThat(relay.relay()).isZero();
        assertThat(publisher.ids).isEmpty();
    }

    private static AlertOutboxEntity message(String id) {
        var message = new AlertOutboxEntity();
        message.setIdMessage(id);
        message.setMessageType("alert.raised");
        message.setPayload("{\"idAlert\":\"alr0000001\"}");
        message.setOccurredAt(NOW.minusSeconds(5));
        return message;
    }

    private static final class RecordingPublisher implements AlertOutboxPublisher {

        private final List<String> ids = new ArrayList<>();
        private final List<String> types = new ArrayList<>();
        private String failOn;
        private String error = "no queue is bound for alert.raised yet";

        @Override
        public void publish(String idMessage, String type, String payload, Instant occurredAt) {
            ids.add(idMessage);
            types.add(type);
            if (idMessage.equals(failOn)) {
                throw new IllegalStateException(error);
            }
        }
    }
}
