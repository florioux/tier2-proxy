package eu.europa.ec.simpl.tier2proxy.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificateFactoryTest {

    private static final String TEST_HOST = "test.example.com";
    private static final String CA_KEY = "_ca";
    private static final String SIGNATURE_ALGO = "SHA256withRSA";

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private X509Certificate certificate;

    private CertificateFactory certificateFactory;
    private CertificateOptions options;

    @BeforeAll
    static void bouncyCastle() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        options = Configuration.getInstance().getCertificateServerOptions().certificateOptions();
        certificateFactory = new CertificateFactory(
                options.caSubject(),
                options.privateKey(),
                options.certificateValidity(),
                options.signatureAlgo(),
                certificateAuthorityRepository);
    }

    @Test
    void getCACertificate_shouldReturnStoredCertificate_whenExists() {
        // Given
        var expectedCert = mock(CertificateInfo.class);
        given(expectedCert.certificate()).willReturn(certificate);
        given(certificateAuthorityRepository.getStoredCertificate(CA_KEY)).willReturn(expectedCert);

        // When
        var result = certificateFactory.getCACertificate();

        // Then
        assertThat(result.certificate())
                .as("Check returned CA certificate matches stored certificate")
                .isEqualTo(certificate);

        then(certificateAuthorityRepository).should().getStoredCertificate(CA_KEY);
    }

    @Test
    void getCACertificate_shouldCreateNewCertificate_whenNotExists() throws IOException {
        // Given
        given(certificateAuthorityRepository.getStoredCertificate(CA_KEY))
                .willThrow(new NoSuchElementException("Certificate not found"));

        // When
        var result = certificateFactory.getCACertificate();

        // Then
        then(certificateAuthorityRepository).should().getStoredCertificate(CA_KEY);
        then(certificateAuthorityRepository).should().storeCertificate(CA_KEY, result);

        assertThat(result)
                .as("Check new CA certificate is created and not null")
                .isNotNull();
    }

    @Test
    void getCACertificate_shouldThrowException_whenCreationFails() throws IOException {
        // Given
        given(certificateAuthorityRepository.getStoredCertificate(CA_KEY))
                .willThrow(new NoSuchElementException("Certificate not found"));

        BDDMockito.willThrow(new IOException("Storage error"))
                .given(certificateAuthorityRepository)
                .storeCertificate(eq(CA_KEY), any(CertificateInfo.class));

        // When / Then
        thenThrownBy(() -> certificateFactory.getCACertificate())
                .as("Check that exception is thrown when CA certificate creation fails")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("certificate authority generation in error");
    }

    @Test
    void getCertificate_shouldReturnStoredCertificate_whenExists() throws IOException {
        // Given
        var caCertInfo = mock(CertificateInfo.class);

        var expectedCertInfo = mock(CertificateInfo.class);

        given(certificateAuthorityRepository.getStoredCertificate(TEST_HOST)).willReturn(expectedCertInfo);

        // When
        var result = certificateFactory.getCertificate(caCertInfo, TEST_HOST);

        // Then
        assertThat(result)
                .as("Check returned certificate matches stored certificate for host")
                .isEqualTo(expectedCertInfo);

        then(certificateAuthorityRepository).should().getStoredCertificate(TEST_HOST);
        then(certificateAuthorityRepository)
                .should(never())
                .storeCertificate(org.mockito.ArgumentMatchers.anyString(), any());
    }

    @Test
    void constructor_shouldThrowException_whenInvalidAlgorithm() {
        // Given
        var invalidPrivateKeyOptions = new CertificateOptions.PrivateKey("INVALID_ALGORITHM", null, null);

        // When / Then
        thenThrownBy(() -> new CertificateFactory(
                        options.caSubject(),
                        invalidPrivateKeyOptions,
                        options.certificateValidity(),
                        options.signatureAlgo(),
                        certificateAuthorityRepository))
                .as("Check constructor throws NoSuchAlgorithmException on invalid algorithm")
                .isInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void getCertificateShouldCreateNewCertificateWhenNotExists() throws Exception {
        // Given
        var privateKey = KeyPairGenerator.getInstance(options.privateKey().algo())
                .generateKeyPair()
                .getPrivate();
        var caCertInfo = new CertificateInfo(privateKey, certificate);

        given(certificateAuthorityRepository.getStoredCertificate(TEST_HOST))
                .willThrow(new NoSuchElementException("Certificate not found"));

        // When
        var result = certificateFactory.getCertificate(caCertInfo, TEST_HOST);

        // Then
        then(certificateAuthorityRepository).should().getStoredCertificate(TEST_HOST);
        then(certificateAuthorityRepository).should().storeCertificate(eq(TEST_HOST), any(CertificateInfo.class));

        assertThat(result).as("Il certificato creato non deve essere nullo").isNotNull();
    }
}
