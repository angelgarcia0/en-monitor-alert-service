package com.energymonitor.alert.application.port.in;

/**
 * Use case: only members of a home may read or resolve its alerts. Anything else looks like a
 * missing alert ({@code AlertNotFoundException}, 404).
 */
public interface AuthorizeAlertAccess {

    void requireHomeMember(String userId, String homeId);

    void requireAlertMember(String userId, String alertId);
}
