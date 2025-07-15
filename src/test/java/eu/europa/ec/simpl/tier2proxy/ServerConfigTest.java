package eu.europa.ec.simpl.tier2proxy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServerConfigTest {

    @Test
    void testCreateServerConfigWithCorrectValues() {
        var config = new ServerConfig(true, "127.0.0.1", 8080);

        assertThat(config.enabled()).as("server should be enabled").isTrue();

        assertThat(config.bindAddr()).as("address should be set correctly").isEqualTo("127.0.0.1");

        assertThat(config.bindPort()).as("port should be set correctly").isEqualTo(8080);
    }

    @Test
    void testReturnCorrectToStringFormat() {
        var config = new ServerConfig(false, "0.0.0.0", 443);

        assertThat(config).as("string representation").hasToString("0.0.0.0:443");
    }
}
