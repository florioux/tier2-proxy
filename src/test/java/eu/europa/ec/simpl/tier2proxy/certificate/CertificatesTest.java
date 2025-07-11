package eu.europa.ec.simpl.tier2proxy.certificate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificatesTest {

    @Mock
    CertificateOptions certificateOptions;

    @Mock
    CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    CertificateFactory certificateFactory;

    @Mock
    CertificateInfo certificateInfo;

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
