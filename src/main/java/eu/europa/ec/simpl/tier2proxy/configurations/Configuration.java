package eu.europa.ec.simpl.tier2proxy.configurations;

import eu.europa.ec.simpl.tier2proxy.ProxyOptions;
import eu.europa.ec.simpl.tier2proxy.ServerConfig;
import eu.europa.ec.simpl.tier2proxy.TeardownPolicy;
import eu.europa.ec.simpl.tier2proxy.certificate.CaEndpoint;
import eu.europa.ec.simpl.tier2proxy.certificate.CertificateOptions;
import eu.europa.ec.simpl.tier2proxy.certificate.http.CertificateServerOptions;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpProtocolServerOptions;
import eu.europa.ec.simpl.tier2proxy.proxy.socks.SocksProtocolServerOptions;
import eu.europa.ec.simpl.tier2proxy.util.PropertyLoader;
import io.netty.handler.codec.http.HttpMethod;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import org.bouncycastle.asn1.x500.X500Name;

@Getter
public final class Configuration {
    private static final String ALL_IP = "0.0.0.0";
    private static final String AGGREGATOR_MAX_LENGTH = "65536";
    private static final int KEY_VALUE_DN_ARRAY_LENGTH = 2;

    @Getter(value = AccessLevel.NONE)
    private static final Properties properties;

    static {
        properties = PropertyLoader.loadPropertiesFromClasspath("application.properties");
        var external = PropertyLoader.loadPropertiesFromFile("./config/application.properties");
        properties.putAll(external);
    }

    private final ProxyOptions proxyOptions;
    private final TeardownPolicy teardownPolicy;
    private final SocksProtocolServerOptions socksProtocolServerOptions;
    private final HttpProtocolServerOptions httpProtocolServerOptions;
    private final CertificateServerOptions certificateServerOptions;
    private final SimplProperties simplProperties;

    private Configuration() {
        super();
        this.proxyOptions = loadProxyOptions();
        this.teardownPolicy = loadTeardownPolicy();
        this.socksProtocolServerOptions = loadSocksProtocolServerOptions();
        this.httpProtocolServerOptions = loadHttpProtocolServerOptions();
        this.certificateServerOptions = loadCertificateServerOptions();
        this.simplProperties = loadSimplProperties();
    }

    private static SimplProperties loadSimplProperties() {
        return new SimplProperties(new SimplProperties.AuthenticationProviderProperties(
                properties.getProperty("simpl.authentication-provider.baseurl", "http://localhost:8085"),
                properties.getProperty(
                        "simpl.authentication-provider.get-credentials-url", "http://localhost:8085/v1/credentials"),
                properties.getProperty(
                        "simpl.authentication-provider.get-keypair-url", "http://localhost:8085/v1/keypairs"),
                properties.getProperty(
                        "simpl.authentication-provider.get-ephemeral-proof-url",
                        "http://localhost:8085/v1/agent/ephemeralProof")));
    }

    private static ProxyOptions loadProxyOptions() {
        return new ProxyOptions(Integer.parseInt(properties.getProperty("proxy-options.thread-number", "1")));
    }

    private static TeardownPolicy loadTeardownPolicy() {
        return new TeardownPolicy(
                Integer.parseInt(properties.getProperty("teardown-policy.quiet-period", "2")),
                Integer.parseInt(properties.getProperty("teardown-policy.timeout", "10")),
                TimeUnit.valueOf((properties.getProperty("teardown-policy.time-unit", "SECONDS"))
                        .toUpperCase(Locale.getDefault())));
    }

    private static SocksProtocolServerOptions loadSocksProtocolServerOptions() {
        return new SocksProtocolServerOptions(
                new ServerConfig(
                        Boolean.parseBoolean(properties.getProperty("proxy.socks.server.enabled", "true")),
                        properties.getProperty("proxy.socks.server.address", ALL_IP),
                        Integer.parseInt(properties.getProperty("proxy.socks.server.port", "3002"))),
                Integer.parseInt(properties.getProperty(
                        "proxy.socks.http-object-aggregator-max-content-length", AGGREGATOR_MAX_LENGTH)));
    }

