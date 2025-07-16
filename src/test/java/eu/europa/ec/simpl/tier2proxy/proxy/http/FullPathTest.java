package eu.europa.ec.simpl.tier2proxy.proxy.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FullPathTest {

    @Test
    void testFullPathWithHttpsProtocolShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("https://example.com:8080"));
        assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithHttpProtocolShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("http://example.com:8080"));
        assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithoutPortShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("http://example.com"));
        assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithoutProtocolShouldThrow() {
        var ex = Assertions.catchException(() -> new FullPath("example.com:8080"));
        assertThat(ex).isNotNull().isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void testFullPathWithFinalEmptyPathShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("http://example.com:8080/"));
        assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithMalformedInputShouldThrow() {
        var ex = Assertions.catchException(() -> new FullPath("ht://example.com:8080"));
        assertThat(ex).isNotNull().isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void testShouldResolveValidConnectAddr() {
        // given
        var connectAddr = "example.com:443";

        // when
        var result = FullPath.resolveAddrInConnect(connectAddr);

        // then
        assertThat(result.addr()).isEqualTo("example.com");
        assertThat(result.port()).isEqualTo(443);
    }

    @Test
    void testShouldResolveIPAddressInConnectAddr() {
        // given
        var connectAddr = "192.168.1.1:8080";

        // when
        var result = FullPath.resolveAddrInConnect(connectAddr);

        // then
        assertThat(result.addr()).isEqualTo("192.168.1.1");
        assertThat(result.port()).isEqualTo(8080);
    }

    @Test
    void testShouldThrowForInvalidConnectAddr() {
        // given
        var invalidAddr = "invalid-address";

        // then
        assertThatThrownBy(() -> FullPath.resolveAddrInConnect(invalidAddr))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal tunnel addr");
    }

    @Test
    void testShouldThrowIfPortIsMissing() {
        // given
        var missingPort = "example.com";

        // then
        assertThatThrownBy(() -> FullPath.resolveAddrInConnect(missingPort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal tunnel addr");
    }
}
