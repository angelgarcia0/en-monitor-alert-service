package com.energymonitor.alert.application.command;

import java.time.Instant;

/**
 * Input of {@code RegisterThresholdAlert}: evaluates a recorded measurement and raises a
 * THRESHOLD alert when the reading falls into a risky consumption level.
 *
 * @param deviceId      identifier of the measuring device
 * @param measurementId identifier of the measurement that triggered the evaluation
 * @param dateTime      business timestamp of the reading
 * @param activePower   active power reading to classify
 */
public record RegisterThresholdAlertCommand(String deviceId, String measurementId,
                                            Instant dateTime, double activePower) {
}
