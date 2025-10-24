package eu.europa.ec.simpl.tier2proxy.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.certificate.http.CertificateServerOptions;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.List;
import javax.security.auth.x500.X500Principal;
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
    void testLoadCredentialShouldCreateKeyStoreWhenValidInputs() {
        // Given
        var certificates = List.of(certificate);

        when(privateKey.getFormat()).thenReturn("PKCS#8");
        when(privateKey.getEncoded()).thenReturn(new byte[] {1, 2, 3, 4, 5}); // Non-null encoded bytes

        try (MockedStatic<CredentialUtil> credentialUtilMockedStatic = mockStatic(CredentialUtil.class, invocation -> {
            if (invocation.getMethod().getName().equals("verifyPrivateKeyMatchesCertificateChain")) {
                return null; // void method
            } else {
                return invocation.callRealMethod();
            }
        })) {
            // When
            var result = CredentialUtil.loadCredential(certificates, privateKey);

            // Then
            assertThat(result).isNotNull();
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
}
