package eu.europa.ec.simpl.tier2proxy.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TLS {

    private static final TrustManagerFactory trustManagersFactory;

    static {
        /*try {
            trustManagersFactory = getSystemTrustManagers();
        } catch(NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(e);
        }*/
        trustManagersFactory = InsecureTrustManagerFactory.INSTANCE; // TODO remove this!
    }

    public static SslContext getClientSslContext() throws SSLException {
        log.debug("Creating client TLS context");
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
        buffer.skipBytes(5);
        // Skip the handshake header (4 bytes)
        buffer.skipBytes(4);
        // Skip the legacy version (2 bytes)
        buffer.skipBytes(2);
        // Skip the random (32 bytes)
        buffer.skipBytes(32);

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


    private static TrustManagerFactory getSystemTrustManagers() throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);

        return trustManagerFactory;
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
