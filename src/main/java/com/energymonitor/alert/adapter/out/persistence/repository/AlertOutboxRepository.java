package com.energymonitor.alert.adapter.out.persistence.repository;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertOutboxEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over {@code alert_outbox}.
 */
@Repository
public interface AlertOutboxRepository extends JpaRepository<AlertOutboxEntity, String> {

    /**
     * The messages still waiting for the broker, oldest first, so the order in which the alerts
     * were raised is the order in which the notification service hears about them.
     */
    @Query("""
            select m from AlertOutboxEntity m
            where m.publishedAt is null and m.discardedAt is null
            order by m.occurredAt asc, m.idMessage asc
            """)
    List<AlertOutboxEntity> findPending(Pageable page);
}
