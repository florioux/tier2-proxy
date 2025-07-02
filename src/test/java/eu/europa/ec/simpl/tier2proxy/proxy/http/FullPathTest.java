package eu.europa.ec.simpl.tier2proxy.proxy.http;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FullPathTest {

    @Test
    void testFullPathWithHttpsProtocolShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("https://example.com:8080"));
        Assertions.assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithHttpProtocolShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("http://example.com:8080"));
        Assertions.assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithoutPortShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("http://example.com"));
        Assertions.assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithoutProtocolShouldThrow() {
        var ex = Assertions.catchException(() -> new FullPath("example.com:8080"));
        Assertions.assertThat(ex).isNotNull().isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void testFullPathWithFinalEmptyPathShouldSucceed() {
        var ex = Assertions.catchException(() -> new FullPath("http://example.com:8080/"));
        Assertions.assertThat(ex).isNull();
    }

    @Test
    void testFullPathWithMalformedInputShouldThrow() {
        var ex = Assertions.catchException(() -> new FullPath("ht://example.com:8080"));
        Assertions.assertThat(ex).isNotNull().isExactlyInstanceOf(IllegalStateException.class);
    }
}
