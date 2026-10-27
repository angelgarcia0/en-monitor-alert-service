/**
 * Alert service: the Alert bounded context of EnergyMonitor, deployed on its own.
 *
 * <p>Owns the alerts of a home: a reading above the power threshold of its device, the home's
 * consumption limit reached, a device that lost its connection, and the first reading of a newly
 * linked device. It raises them from the measurement and device events, resolves the
 * connectivity ones when the device comes back, lets the members of the home list, read and
 * delete them, and announces every new alert to the notification service, which delivers it.
 *
 * <p>Internal layout: {@code domain}, {@code application}, {@code adapter},
 * {@code infrastructure}.
 */
package com.energymonitor.alert;
