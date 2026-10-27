package com.energymonitor.alert.application.port.in;

/**
 * Use case: remove alerts from the inbox. Only RESOLVED ones: a pending alert describes a
 * problem that is still there (or, for an informational one, something not read yet).
 */
public interface DeleteAlerts {

    /** @throws IllegalStateException when the alert is still pending */
    void delete(String idAlert);

    /** @return how many resolved alerts of the home were removed */
    int deleteResolved(String homeId);
}
