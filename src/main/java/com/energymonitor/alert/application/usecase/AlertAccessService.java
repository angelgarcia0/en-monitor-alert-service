package com.energymonitor.alert.application.usecase;

import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.port.in.AuthorizeAlertAccess;
import com.energymonitor.alert.application.port.out.AlertPersistencePort;
import com.energymonitor.alert.application.port.out.HomeLookupPort;

/**
 * Membership checks of the alert endpoints. This service contains no Spring annotations.
 */
public class AlertAccessService implements AuthorizeAlertAccess {

    private final AlertPersistencePort alertPort;
    private final HomeLookupPort homes;

    public AlertAccessService(AlertPersistencePort alertPort, HomeLookupPort homes) {
        this.alertPort = alertPort;
        this.homes = homes;
    }

    @Override
    public void requireHomeMember(String userId, String homeId) {
        if (!homes.isMember(userId, homeId)) {
            throw new AlertNotFoundException("no alerts for home " + homeId);
        }
    }

    @Override
    public void requireAlertMember(String userId, String alertId) {
        boolean member = alertPort.findActive(alertId)
                .map(alert -> homes.isMember(userId, alert.homeId()))
                .orElse(false);
        if (!member) {
            throw new AlertNotFoundException("no alert " + alertId);
        }
    }
}
