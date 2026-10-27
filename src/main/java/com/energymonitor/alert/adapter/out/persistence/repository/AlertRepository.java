package com.energymonitor.alert.adapter.out.persistence.repository;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertEntity;
import com.energymonitor.alert.domain.model.AlertStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over {@code alert}.
 */
@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, String> {

    List<AlertEntity> findByHomeIdAndDeletedAtIsNullOrderByDateTimeDesc(String homeId);

    List<AlertEntity> findByHomeIdAndAlertStatusAndDeletedAtIsNullOrderByDateTimeDesc(
            String homeId, AlertStatus status);

    boolean existsByDeviceIdAndMessageKeyAndDateTimeGreaterThanEqual(String deviceId, String messageKey,
                                                                     Instant since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AlertEntity a set a.deletedAt = :now where a.idAlert = :id and a.deletedAt is null")
    int softDelete(@Param("id") String idAlert, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertEntity a set a.deletedAt = :now
             where a.homeId = :homeId and a.alertStatus = :status and a.deletedAt is null
            """)
    int softDeleteByStatus(@Param("homeId") String homeId, @Param("status") AlertStatus status,
                           @Param("now") Instant now);
}
