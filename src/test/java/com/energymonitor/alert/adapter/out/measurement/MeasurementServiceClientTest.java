package com.energymonitor.alert.adapter.out.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteHourlyEnergy;
import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteLevel;
import com.energymonitor.alert.domain.model.RiskConsumption;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Measurement service client")
class MeasurementServiceClientTest {

    private static final Instant FROM = Instant.parse("2026-10-24T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-24T23:59:59Z");

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://measurement")
            .defaultHeader(MeasurementServiceClient.TOKEN_HEADER, "secret");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MeasurementServiceClient client = new MeasurementServiceClient(builder.build());

    @Test
    @DisplayName("reads the consumption levels, sending the internal token")
    void levels() {
        server.expect(requestTo("http://measurement/internal/consumption-levels"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(MeasurementServiceClient.TOKEN_HEADER, "secret"))
                .andRespond(withSuccess("[{\"idConsumptionLevel\":\"lev0000003\",\"name\":\"HIGH\","
                        + "\"description\":\"Alto\",\"minLimit\":1500.0,\"maxLimit\":3000.0}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.levels()).containsExactly(
                new RemoteLevel("lev0000003", RiskConsumption.HIGH, "Alto", 1500.0, 3000.0));
        server.verify();
    }

    @Test
    @DisplayName("asks the hourly energy of every device in the range")
    void hourlyEnergy() {
        server.expect(requestTo("http://measurement/internal/measurements/hourly-energy"
                        + "?deviceId=DEV0000001&deviceId=DEV0000002&from=2026-10-24T00:00:00Z&to=2026-10-24T23:59:59Z"))
                .andRespond(withSuccess("[{\"deviceId\":\"DEV0000001\",\"hourStart\":\"2026-10-24T10:00:00Z\","
                        + "\"averagePower\":200.0,\"energy\":0.2}]", MediaType.APPLICATION_JSON));

        assertThat(client.hourlyEnergy(List.of("DEV0000001", "DEV0000002"), FROM, TO)).containsExactly(
                new RemoteHourlyEnergy("DEV0000001", Instant.parse("2026-10-24T10:00:00Z"), 200.0, 0.2));
    }

    @Test
    @DisplayName("a refused token, a server error or no answer is unavailable")
    void unavailable() {
        server.expect(requestTo("http://measurement/internal/consumption-levels"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("http://measurement/internal/measurements/hourly-energy"
                        + "?deviceId=DEV0000001&from=2026-10-24T00:00:00Z&to=2026-10-24T23:59:59Z"))
                .andRespond(withServerError());
        server.expect(requestTo("http://measurement/internal/consumption-levels"))
                .andRespond(withException(new IOException("refused")));

        assertThatThrownBy(client::levels)
                .isInstanceOf(MeasurementServiceUnavailableException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> client.hourlyEnergy(List.of("DEV0000001"), FROM, TO))
                .isInstanceOf(MeasurementServiceUnavailableException.class).hasMessageContaining("500");
        assertThatThrownBy(client::levels).isInstanceOf(MeasurementServiceUnavailableException.class);
    }
}
