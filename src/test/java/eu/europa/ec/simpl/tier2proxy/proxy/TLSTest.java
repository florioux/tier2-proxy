package eu.europa.ec.simpl.tier2proxy.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.client.util.CertificateRevocation;
import eu.europa.ec.simpl.tier2proxy.authprovider.CredentialHolder;
import eu.europa.ec.simpl.tier2proxy.enums.ConnectionType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TLSTest {

    @Mock
    private CredentialHolder credentialHolder;

    @Mock
    private KeyStore keyStore;

    @Mock
    private PrivateKey privateKey;

    @Mock
    private X509Certificate certificate;

    @Mock
    private SslContextBuilder sslContextBuilder;

    @Mock
    private SslContext sslContext;

    @Mock
    private KeyManagerFactory keyManagerFactory;

    @Mock
    private TrustManagerFactory trustManagerFactory;

    @Mock
    private X509TrustManager trustManager;

    @Mock
    private CertificateRevocation certificateRevocation;

    @BeforeEach
    void setUp() throws Exception {
        try (MockedStatic<CredentialHolder> credentialHolderMockedStatic = mockStatic(CredentialHolder.class)) {
            credentialHolderMockedStatic.when(CredentialHolder::getInstance).thenReturn(credentialHolder);
        }
    }

    @Test
    void testGetClientSslContextWithTls() throws SSLException {
        // Given
        try (MockedStatic<TrustManagerFactory> trustManagerFactoryMockedStatic = mockStatic(TrustManagerFactory.class);
                MockedStatic<SslContextBuilder> sslContextBuilderMockedStatic = mockStatic(SslContextBuilder.class);
                MockedStatic<OpenSsl> openSslMockedStatic = mockStatic(OpenSsl.class)) {

            trustManagerFactoryMockedStatic
                    .when(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()))
                    .thenReturn(trustManagerFactory);

            sslContextBuilderMockedStatic.when(SslContextBuilder::forClient).thenReturn(sslContextBuilder);
            when(sslContextBuilder.trustManager(any(TrustManagerFactory.class))).thenReturn(sslContextBuilder);
            when(sslContextBuilder.sslProvider(any(SslProvider.class))).thenReturn(sslContextBuilder);
            when(sslContextBuilder.build()).thenReturn(sslContext);

            openSslMockedStatic.when(OpenSsl::isAlpnSupported).thenReturn(true);

            // When
            var result = TLS.getClientSslContext(ConnectionType.TLS);

            // Then
            assertThat(result).isEqualTo(sslContext);
        }
    }

    @Test
    void testGetClientSslContextWithHttp() throws SSLException {
        // When
        var result = TLS.getClientSslContext(ConnectionType.HTTP);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testGetServerSslContext() throws SSLException {
        // Given
        try (MockedStatic<SslContextBuilder> sslContextBuilderMockedStatic = mockStatic(SslContextBuilder.class);
                MockedStatic<OpenSsl> openSslMockedStatic = mockStatic(OpenSsl.class)) {

            sslContextBuilderMockedStatic
                    .when(() -> SslContextBuilder.forServer(privateKey, certificate))
                    .thenReturn(sslContextBuilder);
            when(sslContextBuilder.sslProvider(any(SslProvider.class))).thenReturn(sslContextBuilder);
            when(sslContextBuilder.build()).thenReturn(sslContext);

            openSslMockedStatic.when(OpenSsl::isAlpnSupported).thenReturn(true);

            // When
            var result = TLS.getServerSslContext(privateKey, certificate);

            // Then
            assertThat(result).isEqualTo(sslContext);
        }
    }

    @Test
    void testExtractSniWhenPresent() {
        // Given
        var hostname = "test.example.com";
        var byteBuf = createClientHelloWithSNI(hostname);

        // When
        var result = TLS.extractSNI(byteBuf);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(hostname);
    }

    @Test
    void testExtractSniWhenNotPresent() {
        // Given
        var byteBuf = createClientHelloWithoutSNI();

        // When
        var result = TLS.extractSNI(byteBuf);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testSslProviderWithOpenSslSupport() throws SSLException {
        // Given
        try (MockedStatic<OpenSsl> openSslMockedStatic = mockStatic(OpenSsl.class);
                MockedStatic<SslContextBuilder> sslContextBuilderMockedStatic = mockStatic(SslContextBuilder.class);
                MockedStatic<TrustManagerFactory> trustManagerFactoryMockedStatic =
                        mockStatic(TrustManagerFactory.class)) {

            openSslMockedStatic.when(OpenSsl::isAlpnSupported).thenReturn(true);

            trustManagerFactoryMockedStatic
                    .when(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()))
                    .thenReturn(trustManagerFactory);

            sslContextBuilderMockedStatic.when(SslContextBuilder::forClient).thenReturn(sslContextBuilder);
            when(sslContextBuilder.trustManager(any(TrustManagerFactory.class))).thenReturn(sslContextBuilder);
            when(sslContextBuilder.sslProvider(SslProvider.OPENSSL)).thenReturn(sslContextBuilder);
            when(sslContextBuilder.build()).thenReturn(sslContext);

            // When
            var result = TLS.getClientSslContext(ConnectionType.TLS);

            // Then
            assertThat(result).isEqualTo(sslContext);
        }
    }

    @Test
    void testSslProviderWithoutOpenSslSupport() throws SSLException {
        // Given
        try (MockedStatic<OpenSsl> openSslMockedStatic = mockStatic(OpenSsl.class);
                MockedStatic<SslContextBuilder> sslContextBuilderMockedStatic = mockStatic(SslContextBuilder.class);
                MockedStatic<TrustManagerFactory> trustManagerFactoryMockedStatic =
                        mockStatic(TrustManagerFactory.class)) {

            openSslMockedStatic.when(OpenSsl::isAlpnSupported).thenReturn(false);

            trustManagerFactoryMockedStatic
                    .when(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()))
                    .thenReturn(trustManagerFactory);

            sslContextBuilderMockedStatic.when(SslContextBuilder::forClient).thenReturn(sslContextBuilder);
            when(sslContextBuilder.trustManager(any(TrustManagerFactory.class))).thenReturn(sslContextBuilder);
            when(sslContextBuilder.sslProvider(SslProvider.JDK)).thenReturn(sslContextBuilder);
            when(sslContextBuilder.build()).thenReturn(sslContext);

            // When
            var result = TLS.getClientSslContext(ConnectionType.TLS);

            // Then
            assertThat(result).isEqualTo(sslContext);
        }
    }

    // Helper methods
    private ByteBuf createClientHelloWithSNI(String hostname) {
        // This is a simplified mock of a TLS ClientHello with SNI
        ByteBuf buf = Unpooled.buffer();

        // Record layer (5 bytes)
        buf.writeBytes(new byte[TLS.RECORD_LAYER_BITS_TO_SKIP]);

        // Handshake header (4 bytes)
        buf.writeBytes(new byte[TLS.HANDSHAKE_HEADER_BITS_TO_SKIP]);

        // Legacy version (2 bytes)
        buf.writeBytes(new byte[TLS.LEGACY_VERSION_BITS_TO_SKIP]);

        // Random (32 bytes)
        buf.writeBytes(new byte[TLS.RANDOM_BITS_TO_SKIP]);

        // Session ID length (1 byte) and session ID (0 bytes in this mock)
        buf.writeByte(0);

        // Cipher suites length (2 bytes) and cipher suites (2 bytes in this mock)
        buf.writeShort(2);
        buf.writeShort(0x1301); // TLS_AES_128_GCM_SHA256

        // Compression methods length (1 byte) and compression methods (1 byte in this mock)
        buf.writeByte(1);
        buf.writeByte(0); // No compression

        // Extensions length (2 bytes)
        int extensionsLengthIndex = buf.writerIndex();
        buf.writeShort(0); // Placeholder, will be updated

        // SNI extension
        buf.writeShort(0x0000); // Extension type: server_name

        // SNI extension length (2 bytes)
        int sniExtensionLengthIndex = buf.writerIndex();
        buf.writeShort(0); // Placeholder, will be updated

        // SNI list length (2 bytes)
        int sniListLengthIndex = buf.writerIndex();
        buf.writeShort(0); // Placeholder, will be updated

        // SNI entry type (1 byte)
        buf.writeByte(0x00); // Host name

        // SNI hostname length (2 bytes) and hostname
        byte[] hostnameBytes = hostname.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(hostnameBytes.length);
        buf.writeBytes(hostnameBytes);

        // Update SNI list length
        int sniListLength = buf.writerIndex() - sniListLengthIndex - 2;
        buf.setShort(sniListLengthIndex, sniListLength);

        // Update SNI extension length
        int sniExtensionLength = buf.writerIndex() - sniExtensionLengthIndex - 2;
        buf.setShort(sniExtensionLengthIndex, sniExtensionLength);

        // Update extensions length
        int extensionsLength = buf.writerIndex() - extensionsLengthIndex - 2;
        buf.setShort(extensionsLengthIndex, extensionsLength);

        // Reset reader index to beginning
        buf.readerIndex(0);

        return buf;
    }

    private ByteBuf createClientHelloWithoutSNI() {
        // This is a simplified mock of a TLS ClientHello without SNI
        ByteBuf buf = Unpooled.buffer();

        // Record layer (5 bytes)
        buf.writeBytes(new byte[TLS.RECORD_LAYER_BITS_TO_SKIP]);

        // Handshake header (4 bytes)
        buf.writeBytes(new byte[TLS.HANDSHAKE_HEADER_BITS_TO_SKIP]);

        // Legacy version (2 bytes)
        buf.writeBytes(new byte[TLS.LEGACY_VERSION_BITS_TO_SKIP]);

        // Random (32 bytes)
        buf.writeBytes(new byte[TLS.RANDOM_BITS_TO_SKIP]);

        // Session ID length (1 byte) and session ID (0 bytes in this mock)
        buf.writeByte(0);

        // Cipher suites length (2 bytes) and cipher suites (2 bytes in this mock)
        buf.writeShort(2);
        buf.writeShort(0x1301); // TLS_AES_128_GCM_SHA256

        // Compression methods length (1 byte) and compression methods (1 byte in this mock)
        buf.writeByte(1);
        buf.writeByte(0); // No compression

        // Extensions length (2 bytes)
        buf.writeShort(0); // No extensions

        // Reset reader index to beginning
        buf.readerIndex(0);

        return buf;
    }
}
