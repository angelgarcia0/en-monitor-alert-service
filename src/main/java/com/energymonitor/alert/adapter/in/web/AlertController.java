package com.energymonitor.alert.adapter.in.web;

import com.energymonitor.alert.adapter.in.web.dto.AlertResponse;
import com.energymonitor.alert.application.command.GetAlertQuery;
import com.energymonitor.alert.application.command.ListAlertsQuery;
import com.energymonitor.alert.application.command.ResolveAlertCommand;
import com.energymonitor.alert.application.port.in.AuthorizeAlertAccess;
import com.energymonitor.alert.application.port.in.DeleteAlerts;
import com.energymonitor.alert.application.port.in.GetAlert;
import com.energymonitor.alert.application.port.in.ListAlerts;
import com.energymonitor.alert.application.port.in.ResolveAlert;
import com.energymonitor.alert.application.result.AlertResult;
import com.energymonitor.alert.domain.model.AlertStatus;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for the alert inbox.
 *
 * <p>There is deliberately no POST: alerts are raised by the system, from the readings of the
 * measurement service and the events of the device service, never by clients.
 *
 * <p>Only members of the home see, read or delete its alerts; anything else is a 404.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final ListAlerts listAlerts;
    private final GetAlert getAlert;
    private final ResolveAlert resolveAlert;
    private final DeleteAlerts deleteAlerts;
    private final AuthorizeAlertAccess authorizeAlertAccess;
    private final CurrentUserResolver currentUser;

    public AlertController(ListAlerts listAlerts, GetAlert getAlert, ResolveAlert resolveAlert,
                           DeleteAlerts deleteAlerts, AuthorizeAlertAccess authorizeAlertAccess,
                           CurrentUserResolver currentUser) {
        this.listAlerts = listAlerts;
        this.getAlert = getAlert;
        this.resolveAlert = resolveAlert;
        this.deleteAlerts = deleteAlerts;
        this.authorizeAlertAccess = authorizeAlertAccess;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AlertResponse> list(@RequestParam String homeId,
                                    @RequestParam(required = false) AlertStatus status) {
        authorizeAlertAccess.requireHomeMember(currentUser.resolveCurrentUserId(), homeId);
        return listAlerts.list(new ListAlertsQuery(homeId, status)).stream()
                .map(AlertController::toResponse)
                .toList();
    }

    @GetMapping("/{idAlert}")
    public AlertResponse get(@PathVariable String idAlert) {
        authorizeAlertAccess.requireAlertMember(currentUser.resolveCurrentUserId(), idAlert);
        return toResponse(getAlert.get(new GetAlertQuery(idAlert)));
    }

    /**
     * Marks an informational (DEVICE) alert as read. Threshold and connectivity alerts are
     * resolved by the system when the condition clears; asking for them is a 409.
     */
    @PutMapping("/{idAlert}/read")
    public AlertResponse markRead(@PathVariable String idAlert) {
        authorizeAlertAccess.requireAlertMember(currentUser.resolveCurrentUserId(), idAlert);
        return toResponse(resolveAlert.resolve(new ResolveAlertCommand(idAlert)));
    }

    /** Removes a resolved (or read) alert; a pending one is a 409. */
    @DeleteMapping("/{idAlert}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String idAlert) {
        authorizeAlertAccess.requireAlertMember(currentUser.resolveCurrentUserId(), idAlert);
        deleteAlerts.delete(idAlert);
    }

    /** Removes every resolved (or read) alert of a home. */
    @DeleteMapping
    public Map<String, Integer> deleteResolved(@RequestParam String homeId) {
        authorizeAlertAccess.requireHomeMember(currentUser.resolveCurrentUserId(), homeId);
        return Map.of("deleted", deleteAlerts.deleteResolved(homeId));
    }

    private static AlertResponse toResponse(AlertResult result) {
        return new AlertResponse(result.idAlert(), result.homeId(), result.deviceId(),
                result.type(), result.messageKey(), result.dateTime(), result.alertStatus(),
                result.consumptionLevelId(), result.measurementId());
    }
}
