package com.energymonitor.alert.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.alert.application.exception.AlertNotFoundException;
import com.energymonitor.alert.application.port.in.AuthorizeAlertAccess;
import com.energymonitor.alert.application.port.in.DeleteAlerts;
import com.energymonitor.alert.application.port.in.GetAlert;
import com.energymonitor.alert.application.port.in.ListAlerts;
import com.energymonitor.alert.application.port.in.ResolveAlert;
import com.energymonitor.alert.application.result.AlertResult;
import com.energymonitor.alert.domain.model.AlertStatus;
import com.energymonitor.alert.domain.model.AlertType;
import com.energymonitor.alert.infrastructure.JwtKeyedTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Route protection and shape of the alert endpoints. The requests carry an access token signed
 * with the test key pair, so the real filter chain runs and the caller is the token's subject.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AlertControllerWebTest extends JwtKeyedTest {

    private static final String CALLER = "use0000001";
    private static final Instant DATE_TIME = Instant.parse("2026-01-15T10:30:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListAlerts listAlerts;

    @MockitoBean
    private GetAlert getAlert;

    @MockitoBean
    private ResolveAlert resolveAlert;

    @MockitoBean
    private DeleteAlerts deleteAlerts;

    @MockitoBean
    private AuthorizeAlertAccess authorizeAlertAccess;

    private static AlertResult sampleResult(AlertStatus status) {
        return new AlertResult("ale0000001", "hom0000001", "dev0000001", AlertType.THRESHOLD,
                "alert.threshold.high", DATE_TIME, status, "high000001", "mea0000001");
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("homeId", "hom0000001"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listAlerts, authorizeAlertAccess);
    }

    @Test
    void listReturns200ForTheTokenSubject() throws Exception {
        given(listAlerts.list(any())).willReturn(List.of(sampleResult(AlertStatus.PENDING)));

        mvc.perform(get("/api/v1/alerts").param("homeId", "hom0000001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idAlert").value("ale0000001"))
                .andExpect(jsonPath("$[0].type").value("THRESHOLD"))
                .andExpect(jsonPath("$[0].alertStatus").value("PENDING"));

        verify(authorizeAlertAccess).requireHomeMember(CALLER, "hom0000001");
    }

    @Test
    void listWithStatusFilterReturns200() throws Exception {
        given(listAlerts.list(any())).willReturn(List.of(sampleResult(AlertStatus.PENDING)));

        mvc.perform(get("/api/v1/alerts")
                        .param("homeId", "hom0000001")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isOk());
    }

    @Test
    void notAMemberIs404() throws Exception {
        willThrow(new AlertNotFoundException("no home hom0000002"))
                .given(authorizeAlertAccess).requireHomeMember(CALLER, "hom0000002");

        mvc.perform(get("/api/v1/alerts").param("homeId", "hom0000002")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(listAlerts);
    }

    @Test
    void getReturns200() throws Exception {
        given(getAlert.get(any())).willReturn(sampleResult(AlertStatus.PENDING));

        mvc.perform(get("/api/v1/alerts/ale0000001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageKey").value("alert.threshold.high"))
                .andExpect(jsonPath("$.measurementId").value("mea0000001"));

        verify(authorizeAlertAccess).requireAlertMember(CALLER, "ale0000001");
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        given(getAlert.get(any())).willThrow(new AlertNotFoundException("no alert ale0000009"));

        mvc.perform(get("/api/v1/alerts/ale0000009")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("no alert ale0000009"));
    }

    @Test
    void resolveReturns200() throws Exception {
        given(resolveAlert.resolve(any())).willReturn(sampleResult(AlertStatus.RESOLVED));

        mvc.perform(put("/api/v1/alerts/ale0000001/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertStatus").value("RESOLVED"));
    }

    @Test
    void resolveReturns404WhenMissing() throws Exception {
        given(resolveAlert.resolve(any())).willThrow(new AlertNotFoundException("no alert ale0000009"));

        mvc.perform(put("/api/v1/alerts/ale0000009/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveReturns409WhenAlreadyResolved() throws Exception {
        given(resolveAlert.resolve(any()))
                .willThrow(new IllegalStateException("alert ale0000001 is already resolved"));

        mvc.perform(put("/api/v1/alerts/ale0000001/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("alert ale0000001 is already resolved"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/alerts/ale0000001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isNoContent());

        verify(authorizeAlertAccess).requireAlertMember(CALLER, "ale0000001");
        verify(deleteAlerts).delete("ale0000001");
    }

    @Test
    void deletePendingIs409() throws Exception {
        willThrow(new IllegalStateException("alert ale0000001 is still pending"))
                .given(deleteAlerts).delete("ale0000001");

        mvc.perform(delete("/api/v1/alerts/ale0000001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteResolvedReturnsHowMany() throws Exception {
        given(deleteAlerts.deleteResolved("hom0000001")).willReturn(3);

        mvc.perform(delete("/api/v1/alerts").param("homeId", "hom0000001")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(3));

        verify(authorizeAlertAccess).requireHomeMember(CALLER, "hom0000001");
    }
}