    private static HttpProtocolServerOptions loadHttpProtocolServerOptions() {
        return new HttpProtocolServerOptions(
                new ServerConfig(
                        Boolean.parseBoolean(properties.getProperty("proxy.http.server.enabled", "true")),
                        properties.getProperty("proxy.http.server.address", ALL_IP),
                        Integer.parseInt(properties.getProperty("proxy.http.server.port", "3001"))),
                Integer.parseInt(properties.getProperty(
                        "proxy.http.http-object-aggregator-max-content-length", AGGREGATOR_MAX_LENGTH)));
    }

    private static CertificateServerOptions loadCertificateServerOptions() {
        return new CertificateServerOptions(
                new ServerConfig(
                        Boolean.parseBoolean(properties.getProperty("proxy.certificate.server.enabled", "true")),
                        properties.getProperty("proxy.certificate.server.address", ALL_IP),
                        Integer.parseInt(properties.getProperty("proxy.certificate.server.port", "3000"))),
                Integer.parseInt(properties.getProperty(
                        "proxy.certificate.http-object-aggregator-max-content-length", AGGREGATOR_MAX_LENGTH)),
                HttpMethod.valueOf(properties
                        .getProperty("proxy.certificate.ca.endpoint.method", HttpMethod.GET.name())
                        .toUpperCase(Locale.getDefault())),
                loadCertificateOptions());
    }

    private static CertificateOptions loadCertificateOptions() {
        var location = properties.getProperty("proxy.certificate.ca.location", "/tmp/certs");
        var dn = parseDistinguishedName();
        var privateKey = loadPrivateKeyConfig();
        var signatureAlgorithm = properties.getProperty("proxy.certificate.ca.signature-algorithm", "SHA256withECDSA");
        var cache = loadCache();
        var validity = loadValidity();

        return new CertificateOptions(loadEndpoint(), location, dn, privateKey, signatureAlgorithm, cache, validity);
    }

    private static CaEndpoint loadEndpoint() {
        var method = properties
                .getProperty("proxy.certificate.ca.endpoint.method", "GET")
                .toUpperCase(Locale.getDefault());
        var path = properties.getProperty("proxy.certificate.ca.endpoint.path", "/cert");
        return new CaEndpoint(path, HttpMethod.valueOf(method));
    }

    private static X500Name parseDistinguishedName() {
        var x500String = properties.getProperty(
                "proxy.certificate.ca.x500-name", "C=EU, ST=IT, L=Rome, O=Mitm Proxy, OU=Mitm Proxy, CN=Mitm Proxy CA");
        return new X500Name(x500String);
    }

    private static CertificateOptions.PrivateKey loadPrivateKeyConfig() {
        var algorithm = properties.getProperty("proxy.certificate.ca.private-key.algorithm", "ECDSA");
        var curve = properties.getProperty("proxy.certificate.ca.private-key.ec-curve", "secp521r1");
        return new CertificateOptions.PrivateKey(algorithm, new ECGenParameterSpec(curve), new SecureRandom());
    }

    private static CertificateOptions.CertificateCache loadCache() {
        var size = Integer.parseInt(properties.getProperty("proxy.certificate.ca.cache.size", "2000"));
        var expiration = Integer.parseInt(properties.getProperty("proxy.certificate.ca.cache.expiration", "1"));
        var timeUnit = TimeUnit.valueOf(properties
                .getProperty("proxy.certificate.ca.cache.time-unit", "DAYS")
                .toUpperCase(Locale.getDefault()));
        return new CertificateOptions.CertificateCache(size, expiration, timeUnit);
    }

    private static CertificateOptions.CertificateValidity loadValidity() {
        var amount = Integer.parseInt(properties.getProperty("proxy.certificate.ca.validity.amount", "6"));
        var buffer = Integer.parseInt(properties.getProperty("proxy.certificate.ca.validity.buffer", "1000"));
        var unit = ChronoUnit.valueOf(properties
                .getProperty("proxy.certificate.ca.validity.unit", "MONTHS")
                .toUpperCase(Locale.getDefault()));
        var maxUnit = ChronoUnit.valueOf(properties
                .getProperty("proxy.certificate.ca.validity.max-unit", "YEARS")
                .toUpperCase(Locale.getDefault()));
        return new CertificateOptions.CertificateValidity(amount, buffer, unit, maxUnit);
    }

    public static Configuration getInstance() {
        return ConfigurationInstanceHolder.INSTANCE;
    }

    private static class ConfigurationInstanceHolder {
        private static final Configuration INSTANCE = new Configuration();
    }
}
