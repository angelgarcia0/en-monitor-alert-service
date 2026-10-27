package com.energymonitor.alert.adapter.out.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.alert.adapter.out.home.HomeServiceClient.LimitPeriod;
import com.energymonitor.alert.adapter.out.home.HomeServiceClient.RemoteAccess;
import com.energymonitor.alert.adapter.out.home.HomeServiceClient.RemoteLimits;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the cached homes ask the home service, when they ask again, and what they serve while the
 * service does not answer. The clock of the caches is moved by hand.
 */
@DisplayName("Homes answered by the home service")
class RemoteHomesTest {

    private static final HomeServiceUnavailableException DOWN =
            new HomeServiceUnavailableException("home service did not answer", null);

    private final HomeServiceClient client = mock(HomeServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final RemoteHomes homes = new RemoteHomes(client, Duration.ofSeconds(30),
            Duration.ofHours(1), nanos::get);

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("membership comes from one cached request per user and home")
    void access() {
        given(client.access("hom0000001", "use0000002")).willReturn(new RemoteAccess(true, true, false));

        assertThat(homes.isMember("use0000002", "hom0000001")).isTrue();
        assertThat(homes.isMember("use0000002", "hom0000001")).isTrue();

        verify(client, times(1)).access("hom0000001", "use0000002");
    }

    @Test
    @DisplayName("a member removed loses access once the entry expires")
    void asksAgainAfterTtl() {
        given(client.access("hom0000001", "use0000002"))
                .willReturn(new RemoteAccess(true, true, false))
                .willReturn(new RemoteAccess(true, false, false));

        assertThat(homes.isMember("use0000002", "hom0000001")).isTrue();
        advance(Duration.ofSeconds(31));

        assertThat(homes.isMember("use0000002", "hom0000001")).isFalse();
    }

    @Test
    @DisplayName("what a user is in a home is never served stale")
    void accessNeverStale() {
        given(client.access("hom0000001", "use0000002"))
                .willReturn(new RemoteAccess(true, true, false))
                .willThrow(DOWN);

        assertThat(homes.isMember("use0000002", "hom0000001")).isTrue();
        advance(Duration.ofSeconds(31));

        assertThatThrownBy(() -> homes.isMember("use0000002", "hom0000001")).isSameAs(DOWN);
    }

    @Test
    @DisplayName("the limits of a home come from one cached request")
    void limits() {
        given(client.limits("hom0000001"))
                .willReturn(Optional.of(new RemoteLimits(6.0, 180.0, LimitPeriod.MONTHLY)));

        assertThat(homes.limits("hom0000001"))
                .contains(new RemoteLimits(6.0, 180.0, LimitPeriod.MONTHLY));
        homes.limits("hom0000001");

        verify(client, times(1)).limits("hom0000001");
    }

    @Test
    @DisplayName("serves the last limits while the service does not answer")
    void staleLimitsWhileDown() {
        given(client.limits("hom0000001"))
                .willReturn(Optional.of(new RemoteLimits(6.0, 180.0, LimitPeriod.DAILY)))
                .willThrow(DOWN);

        homes.limits("hom0000001");
        advance(Duration.ofMinutes(5));

        assertThat(homes.limits("hom0000001")).isPresent();
    }

    @Test
    @DisplayName("fails when the service does not answer and no limits were kept")
    void failsWithNothingKept() {
        given(client.limits("hom0000001")).willThrow(DOWN);

        assertThatThrownBy(() -> homes.limits("hom0000001")).isSameAs(DOWN);
    }

    @Test
    @DisplayName("asks nothing without a home or a user")
    void nothingWithoutIdentifiers() {
        assertThat(homes.limits(null)).isEmpty();
        assertThat(homes.isMember(null, "hom0000001")).isFalse();
        assertThat(homes.isMember("use0000001", null)).isFalse();

        verifyNoInteractions(client);
    }
}
