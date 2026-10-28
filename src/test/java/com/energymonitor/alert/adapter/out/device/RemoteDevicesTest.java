package com.energymonitor.alert.adapter.out.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient.RemoteDevice;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the cached devices ask the device service, when they ask again, and what they serve while
 * the service does not answer. The clock of the caches is moved by hand.
 */
@DisplayName("Devices answered by the device service")
class RemoteDevicesTest {

    private static final Instant LINKED = Instant.parse("2026-10-01T10:00:00Z");
    private static final RemoteDevice FRIDGE = new RemoteDevice("DEV0000001", "Nevera", "appl000001",
            "Cocina", null, LINKED, "EM204", "hom0000001");

    private final DeviceServiceClient client = mock(DeviceServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final RemoteDevices devices = new RemoteDevices(client, Duration.ofSeconds(30),
            Duration.ofHours(1), nanos::get);

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("a device and its home come from one cached request")
    void deviceAndHomeFromOneRequest() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));

        assertThat(devices.device("DEV0000001")).contains(FRIDGE);
        assertThat(devices.device("DEV0000001").map(RemoteDevice::homeId)).contains("hom0000001");

        verify(client, times(1)).device("DEV0000001");
    }

    @Test
    @DisplayName("an unknown device is cached too, so its readings do not ask every time")
    void unknownDeviceCached() {
        given(client.device("DEV0000099")).willReturn(Optional.empty());

        assertThat(devices.device("DEV0000099")).isEmpty();
        assertThat(devices.device("DEV0000099")).isEmpty();

        verify(client, times(1)).device("DEV0000099");
    }

    @Test
    @DisplayName("asks again once the entry expires")
    void asksAgainAfterTtl() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));

        devices.device("DEV0000001");
        advance(Duration.ofSeconds(31));
        devices.device("DEV0000001");

        verify(client, times(2)).device("DEV0000001");
    }

    @Test
    @DisplayName("serves the last answer while the service does not answer")
    void servesStaleWhenUnavailable() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE))
                .willThrow(new DeviceServiceUnavailableException("down", null));
        given(client.devicesOfHome("hom0000001")).willReturn(List.of(FRIDGE))
                .willThrow(new DeviceServiceUnavailableException("down", null));

        devices.device("DEV0000001");
        devices.devicesOfHome("hom0000001");
        advance(Duration.ofMinutes(10));

        assertThat(devices.device("DEV0000001")).contains(FRIDGE);
        assertThat(devices.devicesOfHome("hom0000001")).containsExactly(FRIDGE);
    }

    @Test
    @DisplayName("with nothing kept, an unavailable service fails the call")
    void failsWithNothingKept() {
        given(client.device("DEV0000001")).willThrow(new DeviceServiceUnavailableException("down", null));

        assertThatThrownBy(() -> devices.device("DEV0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the last answer is not kept forever")
    void staleExpires() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE))
                .willThrow(new DeviceServiceUnavailableException("down", null));

        devices.device("DEV0000001");
        advance(Duration.ofHours(2));

        assertThatThrownBy(() -> devices.device("DEV0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the devices of a home are cached by home")
    void devicesOfHomeCached() {
        given(client.devicesOfHome("hom0000001")).willReturn(List.of(FRIDGE));

        assertThat(devices.devicesOfHome("hom0000001")).extracting(RemoteDevice::name)
                .containsExactly("Nevera");
        devices.devicesOfHome("hom0000001");
        assertThat(devices.devicesOfHome(null)).isEmpty();

        verify(client, times(1)).devicesOfHome("hom0000001");
    }

    @Test
    @DisplayName("forgetting a device and its home asks the service again at once")
    void forget() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));
        given(client.devicesOfHome("hom0000001")).willReturn(List.of(FRIDGE));
        devices.device("DEV0000001");
        devices.devicesOfHome("hom0000001");

        devices.forget("DEV0000001", "hom0000001");
        devices.device("DEV0000001");
        devices.devicesOfHome("hom0000001");

        verify(client, times(2)).device("DEV0000001");
        verify(client, times(2)).devicesOfHome("hom0000001");
    }

    @Test
    @DisplayName("no device asked is no request")
    void nothingAsked() {
        assertThat(devices.device(null)).isEmpty();
        verifyNoInteractions(client);
    }
}
