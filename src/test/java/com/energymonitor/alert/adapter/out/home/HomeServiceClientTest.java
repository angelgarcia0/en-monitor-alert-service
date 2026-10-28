package com.energymonitor.alert.adapter.out.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.alert.adapter.out.home.HomeServiceClient.LimitPeriod;
import com.energymonitor.alert.adapter.out.home.HomeServiceClient.RemoteAccess;
import com.energymonitor.alert.adapter.out.home.HomeServiceClient.RemoteLimits;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Home service client")
class HomeServiceClientTest {

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://home")
            .defaultHeader(HomeServiceClient.TOKEN_HEADER, "secret");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HomeServiceClient client = new HomeServiceClient(builder.build());

    @Test
    @DisplayName("reads what a user is in a home, sending the internal token")
    void access() {
        server.expect(requestTo("http://home/internal/homes/hom0000001/access?userId=use0000002"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HomeServiceClient.TOKEN_HEADER, "secret"))
                .andRespond(withSuccess("{\"exists\":true,\"member\":true,\"owner\":false}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.access("hom0000001", "use0000002"))
                .isEqualTo(new RemoteAccess(true, true, false));
        server.verify();
    }

    @Test
    @DisplayName("reads the limits of a home, and none when it has none")
    void limits() {
        server.expect(requestTo("http://home/internal/homes/hom0000001/thresholds"))
                .andExpect(header(HomeServiceClient.TOKEN_HEADER, "secret"))
                .andRespond(withSuccess("{\"dailyLimit\":6.0,\"monthlyLimit\":180.0,\"limitPeriod\":\"MONTHLY\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://home/internal/homes/hom0000099/thresholds"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.limits("hom0000001"))
                .contains(new RemoteLimits(6.0, 180.0, LimitPeriod.MONTHLY));
        assertThat(client.limits("hom0000099")).isEmpty();
    }

    @Test
    @DisplayName("an error or no answer is a home service that is unavailable")
    void unavailable() {
        server.expect(requestTo("http://home/internal/homes/hom0000001/access?userId=use0000001"))
                .andRespond(withException(new IOException("connection refused")));
        server.expect(requestTo("http://home/internal/homes/hom0000001/thresholds"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.access("hom0000001", "use0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class);
        assertThatThrownBy(() -> client.limits("hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class)
                .hasMessageContaining("(403)");
    }
}
