package eu.europa.ec.simpl.tier2proxy.proxy;

import eu.europa.ec.simpl.client.util.DaggerCertificateRevocationFactory;
import eu.europa.ec.simpl.tier2proxy.authprovider.CredentialHolder;
import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public final class TLS {

    private static final CredentialHolder credentialHolder = CredentialHolder.getInstance();
    private static final TrustManagerFactory trustManagersFactory;
    public static final int RECORD_LAYER_BITS_TO_SKIP = 5;
    public static final int HANDSHAKE_HEADER_BITS_TO_SKIP = 4;
    public static final int LEGACY_VERSION_BITS_TO_SKIP = 2;
    public static final int RANDOM_BITS_TO_SKIP = 32;

    static {
        trustManagersFactory = getSystemTrustManagers();
    }

    public static SslContext getClientSslContext(ConnectionType connectionType) throws SSLException {
        return switch (connectionType) {
            case MTLS -> getClientMtlsContext();
            case TLS -> getClientSslContext();
            case HTTP -> null;
        };
    }

    @SneakyThrows
    private static SslContext getClientMtlsContext() {
        log.debug("Creating client mTLS context");
        var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(credentialHolder.getKeyStore(), null);

        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(credentialHolder.getKeyStore());
        var trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        var customTrustManager =
                new MTLSTrustManager(DaggerCertificateRevocationFactory.create().get(), trustManager);

        return SslContextBuilder.forClient()
                .keyManager(keyManagerFactory)
                .trustManager(customTrustManager)
                .sslProvider(sslProvider())
                .build();
    }

    private static SslContext getClientSslContext() throws SSLException {
        log.debug("Creating client TLS context with {} trust managers", trustManagersFactory.getTrustManagers().length);

        return SslContextBuilder.forClient()
                .trustManager(trustManagersFactory)
                .sslProvider(sslProvider())
                .build();
    }

    public static SslContext getServerSslContext(PrivateKey privateKey, X509Certificate certificate)
            throws SSLException {
        log.debug("Creating server TLS context for {}", certificate.getSubjectX500Principal());
        return SslContextBuilder.forServer(privateKey, certificate)
                .sslProvider(sslProvider())
                .build();
    }

    public static Optional<String> extractSNI(ByteBuf buffer) {
        // Skip the record layer (5 bytes)
        buffer.skipBytes(RECORD_LAYER_BITS_TO_SKIP);
        // Skip the handshake header (4 bytes)
        buffer.skipBytes(HANDSHAKE_HEADER_BITS_TO_SKIP);
        // Skip the legacy version (2 bytes)
        buffer.skipBytes(LEGACY_VERSION_BITS_TO_SKIP);
        // Skip the random (32 bytes)
        buffer.skipBytes(RANDOM_BITS_TO_SKIP);

        // Skip the session ID (dynamic length)
        int sessionIdLength = buffer.readUnsignedByte();
        buffer.skipBytes(sessionIdLength);

        // Skip cipher suites (dynamic length)
        int cipherSuitesLength = buffer.readUnsignedShort();
        buffer.skipBytes(cipherSuitesLength);

        // Skip compression methods (dynamic length)
        int compressionMethodsLength = buffer.readUnsignedByte();
        buffer.skipBytes(compressionMethodsLength);

        // Skip extensions length (2 bytes)
        int extensionsLength = buffer.readUnsignedShort();

        // Parse each extension
        int extensionsEndOffset = buffer.readerIndex() + extensionsLength;
        while (buffer.readerIndex() < extensionsEndOffset) {
            int extensionType = buffer.readUnsignedShort();
            int extensionLength = buffer.readUnsignedShort();

            // 0x00 is the SNI extension type
            if (extensionType == 0x00) {
                // Skip SNI list length
                buffer.readUnsignedShort();
                int sniType = buffer.readUnsignedByte();

                // Hostname type
                if (sniType == 0x00) {
                    int sniLength = buffer.readUnsignedShort();
                    return Optional.of(buffer.readCharSequence(sniLength, StandardCharsets.UTF_8)
                            .toString());
                }
            } else {
                // Skip this extension
                buffer.skipBytes(extensionLength);
            }
        }

        return Optional.empty();
    }

    @SneakyThrows
    private static TrustManagerFactory getSystemTrustManagers() {
        var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        factory.init((KeyStore) null);

        return factory;
    }

    private static SslProvider sslProvider() {
        if (OpenSsl.isAlpnSupported()) {
            log.debug("Using OpenSsl TLS provider");
            return SslProvider.OPENSSL;
        }

        log.debug("Using JDK TLS provider");
        return SslProvider.JDK;
    }
}
