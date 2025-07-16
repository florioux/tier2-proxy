package eu.europa.ec.simpl.tier2proxy.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PemUtilTest {

    private static X509Certificate generateSelfSignedCertificate() throws Exception {
        // Generate a key pair
        var keyPairGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        var keyPair = keyPairGen.generateKeyPair();

        // Use BouncyCastle X500Name and content signer
        var dnName = new X500Name("CN=Test");
        var now = Instant.now();
        var from = Date.from(now);
        var to = Date.from(now.plus(Duration.ofDays(1)));
        var serial = new BigInteger(64, new java.security.SecureRandom());
        var certBuilder = new X509v3CertificateBuilder(
                dnName,
                serial,
                from,
                to,
                dnName,
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        var contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        var holder = certBuilder.build(contentSigner);
        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(holder);
    }

    @Test
    void testWritePemReturnsBytes() throws Exception {
        var cert = generateSelfSignedCertificate();
        var result = PemUtil.writePem(List.of(cert));
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void testWritePemWithOutputStream() throws Exception {
        var cert = generateSelfSignedCertificate();
        var outputStream = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> PemUtil.writePem(List.of(cert), outputStream));
    }

    @Test
    void testLoadPemObjectsReturnsList() throws Exception {
        // Create a dummy PEM
        var pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getEncoder().encodeToString("dummy-cert".getBytes())
                + "\n-----END CERTIFICATE-----\n";
        var inputStream = new ByteArrayInputStream(pem.getBytes());
        var result = PemUtil.loadPemObjects(inputStream);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).readAllBytes().length > 0);
    }

    @Test
    void testLoadPrivateKeyThrowsOrReturns() {
        // Create a dummy PKCS8 PEM, which will fail to parse as a real key
        var pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString("dummy-key".getBytes())
                + "\n-----END PRIVATE KEY-----\n";
        var inputStream = new ByteArrayInputStream(pem.getBytes());
        // Should throw because dummy key is not valid, but method is annotated with SneakyThrows
        assertThrows(Exception.class, () -> PemUtil.loadPrivateKey(inputStream, "RSA"));
    }
}
