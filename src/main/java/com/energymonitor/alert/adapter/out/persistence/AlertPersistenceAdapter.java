package com.energymonitor.alert.adapter.out.persistence;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertEntity;
import com.energymonitor.alert.adapter.out.persistence.mapper.AlertMapper;
import com.energymonitor.alert.adapter.out.persistence.repository.AlertRepository;
import com.energymonitor.alert.adapter.out.persistence.support.Instants;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.domain.model.Alert;
import com.energymonitor.alert.domain.model.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for {@link Alert}.
 */
@Component
public class AlertPersistenceAdapter implements AlertPersistencePort {

    private final AlertRepository repository;
    private final AlertMapper mapper;

    public AlertPersistenceAdapter(AlertRepository repository, AlertMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Alert save(Alert alert) {
        AlertEntity entity = repository.findById(alert.idAlert())
                .map(existing -> {
                    mapper.applyTo(existing, alert);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(alert));
        repository.save(entity);
        return alert;
    }

    @Override
    public Optional<Alert> findActive(String idAlert) {
        return repository.findById(idAlert)
                .filter(entity -> entity.getDeletedAt() == null)
                .map(mapper::toDomain);
    }

    @Override
    public List<Alert> listActiveByHome(String homeId, AlertStatus status) {
        var entities = status == null
                ? repository.findByHomeIdAndDeletedAtIsNullOrderByDateTimeDesc(homeId)
                : repository.findByHomeIdAndAlertStatusAndDeletedAtIsNullOrderByDateTimeDesc(homeId, status);
        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String idAlert) {
        repository.softDelete(idAlert, Instants.now());
    }

    @Override
    @Transactional
    public int deleteResolved(String homeId) {
        return repository.softDeleteByStatus(homeId, AlertStatus.RESOLVED, Instants.now());
    }

    @Override
    public boolean existsForDeviceSince(String deviceId, String messageKey, Instant since) {
        return repository.existsByDeviceIdAndMessageKeyAndDateTimeGreaterThanEqual(deviceId, messageKey,
                Instants.truncate(since));
    }
}
