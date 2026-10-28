package com.energymonitor.alert.application.command;

import java.time.Instant;

public record RegisterConnectivityAlertCommand(String deviceId, Instant dateTime) {
}
