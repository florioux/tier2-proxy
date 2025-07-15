package eu.europa.ec.simpl.tier2proxy.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.certificate.http.CertificateServerOptions;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialUtilTest {

    @Mock
    private Configuration configuration;

    @Mock
    private CertificateServerOptions certificateServerOptions;

    @Mock
    private X509Certificate certificate;

    @Mock
    private PrivateKey privateKey;

    @Mock
    private KeyStore keyStore;

    @Mock
    private ECPublicKey ecPublicKey;

    @Mock
    private ECPrivateKey ecPrivateKey;

    @Mock
    private X500Principal x500Principal;

    @BeforeEach
    void setUp() {
        try (MockedStatic<Configuration> configurationMockedStatic = mockStatic(Configuration.class)) {
            configurationMockedStatic.when(Configuration::getInstance).thenReturn(configuration);
        }
    }

    @Test
    void testLoadCredentialShouldCreateKeyStoreWhenValidInputs() throws Exception {
        // Given
        var inputStream = new ByteArrayInputStream("test certificate".getBytes(StandardCharsets.UTF_8));
        var certificates = List.of(certificate);

        when(privateKey.getFormat()).thenReturn("PKCS#8");
        when(privateKey.getEncoded()).thenReturn(new byte[] {1, 2, 3, 4, 5}); // Non-null encoded bytes

        try (MockedStatic<PemUtil> pemUtilMockedStatic = mockStatic(PemUtil.class)) {
            var mockInputStream = mock(InputStream.class);
            pemUtilMockedStatic
                    .when(() -> PemUtil.loadPemObjects(any(InputStream.class)))
                    .thenReturn(List.of(mockInputStream));

            try (MockedStatic<CredentialUtil> credentialUtilMockedStatic =
                    mockStatic(CredentialUtil.class, invocation -> {
                        if (invocation.getMethod().getName().equals("generateCertificate")) {
                            return certificate;
                        } else if (invocation.getMethod().getName().equals("verifyPrivateKeyMatchesCertificateChain")) {
                            return null; // void method
                        } else if (invocation.getMethod().getName().equals("parseCertificates")) {
                            return certificates;
                        }
                        return invocation.callRealMethod();
                    })) {
                // When
                var result = CredentialUtil.loadCredential(inputStream, privateKey);

                // Then
                assertThat(result).isNotNull();
            }
        }
    }

    @Test
    void testBuildTrustStoreShouldCreateTrustStoreWhenValidKeyStore() throws Exception {
        // Given
        var alias = "testAlias";
        var certificateChain = new X509Certificate[] {certificate, certificate};

        when(keyStore.aliases()).thenReturn(Collections.enumeration(List.of(alias)));
        when(keyStore.getCertificateChain(alias)).thenReturn(certificateChain);
        when(certificate.getSubjectX500Principal()).thenReturn(x500Principal);
        when(x500Principal.getName()).thenReturn("CN=Test Certificate");

        // When
        var result = CredentialUtil.buildTrustStore(keyStore);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void testGenerateCertificateShouldReturnCertificateWhenValidInput() throws Exception {
        // Given
        var inputStream = new ByteArrayInputStream("test certificate".getBytes(StandardCharsets.UTF_8));
        var certificateFactory = mock(java.security.cert.CertificateFactory.class);

        try (MockedStatic<java.security.cert.CertificateFactory> certificateFactoryMockedStatic =
                mockStatic(java.security.cert.CertificateFactory.class)) {
            certificateFactoryMockedStatic
                    .when(() -> java.security.cert.CertificateFactory.getInstance("X.509"))
                    .thenReturn(certificateFactory);
            when(certificateFactory.generateCertificate(any(InputStream.class))).thenReturn(certificate);

            // When
            var result = CredentialUtil.generateCertificate(inputStream);

            // Then
            assertThat(result).isEqualTo(certificate);
        }
    }

    @Test
    void testExtractCertificateFromKeystoreShouldReturnCertificateWhenValidKeyStore() throws Exception {
        // Given
        var alias = "testAlias";
        var certificateChain = new X509Certificate[] {certificate};

        when(keyStore.aliases()).thenReturn(Collections.enumeration(List.of(alias)));
        when(keyStore.getCertificateChain(alias)).thenReturn(certificateChain);

        // When
        var result = CredentialUtil.extractCertificateFromKeystore(keyStore);

        // Then
        assertThat(result).isEqualTo(certificate);
    }

    @Test
    void testExtractCertificateFromKeystoreShouldThrowExceptionWhenKeyStoreException() throws Exception {
        // Given
        when(keyStore.aliases()).thenThrow(new KeyStoreException("Test exception"));

        // When/Then
        assertThatThrownBy(() -> CredentialUtil.extractCertificateFromKeystore(keyStore))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(KeyStoreException.class);
    }

    @Test
    void testExtractPublicKeyFromKeystoreShouldReturnPublicKeyWhenValidKeyStore() throws Exception {
        // Given
        var alias = "testAlias";
        var certificateChain = new X509Certificate[] {certificate};

        when(keyStore.aliases()).thenReturn(Collections.enumeration(List.of(alias)));
        when(keyStore.getCertificateChain(alias)).thenReturn(certificateChain);
        when(certificate.getPublicKey()).thenReturn(ecPublicKey);

        // When
        var result = CredentialUtil.extractPublicKeyFromKeystore(keyStore);

        // Then
        assertThat(result).isEqualTo(ecPublicKey);
    }

    @Test
    void testExtractPrivateKeyFromKeystoreShouldReturnPrivateKeyWhenValidKeyStore() throws Exception {
        // Given
        var alias = "testAlias";
        var password = "password";

        when(keyStore.aliases()).thenReturn(Collections.enumeration(List.of(alias)));
        when(keyStore.getKey(alias, password.toCharArray())).thenReturn(ecPrivateKey);

        // When
        var result = CredentialUtil.extractPrivateKeyFromKeystore(keyStore, password);

        // Then
        assertThat(result).isEqualTo(ecPrivateKey);
    }

    @Test
    void testExtractPrivateKeyFromKeystoreShouldThrowExceptionWhenKeyStoreException() throws Exception {
        // Given
        var password = "password";
        when(keyStore.aliases()).thenThrow(new KeyStoreException("Test exception"));

        // When/Then
        assertThatThrownBy(() -> CredentialUtil.extractPrivateKeyFromKeystore(keyStore, password))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(KeyStoreException.class);
    }

    @Test
    void testGetIdentifierFromSubjectShouldThrowExceptionWhenCertificateEncodingException() throws Exception {
        // Given
        var identifier = BCStyle.CN;

        try (MockedStatic<JcaX509CertificateHolder> holderMockedStatic = mockStatic(JcaX509CertificateHolder.class)) {
            holderMockedStatic
                    .when(() -> new JcaX509CertificateHolder(certificate))
                    .thenThrow(new CertificateEncodingException("Test exception"));

            // When/Then
            assertThatThrownBy(() -> CredentialUtil.getIdentifierFromSubject(certificate, identifier))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(CertificateEncodingException.class);
        }
    }

    @Test
    void testGetIdentifierFromSubjectShouldReturnIdentifierWhenValidX500Name() {
        // Given
        var identifier = BCStyle.CN;
        var expectedValue = "test.example.com";
        var x500Name = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, expectedValue)
                .build();

        // When
        var result = CredentialUtil.getIdentifierFromSubject(x500Name, identifier);

        // Then
        assertThat(result).isEqualTo(expectedValue);
    }
}
