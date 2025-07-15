package eu.europa.ec.simpl.tier2proxy.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificatesTest {

    @Mock
    private CertificateOptions certificateOptions;

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private CertificateFactory certificateFactory;

    @Mock
    private CertificateInfo caCert;

    @Mock
    private CertificateInfo certificateInfo;

    private CertificateAuthorityRepository mockRepo;

    @Test
    void shouldBuildCertificatesAndReturnCachedCert() throws Exception {
        var options = Configuration.getInstance().getCertificateServerOptions().certificateOptions();

        try (MockedConstruction<CertificateFactory> factoryConstruction =
                mockConstruction(CertificateFactory.class, (mock, context) -> {
                    given(mock.getCACertificate()).willReturn(caCert);
                    given(mock.getCertificate(caCert, "localhost")).willReturn(certificateInfo);
                })) {

            // when
            var certificates = new Certificates(options, mockRepo);
            var cert = certificates.certificateFor("localhost");

            // then
            assertThat(cert).isEqualTo(certificateInfo);
            assertThat(certificates.getCaCertificate()).isEqualTo(caCert);

            // Verifica caching
            CertificateInfo secondCall = certificates.certificateFor("localhost");
            assertThat(secondCall).isEqualTo(cert); // Same instance
        }
    }

    @Test
    void testToPemAndPrivateKeyFromPem() throws IOException {
        // Arrange
        var keyPair = TestUtils.generateKeyPair();
        var privateKey = keyPair.getPrivate();
        // Act
        var pemBytes = Certificates.toPem(privateKey);
        var privateKeyRestored = Certificates.privateKeyFromPem(pemBytes);
        // Assert
        assertNotNull(pemBytes);
        assertNotNull(privateKeyRestored);
        assertEquals(privateKey.getAlgorithm(), privateKeyRestored.getAlgorithm());
    }

    @Test
    void testToPemAndCertificateFromPem() throws Exception {
        // Arrange
        var keyPair = TestUtils.generateKeyPair();
        var cert = TestUtils.generateSelfSignedCertificate(keyPair);
        // Act
        var pemBytes = Certificates.toPem(cert);
        var certRestored = Certificates.certificateFromPem(pemBytes);
        // Assert
        assertNotNull(pemBytes);
        assertNotNull(certRestored);
        assertEquals(cert.getSubjectDN().toString(), certRestored.getSubjectDN().toString());
    }

    @Test
    void testPrivateKeyFromPemThrowsOnInvalid() {
        // Arrange
        var invalidPem = "invalid".getBytes();
        // Act & Assert
        assertThrows(IOException.class, () -> Certificates.privateKeyFromPem(invalidPem));
    }

    @Test
    void testCertificateFromPemThrowsOnInvalid() {
        // Arrange
        var invalidPem = "invalid".getBytes();
        // Act & Assert
        assertThrows(CertificateException.class, () -> Certificates.certificateFromPem(invalidPem));
    }
}

// Utility class for key/cert generation for tests
class TestUtils {
    static java.security.KeyPair generateKeyPair() {
        try {
            var keyGen = java.security.KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static X509Certificate generateSelfSignedCertificate(java.security.KeyPair keyPair) {
        try {
            // Generate a real self-signed certificate using BouncyCastle
            var now = java.time.Instant.now();
            var from = java.util.Date.from(now);
            var to = java.util.Date.from(now.plus(java.time.Duration.ofDays(365)));
            var dnName = new org.bouncycastle.asn1.x500.X500Name("CN=Test");
            var serial = new java.math.BigInteger(64, new java.security.SecureRandom());

            var contentSigner = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                    .build(keyPair.getPrivate());

            var certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                    dnName, serial, from, to, dnName, keyPair.getPublic());

            var certHolder = certBuilder.build(contentSigner);
            return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                    .setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
                    .getCertificate(certHolder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
